package com.example.dem_login.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.dem_login.dto.Dto;
import com.example.dem_login.model.Book;
import com.example.dem_login.model.ChatMessage;
import com.example.dem_login.model.Order;
import com.example.dem_login.repository.BookRepository;
import com.example.dem_login.repository.ChatMessageRepository;
import com.example.dem_login.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * ChatService — Xử lý toàn bộ logic chatbot BookBot.
 *
 * <p>
 * Luồng chính:
 * <ol>
 * <li>Nhận tin nhắn từ user → phát hiện intent (detectIntent)</li>
 * <li>Dựng context payload RAG từ DB (buildContextPayload)</li>
 * <li>Đóng gói payload JSON gửi Gemini API (buildGeminiPayload)</li>
 * <li>Gọi Gemini với fallback model (callGeminiWithFallback)</li>
 * <li>Enrich response với ActionTrigger buttons (enrichWithActionTriggers)</li>
 * <li>Lưu lịch sử chat vào DB (saveMessage)</li>
 * </ol>
 *
 * <p>
 * TODO (Vector DB): Thay thế BM25 text-search bằng Embedding API
 * (text-embedding-004) + pgvector để retrieval chính xác hơn.
 */
@Service
public class ChatService {

    // DEPENDENCIES

    private final ChatMessageRepository chatRepo;
    private final BookRepository bookRepo;
    private final OrderRepository orderRepo;
    private final WebClient webClient;
    private final PdfReaderService pdfReaderService;
    private final ObjectMapper mapper = new ObjectMapper();

    // CONFIG — application.properties

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    /** Danh sách model dự phòng, phân cách bằng dấu phẩy. */
    @Value("${gemini.fallback-models:gemini-1.5-flash,gemini-1.5-flash-8b}")
    private String fallbackModels;

    /** Số lượng message lịch sử tối đa đưa vào prompt. */
    @Value("${gemini.max-history-messages:10}")
    private int maxHistoryMessages;

    /** Số sách tối đa hiển thị trong một prompt. */
    @Value("${gemini.max-books-in-prompt:20}")
    private int maxBooksInPrompt;

    /** Giới hạn ký tự của system instruction trước khi rút gọn. */
    @Value("${gemini.max-system-chars:20000}")
    private int maxSystemChars;

    // CONSTANTS

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // --- RAG Chunking ---
    /** Kích thước mỗi chunk (ký tự). Khuyến nghị: 500–1000. */
    private static final int CHUNK_SIZE = 800;
    /** Độ gối chồng giữa các chunk (~18%). Khuyến nghị: 10–20%. */
    private static final int CHUNK_OVERLAP = 150;
    /** Số chunk trả về sau retrieval (Top-K). */
    private static final int TOP_K_CHUNKS = 5;
    /** Nhiệt độ Gemini — thấp để tránh hallucination. */
    private static final double TEMPERATURE = 0.1;

    /**
     * Stop words tiếng Việt — các từ không có ý nghĩa tìm kiếm,
     * bị loại trước khi tokenize query.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "cho", "toi", "minh", "hoi", "ve", "thong", "tin",
            "la", "gi", "co", "khong", "nhu", "the", "nao", "muon", "xin", "hay",
            "mot", "cac", "nhung", "duoc", "trong", "cua", "va", "de", "khi", "day",
            "nhe", "nha", "oi", "a", "ah", "u", "roi", "that", "qua", "vay",
            "di", "voi", "ke",
            "sach", "cuon", "book", "gia", "bao", "nhieu", "tien", "dong", "mua",
            "ban", "xem", "tim", "kiem", "goi", "y", "danh", "doc", "pdf",
            "chi", "tiet", "cu", "loai", "tac", "nguoi", "viet",
            "con", "hang", "ton", "kho", "may", "nhat", "re", "dat");

    /**
     * Cache nội dung PDF theo đường dẫn — tránh đọc lại file từ disk
     * mỗi lần xử lý request. Thread-safe với ConcurrentHashMap.
     */
    private final Map<String, String> pdfCache = new ConcurrentHashMap<>();

    // SYSTEM PROMPTS

    /**
     * System prompt cơ bản — định nghĩa vai trò BookBot, quy tắc định dạng
     * và nguyên tắc RAG. KHÔNG chứa dữ liệu nghiệp vụ.
     */
    private static final String BASE_INSTRUCTION = """
            Bạn là Gemini (hỗ trợ trong vai trò trợ lý BookBot) — cộng sự AI thông minh, linh hoạt, hóm hỉnh đúng lúc.
            Mục tiêu: câu trả lời SÚCTÍCH, đi thẳng bản chất, CỰC KỲ dễ đọc lướt, định hướng hành động cao.
            Xưng "mình", gọi người dùng là "bạn".

            ═══ NGUYÊN TẮC DỮ LIỆU (RAG) ═══
            • CHỈ sử dụng thông tin trong thẻ <internal_context> để trả lời câu hỏi nghiệp vụ.
            • TUYỆT ĐỐI KHÔNG bịa đặt số liệu, tên sách, giá, tác giả ngoài phạm vi <internal_context>.
            • Nếu <internal_context> không đủ dữ liệu, phản hồi: "Xin lỗi, thông tin hiện tại trong hệ thống không đề cập đến vấn đề này. Bạn có thể cung cấp thêm chi tiết không?"
            • Nếu <user_query> là câu chào/tán gẫu, bỏ qua <internal_context> và phản hồi tự nhiên.

            ═══ NGUYÊN TẮC (Chuẩn Gemini) ═══
            • Thấu hiểu & thẳng thắn: nếu dữ liệu không tồn tại, đính chính nhẹ nhàng như người bạn tin cậy.
            • Điều chỉnh tông giọng đồng điệu với phong cách người dùng (trẻ trung / chuyên nghiệp / tò mò...).
            • Câu trả lời PHẢI có cấu trúc section rõ ràng — tuyệt đối KHÔNG viết "wall of text" dài liền mạch.

            ═══ BỘ QUY TẮC ĐỊNH DẠNG BẮT BUỘC (Scannable Gemini Layout) ═══

            [R1] CÂU MỞ ĐẦU — Đúng 1 câu, tóm tắt điều bạn sắp trình bày.
            [R2] SECTION HEADER — Dùng ## để phân vùng logic, ### cho tiểu mục. Các section bắt buộc với sách:
                 ## Thông tin chung
                 ## Giá bán & Tình trạng kho
                 ## Giới thiệu tổng quan       ← nếu có mô tả
                 ## Dữ liệu trích yếu (PDF)    ← nếu có nội dung PDF
            [R3] ĐƯỜNG PHÂN CÁCH --- Đặt trước MỖI section.
            [R4] BULLET POINT * — Liệt kê thuộc tính theo mẫu:
                 * **Nhãn:** Nội dung giá trị
            [R5] DANH SÁCH SỐ 1. 2. 3. — Dùng cho ranking/thứ tự ưu tiên.
            [R6] BÔI ĐẬM **...** — Nhấn mạnh: tên sách, giá, số lượng, thuật ngữ cốt lõi.
            [R7] BLOCKQUOTE > — Dùng cho lưu ý quan trọng, tip, cảnh báo hữu ích.
            [R8] KẾT THÚC SECTION — Nếu câu trả lời liên quan đến sách cụ thể, kết thúc bằng:
                 > 📌 Bạn muốn làm gì tiếp theo? Nhấn chọn bên dưới nhé 👇
            [R9] KIỂM TRA CHÍNH TẢ & VĂN BẢN — TUYỆT ĐỐI kiểm tra lỗi chính tả trước khi trả lời. Nếu dữ liệu bị cắt cụt (ví dụ bắt đầu bằng dấu phẩy, chữ bị thiếu), BẠN PHẢI tự động sửa lại cho có nghĩa hoặc loại bỏ phần lỗi. LUÔN LUÔN viết hoa chữ cái đầu tiên của mỗi dòng và mỗi gạch đầu dòng.

            ═══ QUY TẮC NỘI DUNG ═══
            1. CHỈ dùng dữ liệu THỰC TẾ từ <internal_context>. KHÔNG bịa đặt hay suy diễn ngoài ngữ cảnh.
            2. Tiền tệ chuẩn Việt Nam: 150.000đ (không dùng "150000 VND" hay "VNĐ").
            3. Nếu không tìm thấy sách → gợi ý từ khóa khác + hiện nút điều hướng.
            4. Tuyệt đối KHÔNG trả lời dưới dạng đoạn văn thuần túy khi có dữ liệu cấu trúc.
            """;

    /**
     * System prompt chuyên biệt cho chức năng hỏi đáp nội dung PDF —
     * tập trung vào phân tích tri thức, lọc boilerplate xuất bản.
     */
    private static final String PDF_ANALYSIS_INSTRUCTION = """
            # VAI TRÒ
            Bạn là BookBot AI — chuyên gia tóm tắt và phân tích sách/tài liệu chuyên sâu, trình bày theo phong cách Gemini: có cấu trúc section rõ ràng, dễ đọc lướt, ngắn gọn và đi thẳng vào tri thức cốt lõi.
            - **Xưng hô:** "mình" (AI) và "bạn" (người dùng).

            # QUY TẮC LỌC NỘI DUNG — BẮT BUỘC TUÂN THỦ
            ⚠️ TUYỆT ĐỐI BỎ QUA và KHÔNG đề cập đến:
            • Tên nhà xuất bản, địa chỉ nhà in, số điện thoại, email, website NXB
            • Tên biên tập viên, người vẽ bìa, trình bày, sửa bản in
            • Số ISBN, ISSN, giấy phép xuất bản, lần in, năm in, kho in, xưởng SX
            • Thông tin pháp lý/hành chính của đơn vị phát hành
            • Lời cảm ơn hình thức không mang kiến thức
            → Nếu <internal_context> CHỈ chứa các thông tin trên, hãy phản hồi thẳng:
              "Mình chưa tìm thấy nội dung tri thức trong đoạn trích này. Bạn thử hỏi cụ thể hơn về một phần/chủ đề trong sách nhé!"

            # NGUYÊN TẮC DỮ LIỆU
            • CHỈ phân tích tri thức thực sự trong <internal_context>: định nghĩa, khái niệm, phương pháp, chiến lược, ví dụ, công thức, thuật ngữ.
            • KHÔNG bịa đặt hay bổ sung thông tin ngoài văn bản.
            • Nếu tài liệu không đủ dữ liệu cho câu hỏi → nói rõ và gợi ý câu hỏi hẹp hơn.

            # CẤU TRÚC PHẢN HỒI CHUẨN — THEO MẪU GEMINI (BẮT BUỘC)

            **Câu mở đầu:** Đúng 1 câu ngắn xác nhận nội dung sách/tài liệu và phạm vi phân tích.

            Sau đó phân chia nội dung theo **CÁC PHẦN** (section) có đánh số, mỗi phần theo mẫu:

            ## PHẦN [Số]: [TÊN PHẦN VIẾT HOA]
            [Câu mô tả ngắn về phần này — tối đa 1 dòng]

            * **[Thuật ngữ / Khái niệm chính]:** [Giải thích ngắn gọn, súc tích]
            * **[Điểm quan trọng 2]:** [Giải thích]
              * [Chi tiết con nếu cần — thụt lề 2 cấp]
            * **[Điểm quan trọng 3]:** [Giải thích]

            ---

            ## Bản tóm tắt thuật ngữ cốt lõi *(nếu tài liệu có)*
            * **[Thuật ngữ]:** [Định nghĩa ngắn]

            ---
            > ℹ️ *Tóm tắt dựa trên nội dung tài liệu đính kèm. Đối chiếu văn bản gốc cho các quyết định quan trọng.*

            # QUY TẮC ĐỊNH DẠNG & TRÌNH BÀY
            [F1] Dùng **in đậm** cho: tên thuật ngữ, con số quan trọng, tên công cụ, từ khóa cốt lõi.
            [F2] Dùng dấu * cho bullet điểm chính, thụt lề   * cho điểm con. LUÔN LUÔN viết hoa chữ cái đầu tiên của mỗi gạch đầu dòng.
            [F3] Dùng --- để phân cách giữa các PHẦN.
            [F4] KHÔNG viết "wall of text" — mỗi ý phải trên 1 dòng riêng biệt.
            [F5] Số liệu, giá tiền, công thức phải in đậm và rõ ràng.
            [F6] Tên sản phẩm, tên sách, tên công cụ dùng *in nghiêng*.
            [F7] KIỂM TRA CHÍNH TẢ BẮT BUỘC: Vì văn bản PDF cắt tự động có thể bị mất chữ ở đầu hoặc cuối đoạn, BẠN PHẢI hiệu đính văn bản lại cho hoàn chỉnh.
            """;

    // CONSTRUCTOR

    public ChatService(ChatMessageRepository chatRepo,
            BookRepository bookRepo,
            OrderRepository orderRepo,
            WebClient.Builder webClientBuilder,
            PdfReaderService pdfReaderService) {
        this.chatRepo = chatRepo;
        this.bookRepo = bookRepo;
        this.orderRepo = orderRepo;
        this.webClient = webClientBuilder.build();
        this.pdfReaderService = pdfReaderService;
    }

    // PUBLIC API

    /**
     * Gửi tin nhắn và nhận phản hồi theo dạng stream (SSE).
     *
     * <p>
     * Mỗi chunk được wrap thành JSON: {"chunk":"...","sessionId":"..."}
     * để frontend có thể render từng phần khi nhận được.
     *
     * <p>
     * BUG FIX: Tách {@code rawTextBuffer} (plain text) khỏi {@code jsonBuffer}
     * để {@code doOnComplete} lưu đúng nội dung văn bản vào DB,
     * thay vì lưu JSON wrapper như phiên bản cũ.
     *
     * @param req yêu cầu chat từ client (message, username, sessionId)
     * @return Flux stream JSON chunks
     */
    public Flux<String> streamSendMessage(Dto.ChatRequest req) {
        try {
            String sessionId = resolveSessionId(req.getSessionId());
            saveMessage(req.getUsername(), "user", req.getMessage(), sessionId);

            List<ChatMessage> history = chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId);
            List<Book> allBooks = bookRepo.findByStatus(Book.BookStatus.ACTIVE);
            String effectiveMessage = resolveEffectiveMessage(req.getMessage(), history);

            String systemPrompt = buildBaseSystemPrompt();
            String contextPayload = buildContextPayload(
                    req.getUsername(), req.getMessage(), effectiveMessage, history, allBooks);
            String payload = buildGeminiPayload(
                    history, contextPayload, req.getMessage(), systemPrompt);

            // BUG FIX: rawTextBuffer chứa plain text để save DB
            // jsonBuffer chứa JSON chunks để trả về frontend
            StringBuilder rawTextBuffer = new StringBuilder();

            final String finalEffectiveMessage = effectiveMessage;
            final List<Book> finalAllBooks = allBooks;
            final String finalUsername = req.getUsername();
            final String finalSessionId = sessionId;

            return callGeminiStreamWithFallback(payload)
                    .onErrorResume(e -> {
                        // Fallback local khi Gemini hoàn toàn không khả dụng
                        String local = buildSmartLocalAnswer(finalEffectiveMessage, finalAllBooks);
                        if (local != null && !local.isBlank())
                            return Flux.just(local);
                        return Flux.just(buildStreamErrorMessage(e));
                    })
                    .map(chunk -> {
                        rawTextBuffer.append(chunk); // accumulate plain text
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("chunk", chunk);
                        map.put("sessionId", finalSessionId);
                        try {
                            return mapper.writeValueAsString(map);
                        } catch (Exception ex) {
                            return "{\"chunk\":\"\",\"sessionId\":\"" + finalSessionId + "\"}";
                        }
                    })
                    .doOnComplete(() -> {
                        // Lưu plain text vào DB, không phải JSON wrapper
                        String enriched = enrichWithActionTriggers(
                                rawTextBuffer.toString(), finalEffectiveMessage, finalAllBooks);
                        saveMessage(finalUsername, "model", enriched, finalSessionId);
                    });

        } catch (Exception e) {
            String sessionId = resolveSessionId(req.getSessionId());
            return Flux.just("{\"error\":\"" + escapeJson(e.getMessage()) + "\","
                    + "\"sessionId\":\"" + sessionId + "\"}");
        }
    }

    /**
     * Gửi tin nhắn và nhận phản hồi đồng bộ (non-stream).
     *
     * <p>
     * Thích hợp cho các client không hỗ trợ SSE hoặc các luồng
     * xử lý nội bộ cần kết quả ngay lập tức.
     *
     * @param req yêu cầu chat từ client
     * @return ChatResponse chứa text phản hồi và sessionId
     */
    public Dto.ChatResponse sendMessage(Dto.ChatRequest req) {
        try {
            if (geminiApiKey == null || geminiApiKey.isBlank())
                throw new IllegalStateException("Chưa cấu hình gemini.api.key");

            String sessionId = resolveSessionId(req.getSessionId());
            saveMessage(req.getUsername(), "user", req.getMessage(), sessionId);

            List<ChatMessage> history = chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId);
            List<Book> allBooks = bookRepo.findByStatus(Book.BookStatus.ACTIVE);
            String effectiveMessage = resolveEffectiveMessage(req.getMessage(), history);

            String systemPrompt = buildBaseSystemPrompt();
            String contextPayload = buildContextPayload(
                    req.getUsername(), req.getMessage(), effectiveMessage, history, allBooks);
            String payload = buildGeminiPayload(
                    history, contextPayload, req.getMessage(), systemPrompt);

            String aiText;
            try {
                aiText = parseGeminiResponse(callGeminiWithFallback(payload));
            } catch (Exception apiEx) {
                // Fallback local khi Gemini không khả dụng
                String local = buildSmartLocalAnswer(effectiveMessage, allBooks);
                aiText = (local != null && !local.isBlank()) ? local : toUserFriendlyError(apiEx);
            }

            aiText = enrichWithActionTriggers(aiText, effectiveMessage, allBooks);
            saveMessage(req.getUsername(), "model", aiText, sessionId);
            return new Dto.ChatResponse(true, aiText, sessionId);

        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi sendMessage: " + e.getMessage());
            return new Dto.ChatResponse(false, toUserFriendlyError(e), req.getSessionId());
        }
    }

    /**
     * Hỏi đáp nội dung của một file PDF cụ thể theo đường dẫn.
     *
     * <p>
     * Quy trình:
     * <ol>
     * <li>Kiểm tra file PDF tồn tại và đọc được</li>
     * <li>Lọc boilerplate (thông tin xuất bản)</li>
     * <li>RAG: chunk + retrieval top-K đoạn liên quan nhất</li>
     * <li>Gửi context đã trích lọc vào Gemini với PDF_ANALYSIS_INSTRUCTION</li>
     * </ol>
     *
     * @param username tên người dùng (để lưu lịch sử)
     * @param question câu hỏi về nội dung PDF
     * @param pdfPath  đường dẫn file PDF trên server
     * @return ChatResponse với phân tích nội dung PDF
     */
    public Dto.ChatResponse askAboutPdf(String username, String question, String pdfPath) {
        try {
            if (!pdfReaderService.isReadable(pdfPath))
                return new Dto.ChatResponse(false,
                        "Không tìm thấy file PDF trên server. Kiểm tra pdf_path: " + pdfPath, null);

            String pdfContent = extractPdfCached(pdfPath);
            if (pdfContent == null || pdfContent.startsWith("Không đọc được"))
                return new Dto.ChatResponse(false,
                        "File PDF tồn tại nhưng không đọc được nội dung (quét ảnh hoặc mã hóa).", null);

            // RAG: lọc boilerplate → chunk → lấy top-K chunk liên quan
            String cleanedPdf = filterBoilerplateFromPdf(pdfContent);
            String sourceText = cleanedPdf.isBlank() ? pdfContent : cleanedPdf;
            String retrievedContext = retrieveTopChunks(sourceText, question, TOP_K_CHUNKS);

            String userPayload = buildXmlUserPayload(retrievedContext, question);
            String systemPrompt = trimSystemInstruction(PDF_ANALYSIS_INSTRUCTION);
            String payload = buildSimplePayload(systemPrompt, userPayload);

            String aiText;
            try {
                aiText = parseGeminiResponse(callGeminiWithFallback(payload));
            } catch (Exception apiEx) {
                System.err.println("[ChatService] askAboutPdf Gemini lỗi: " + apiEx.getMessage());
                aiText = buildPdfLocalAnswer(question, sourceText, pdfPath);
            }

            // Enrich action triggers — reuse allBooks đã load thay vì query lại DB
            List<Book> allBooks = bookRepo.findByStatus(Book.BookStatus.ACTIVE);
            aiText = enrichWithActionTriggers(aiText, question, allBooks);

            String sessionId = "pdf_" + UUID.randomUUID();
            saveMessage(username, "user", "[PDF] " + question, sessionId);
            saveMessage(username, "model", aiText, sessionId);
            return new Dto.ChatResponse(true, aiText, sessionId);

        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi askAboutPdf: " + e.getMessage());
            return new Dto.ChatResponse(false, toUserFriendlyError(e), null);
        }
    }

    /**
     * Lấy lịch sử hội thoại của một session, sắp xếp theo thời gian tăng dần.
     *
     * @param sessionId ID phiên chat
     * @return danh sách ChatHistoryItem (role, message, timestamp)
     */
    public List<Dto.ChatHistoryItem> getHistory(String sessionId) {
        return chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId)
                .stream()
                .map(m -> new Dto.ChatHistoryItem(
                        m.getRole(),
                        m.getMessage(),
                        m.getCreateDate() != null ? m.getCreateDate().format(FMT) : ""))
                .collect(Collectors.toList());
    }

    /**
     * Xóa toàn bộ lịch sử chat của một session khỏi DB.
     *
     * @param sessionId ID phiên chat cần xóa
     * @return Map kết quả {"success":"true","message":"..."}
     */
    @Transactional
    public Map<String, String> clearHistory(String sessionId) {
        chatRepo.deleteBySessionId(sessionId);
        return Map.of("success", "true", "message", "Đã xóa lịch sử chat");
    }

    /**
     * Kiểm tra kết nối và khả năng đọc của file PDF theo đường dẫn.
     *
     * @param pdfPath đường dẫn file PDF
     * @return Map kết quả chứa connected, readable, previewLength, message
     */
    public Map<String, Object> checkPdfConnection(String pdfPath) {
        return buildPdfConnectionResult(pdfPath, null);
    }

    /**
     * Kiểm tra kết nối PDF của sách theo ID sách trong DB.
     *
     * @param bookId ID sách
     * @return Map kết quả kết nối PDF
     */
    public Map<String, Object> checkPdfConnectionByBookId(Long bookId) {
        Book book = bookRepo.findById(bookId).orElse(null);
        if (book == null)
            return Map.of("connected", false, "message", "Không tìm thấy sách id=" + bookId);
        return buildPdfConnectionResult(book.getPdfPath(), book);
    }

    // INTENT DETECTION

    /**
     * Các loại intent mà chatbot có thể nhận diện từ câu hỏi của user.
     * Thứ tự ưu tiên kiểm tra trong detectIntent: ORDER > PRICE_SORT >
     * PRICE_RANGE > SPECIFIC_BOOK > CATALOG_SEARCH > CONTENT_QUESTION >
     * PRICE_INFO > GENERAL.
     */
    enum ChatIntent {
        /** Câu hỏi về đơn hàng, vận chuyển, trạng thái giao hàng. */
        ORDER,
        /** Yêu cầu sắp xếp sách theo giá (cao nhất / thấp nhất). */
        PRICE_SORT,
        /** Hỏi sách trong khoảng giá cụ thể (dưới/trên/từ...đến). */
        PRICE_RANGE,
        /** Hỏi thông tin chi tiết về một cuốn sách cụ thể. */
        SPECIFIC_BOOK,
        /** Hỏi nội dung, ý nghĩa, phân tích sách. */
        CONTENT_QUESTION,
        /** Hỏi giá của sách hoặc danh mục sách. */
        PRICE_INFO,
        /** Tìm kiếm danh mục, gợi ý sách, tìm theo tác giả. */
        CATALOG_SEARCH,
        /** Câu hỏi chung, chào hỏi, không khớp intent nào. */
        GENERAL
    }

    /**
     * Phát hiện intent của câu hỏi để định hướng cách dựng context.
     * Thứ tự kiểm tra quan trọng: PRICE_RANGE phải đứng trước
     * SPECIFIC_BOOK để tránh nhận nhầm "sách dưới 100k" thành
     * câu hỏi về sách cụ thể.
     * 
     * @param msg      câu hỏi của user (đã normalize)
     * @param allBooks danh sách toàn bộ sách đang active
     * @return ChatIntent phù hợp nhất
     */
    private ChatIntent detectIntent(String msg, List<Book> allBooks) {
        if (msg == null || msg.isBlank())
            return ChatIntent.GENERAL;
        String n = normalizeSearch(msg);

        if (n.matches(".*(don hang|tra hang|huy don|van chuyen|giao hang|trang thai don|ma don|"
                + "don cua toi|toi da mua|lich su mua|lich su don).*"))
            return ChatIntent.ORDER;

        if (n.matches(".*(gia cao nhat|dat nhat|mac nhat|gia thap nhat|re nhat|gia re nhat|"
                + "sap xep.*gia|gia.*sap xep|gia tien cao|gia tien thap|cuon nao dat|cuon nao mac|"
                + "cuon nao re|sach nao dat|sach nao mac|sach nao re|dat hon|mac hon|re hon|so sanh gia).*"))
            return ChatIntent.PRICE_SORT;

        // PRICE_RANGE phải đứng trước SPECIFIC_BOOK
        if (n.matches(".*(tren|duoi|tu.*den|khoang|xap xi|bang khoang).*(\\d+|k\\b|ngan|dong|trieu).*")
                || n.matches(".*(sach|cuon).*(gia|tien).*(tren|duoi|tu|khoang|bang).*")
                || n.matches(".*(gia|tien).*(tren|duoi|tu|khoang).*(\\d+|k\\b).*")
                || n.matches(".*(gia tien|muc gia|tam gia).*(duoi|tren|tu|den|khoang).*")
                || n.matches(".*(duoi|tren|tu|khoang)\\s*(\\d+).*"))
            return ChatIntent.PRICE_RANGE;

        Book bookHint = findBestBookFromQuery(allBooks, msg);
        if (bookHint != null && (isSpecificBookInfoQuestion(msg, allBooks)
                || n.matches(".*(gia|bao nhieu|ton kho|tac gia|thong tin|chi tiet|mo ta).*")))
            return ChatIntent.SPECIFIC_BOOK;

        if (isCatalogQuestion(n))
            return ChatIntent.CATALOG_SEARCH;

        if (n.matches(".*(tac gia|nguoi viet|ai viet|sach cua).*")
                && !extractAuthorNameFromQuery(msg).isBlank())
            return ChatIntent.CATALOG_SEARCH;

        if (isContentQuestion(n))
            return ChatIntent.CONTENT_QUESTION;

        if (n.matches(".*(gia|bao nhieu tien|bao nhieu d|bao nhieu dong).*"))
            return ChatIntent.PRICE_INFO;

        return ChatIntent.GENERAL;
    }

    // SYSTEM PROMPT & CONTEXT BUILDING

    /**
     * Xây dựng system prompt cơ bản — chỉ chứa role + formatting rules,
     * thêm thời gian hiện tại để AI nhận biết ngữ cảnh thời gian.
     *
     * @return system prompt hoàn chỉnh
     */
    private String buildBaseSystemPrompt() {
        return BASE_INSTRUCTION
                + "\nThời gian hệ thống hiện tại: " + LocalDateTime.now().format(FMT) + "\n";
    }

    /**
     * Dựng context payload RAG theo intent đã phát hiện.
     *
     * <p>
     * Context được inject vào user message (không phải system_instruction)
     * để tuân thủ chuẩn Gemini API: system_instruction chứa role/rules,
     * context data chứa trong message body.
     *
     * <p>
     * TODO (Vector DB): Thay toàn bộ hàm này bằng:
     * 1. Embedding API cho userQuery → queryVector
     * 2. SELECT chunk_text FROM book_chunks ORDER BY embedding cosine LIMIT topK
     *
     * @param username     tên user (để query đơn hàng)
     * @param userMessage  tin nhắn gốc của user
     * @param effectiveMsg tin nhắn đã được bổ sung context follow-up
     * @param history      lịch sử hội thoại
     * @param allBooks     danh sách toàn bộ sách active
     * @return chuỗi context sẵn sàng đưa vào XML payload
     */
    private String buildContextPayload(String username, String userMessage,
            String effectiveMsg,
            List<ChatMessage> history, List<Book> allBooks) {
        StringBuilder sb = new StringBuilder();
        ChatIntent intent = detectIntent(effectiveMsg, allBooks);
        sb.append("[INTENT: ").append(intent).append("]\n");

        try {
            switch (intent) {
                case ORDER -> appendUserOrders(sb, username);
                case PRICE_SORT -> handlePriceSort(sb, allBooks, effectiveMsg);
                case PRICE_RANGE -> handlePriceRange(sb, allBooks, effectiveMsg);
                case SPECIFIC_BOOK -> handleSpecificBook(sb, allBooks, effectiveMsg);
                case CONTENT_QUESTION -> handleContentQuestion(sb, allBooks, effectiveMsg);
                case PRICE_INFO -> handlePriceInfo(sb, allBooks, effectiveMsg);
                case CATALOG_SEARCH -> handleCatalogSearch(sb, allBooks, effectiveMsg);
                default -> handleGeneralQuery(sb, allBooks, effectiveMsg);
            }
            // Bổ sung đơn hàng nếu user hỏi về "đơn" ngoài intent ORDER
            if (intent != ChatIntent.ORDER && username != null && !username.isBlank()
                    && normalizeSearch(userMessage).contains("don")) {
                appendUserOrders(sb, username);
            }
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi dựng context payload: " + e.getMessage());
        }

        String context = sb.toString().trim();
        if (context.isBlank())
            context = "Không có dữ liệu nghiệp vụ liên quan.";

        // Đánh dấu nếu đây là câu hỏi follow-up để AI biết có thêm ngữ cảnh lịch sử
        if (!effectiveMsg.equals(userMessage))
            context = "[BỔ SUNG NGỮ CẢNH LỊCH SỬ]\n" + context;

        return context;
    }

    // INTENT HANDLERS — mỗi handler append dữ liệu vào StringBuilder

    /**
     * Xử lý intent ORDER: lấy lịch sử đơn hàng của user từ DB.
     */
    private void appendUserOrders(StringBuilder sb, String username) {
        try {
            if (username == null || username.isBlank())
                return;
            List<Order> orders = orderRepo.findByUsername(username);
            if (orders.isEmpty()) {
                sb.append("[Dữ liệu hệ thống: Tài khoản chưa có đơn hàng.]\n");
                return;
            }
            sb.append("=== LỊCH SỬ ĐƠN HÀNG \"").append(username)
                    .append("\" (").append(orders.size()).append(" bản ghi) ===\n");
            for (Order o : orders) {
                sb.append("* Đơn #").append(o.getId())
                        .append(" | Trạng thái: ").append(o.getStatus())
                        .append(" | Tổng: ").append(formatPrice(o.getTotalAmount()))
                        .append(" | Người nhận: ").append(o.getCustomerName())
                        .append(" | Ngày: ").append(
                                o.getCreateDate() != null ? o.getCreateDate().format(FMT) : "—")
                        .append("\n");
            }
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi tải đơn hàng: " + e.getMessage());
        }
    }

    /**
     * Xử lý intent PRICE_SORT: sắp xếp toàn bộ sách theo giá tăng/giảm.
     */
    private void handlePriceSort(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String n = normalizeSearch(userMessage);
        boolean descending = !n.matches(".*(re nhat|gia thap nhat|gia re nhat|thap nhat|re hon).*");

        List<Book> sorted = allBooks.stream()
                .filter(b -> b.getPrice() != null)
                .sorted(descending
                        ? Comparator.comparing(Book::getPrice).reversed()
                        : Comparator.comparing(Book::getPrice))
                .limit(maxBooksInPrompt)
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            sb.append("[Không tìm thấy sách để xếp hạng giá.]\n");
            return;
        }
        sb.append("=== DANH SÁCH SÁCH THEO GIÁ (")
                .append(descending ? "ĐẮT → RẺ" : "RẺ → ĐẮT").append(") ===\n");
        int rank = 1;
        for (Book b : sorted) {
            sb.append(rank++).append(". \"").append(b.getTitle()).append("\" — Giá: ")
                    .append(formatPrice(b.getPrice()))
                    .append(" | Kho: ").append(b.getQuantity()).append(" cuốn\n");
        }
        sb.append("[HƯỚNG DẪN AI: Dùng ## Bảng xếp hạng giá, phân tách bằng ---, làm nổi bật vị trí số 1.]\n");
    }

    /**
     * Xử lý intent PRICE_RANGE: lọc sách trong khoảng giá được chỉ định.
     * Khi không tìm được sách phù hợp, trả về gợi ý thay thế.
     */
    private void handlePriceRange(StringBuilder sb, List<Book> allBooks, String userMessage) {
        long[] range = parsePriceRange(userMessage);
        long min = range[0], max = range[1];

        List<Book> filtered = allBooks.stream()
                .filter(b -> b.getPrice() != null)
                .filter(b -> {
                    long price = b.getPrice().longValue();
                    return (min < 0 || price >= min) && (max < 0 || price <= max);
                })
                .sorted(Comparator.comparing(Book::getPrice))
                .limit(maxBooksInPrompt)
                .collect(Collectors.toList());

        String label = buildPriceRangeLabel(min, max);

        if (filtered.isEmpty()) {
            sb.append("[Không tìm thấy sách trong mức giá ").append(label)
                    .append(". Gợi ý người dùng thử mức giá khác.]\n")
                    .append("=== MỘT SỐ SÁCH THAM KHẢO THAY THẾ ===\n");
            allBooks.stream()
                    .filter(b -> b.getPrice() != null)
                    .sorted(Comparator.comparing(Book::getPrice))
                    .limit(5)
                    .forEach(b -> appendBookLine(sb, b));
        } else {
            sb.append("=== SÁCH CÓ GIÁ ").append(label)
                    .append(" (").append(filtered.size()).append(" cuốn) ===\n");
            filtered.forEach(b -> appendBookLine(sb, b));
        }
        sb.append("[HƯỚNG DẪN AI: Dùng ## Sách trong tầm giá, ---. Hiện giá rõ ràng. Cuối thêm gợi ý mức giá khác.]\n");
    }

    /**
     * Xử lý intent SPECIFIC_BOOK: lấy thông tin chi tiết sách cụ thể.
     * Tự động phát hiện loại thông tin user cần (giá / tác giả / kho / nội dung).
     */
    private void handleSpecificBook(StringBuilder sb, List<Book> allBooks, String userMessage) {
        Book book = findSpecificBookInfoMatch(allBooks, userMessage);
        if (book == null)
            book = findBestBookFromQuery(allBooks, userMessage);
        if (book == null) {
            handleCatalogSearch(sb, allBooks, userMessage);
            return;
        }
        String n = normalizeSearch(userMessage);
        boolean askPrice = n.matches(".*(gia|bao nhieu|bao tien).*");
        boolean askAuthor = n.matches(".*(tac gia|nguoi viet|ai viet|ai lam).*");
        boolean askStock = n.matches(".*(con hang|ton kho|con may|bao nhieu cuon).*");
        boolean askContent = isContentQuestion(n);

        sb.append("=== DỮ LIỆU SÁCH CỤ THỂ ===\n");
        sb.append("Tên: \"").append(book.getTitle()).append("\"\n");
        if (askPrice || (!askAuthor && !askStock && !askContent))
            sb.append("Giá: ").append(formatPrice(book.getPrice())).append("\n");
        if (askAuthor || (!askPrice && !askStock && !askContent))
            sb.append("Tác giả: ").append(book.getAuthor() != null ? book.getAuthor() : "Chưa rõ").append("\n");
        if (askStock)
            sb.append("Tồn kho: ").append(book.getQuantity()).append(" cuốn\n");
        if (!askPrice && !askAuthor && !askStock)
            appendBookDetail(sb, book);

        if (askContent) {
            if (book.getPdfPath() != null && !book.getPdfPath().isBlank()
                    && pdfReaderService.isReadable(book.getPdfPath())) {
                appendPdfChunks(sb, List.of(book), userMessage);
            } else {
                sb.append("[Lưu ý: Tác phẩm này chưa có file PDF. Trả lời dựa trên trường MÔ TẢ.]\n");
            }
        }
        sb.append("[HƯỚNG DẪN AI: Dùng ##, phân vùng bằng ---, bullet * để trả lời trọng tâm.]\n");
    }

    /**
     * Xử lý intent CONTENT_QUESTION: tìm sách liên quan và đính kèm
     * nội dung PDF (nếu có) hoặc metadata làm context phân tích.
     */
    private void handleContentQuestion(StringBuilder sb, List<Book> allBooks, String userMessage) {
        List<Book> candidates = findBooksForContentQuestion(allBooks, userMessage);
        if (!candidates.isEmpty()) {
            boolean hasPdf = appendPdfChunks(sb, candidates, userMessage);
            if (!hasPdf) {
                sb.append("=== METADATA SÁCH LIÊN QUAN ===\n");
                candidates.stream().limit(3).forEach(b -> {
                    appendBookDetail(sb, b);
                    sb.append("\n");
                });
                sb.append("[Lưu ý: Chưa có PDF chi tiết. Phân tích dựa trên MÔ TẢ.]\n");
            }
        } else {
            boolean found = appendPdfChunksFromAllBooks(sb, allBooks, userMessage);
            if (!found)
                sb.append("[HƯỚNG DẪN AI: Không phát hiện dữ liệu liên quan. "
                        + "Đề xuất người dùng cung cấp tên sách cụ thể.]\n");
        }
    }

    /**
     * Xử lý intent PRICE_INFO: hiển thị bảng giá cho danh mục sách liên quan.
     */
    private void handlePriceInfo(StringBuilder sb, List<Book> allBooks, String userMessage) {
        Book one = findBestBookFromQuery(allBooks, userMessage);
        if (one != null) {
            handleSpecificBook(sb, allBooks, userMessage);
            return;
        }
        List<Book> toShow = findRelevantBooks(allBooks, userMessage, true)
                .stream().filter(b -> b.getPrice() != null).limit(10).collect(Collectors.toList());

        if (toShow.isEmpty()) {
            sb.append("[Chưa ghi nhận thông tin giá cho danh mục này.]\n");
            return;
        }
        sb.append("=== BẢNG BÁO GIÁ ===\n");
        for (Book b : toShow)
            sb.append("* \"").append(b.getTitle()).append("\" | Giá: ")
                    .append(formatPrice(b.getPrice())).append(" | Kho: ").append(b.getQuantity()).append("\n");
        sb.append("[HƯỚNG DẪN AI: Dùng ## Bảng giá sách, --- và danh sách *.]\n");
    }

    /**
     * Xử lý intent CATALOG_SEARCH: tìm kiếm sách theo từ khóa hoặc tên tác giả.
     */
    private void handleCatalogSearch(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String authorName = extractAuthorNameFromQuery(userMessage);
        if (!authorName.isBlank()) {
            List<Book> byAuthor = findBooksByAuthor(allBooks, authorName);
            if (!byAuthor.isEmpty()) {
                sb.append("=== SÁCH THEO TÁC GIẢ (").append(byAuthor.size()).append(" cuốn) ===\n");
                byAuthor.forEach(b -> appendBookLine(sb, b));
                sb.append("[HƯỚNG DẪN AI: Trình bày danh sách sách của tác giả đã xác định.]\n");
                return;
            }
        }
        List<Book> relevant = findRelevantBooks(allBooks, userMessage, true);
        if (relevant.isEmpty()) {
            sb.append("[Không tìm thấy kết quả. Hướng dẫn người dùng dùng từ khóa khái quát hơn.]\n");
            return;
        }
        sb.append("=== KẾT QUẢ TRA CỨU (").append(relevant.size()).append(" đầu sách) ===\n");
        relevant.forEach(b -> appendBookLine(sb, b));
        sb.append("[HƯỚNG DẪN AI: Dùng ## Kết quả tra cứu, ---, danh sách gợi ý trực quan.]\n");
    }

    /**
     * Xử lý intent GENERAL: hỏi chung chung hoặc câu chào.
     * Nếu có từ khóa liên quan đến sách, gợi ý tác phẩm tiêu biểu.
     */
    private void handleGeneralQuery(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String n = normalizeSearch(userMessage);
        if (n.contains("sach") || n.contains("doc") || n.contains("goi y")) {
            List<Book> relevant = findRelevantBooks(allBooks, userMessage, false);
            if (!relevant.isEmpty()) {
                sb.append("=== GỢI Ý TÁC PHẨM TIÊU BIỂU ===\n");
                relevant.stream().limit(5).forEach(b -> appendBookLine(sb, b));
            }
        }
    }

    // RAG PIPELINE — Chunking & Retrieval

    /**
     * Phân đoạn văn bản thành các chunk có kích thước chuẩn với overlap.
     *
     * <p>
     * Tách văn bản theo câu trước, sau đó gom lại thành chunk.
     * Overlap giữ lại phần cuối của chunk trước để không mất ngữ cảnh
     * tại điểm nối giữa hai chunk.
     *
     * <p>
     * TODO: Sau khi chunk, gọi Embedding API để tạo vector 768 chiều
     * cho mỗi chunk, lưu vào bảng book_chunks (book_id, chunk_index, chunk_text,
     * embedding).
     *
     * @param text văn bản cần chia chunk
     * @return danh sách các chunk
     */
    private List<String> chunkText(String text) {
        if (text == null || text.isBlank())
            return List.of();

        String[] sentences = text.split("(?<=[.!?\\n])\\s+");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isBlank())
                continue;

            if (current.length() + trimmed.length() > CHUNK_SIZE && !current.isEmpty()) {
                chunks.add(current.toString().trim());
                // Overlap: giữ lại phần cuối của chunk trước để tránh mất ngữ cảnh
                String cur = current.toString();
                int overlapStart = Math.max(0, cur.length() - CHUNK_OVERLAP);
                current = new StringBuilder(cur.substring(overlapStart)).append(" ");
            }
            current.append(trimmed).append(" ");
        }
        if (!current.isEmpty())
            chunks.add(current.toString().trim());
        return chunks;
    }

    /**
     * Truy xuất top-K chunk liên quan nhất từ văn bản PDF bằng BM25-style scoring.
     *
     * <p>
     * Quy trình:
     * <ol>
     * <li>Chunk văn bản → lọc quality gate (loại chunk ngắn, boilerplate)</li>
     * <li>Tính IDF cho từng token của query</li>
     * <li>Score mỗi chunk bằng TF×IDF với TF saturation (BM25)</li>
     * <li>Lấy top-K chunk cao điểm nhất, sắp xếp lại theo thứ tự gốc</li>
     * </ol>
     *
     * @param pdfText   văn bản đầy đủ của PDF
     * @param userQuery câu hỏi của user
     * @param topK      số chunk tối đa cần trả về
     * @return chuỗi top-K chunk ghép lại bằng dấu xuống dòng đôi
     */
    private String retrieveTopChunks(String pdfText, String userQuery, int topK) {
        List<String> chunks = chunkText(pdfText);
        if (chunks.isEmpty())
            return "";

        // Quality gate: loại chunk quá ngắn, ít chữ, hoặc là boilerplate xuất bản
        List<String> qualityChunks = chunks.stream()
                .filter(chunk -> {
                    if (chunk.length() < 80)
                        return false;
                    long letters = chunk.chars().filter(Character::isLetter).count();
                    if ((double) letters / chunk.length() < 0.45)
                        return false;
                    return !isPublisherInfoBlock(chunk);
                })
                .collect(Collectors.toList());

        if (qualityChunks.isEmpty())
            return "";

        String[] queryTokens = Arrays.stream(normalizeSearch(userQuery).split("\\s+"))
                .filter(t -> t.length() >= 3 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);

        if (queryTokens.length == 0)
            return qualityChunks.stream().limit(topK).collect(Collectors.joining("\n\n"));

        // Tính IDF đơn giản: log((N+1)/(df+1)) + 1
        int totalChunks = qualityChunks.size();
        Map<String, Double> idf = new HashMap<>();
        for (String token : queryTokens) {
            long df = qualityChunks.stream().filter(c -> normalizeSearch(c).contains(token)).count();
            idf.put(token, Math.log((double) (totalChunks + 1) / (df + 1)) + 1.0);
        }

        // Score từng chunk bằng BM25-style TF saturation (k1=1.5)
        record IndexedChunk(int index, String text, double score) {
        }
        List<IndexedChunk> scored = new ArrayList<>();
        for (int i = 0; i < qualityChunks.size(); i++) {
            String nc = normalizeSearch(qualityChunks.get(i));
            double score = 0;
            for (String token : queryTokens) {
                int tf = countOccurrences(nc, token);
                if (tf > 0) {
                    double tfSat = (tf * 2.5) / (tf + 1.5);
                    score += tfSat * idf.getOrDefault(token, 1.0);
                    // Boost thêm cho token dài (từ chuyên ngành quan trọng hơn)
                    if (token.length() >= 6)
                        score += idf.getOrDefault(token, 1.0) * 0.5;
                }
            }
            if (score > 0)
                scored.add(new IndexedChunk(i, qualityChunks.get(i), score));
        }

        if (scored.isEmpty())
            return qualityChunks.stream().limit(topK).collect(Collectors.joining("\n\n"));

        // Sắp xếp theo score, lấy top-K, rồi sắp xếp lại theo thứ tự gốc để đọc tự
        // nhiên
        return scored.stream()
                .sorted(Comparator.comparingDouble(IndexedChunk::score).reversed())
                .limit(topK)
                .sorted(Comparator.comparingInt(IndexedChunk::index))
                .map(IndexedChunk::text)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * Đếm số lần xuất hiện (không phân biệt hoa/thường) của token trong text.
     *
     * @param text  chuỗi cần tìm
     * @param token từ cần đếm
     * @return số lần xuất hiện
     */
    private int countOccurrences(String text, String token) {
        if (text == null || token == null || token.isBlank())
            return 0;
        int count = 0, idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }

    /**
     * Lấy top-K chunk liên quan từ danh sách sách có PDF.
     * Ưu tiên sách có PDF đọc được, giới hạn tổng ký tự để không vượt
     * context window của Gemini.
     *
     * @param sb          StringBuilder để append kết quả
     * @param books       danh sách sách cần tìm chunk
     * @param userMessage câu hỏi của user
     * @return true nếu có ít nhất một chunk được thêm vào
     */
    private boolean appendPdfChunks(StringBuilder sb, List<Book> books, String userMessage) {
        List<Book> sorted = books.stream()
                .sorted(Comparator.comparingInt(
                        b -> (b.getPdfPath() != null && pdfReaderService.isReadable(b.getPdfPath())) ? 0 : 1))
                .collect(Collectors.toList());

        final int MAX_TOTAL_CHARS = 12_000;
        int totalCharsAdded = 0;
        int added = 0;

        for (Book b : sorted) {
            if (totalCharsAdded >= MAX_TOTAL_CHARS || added >= 3)
                break;
            String path = b.getPdfPath();
            if (path == null || path.isBlank() || !pdfReaderService.isReadable(path))
                continue;

            String pdfText;
            try {
                pdfText = extractPdfCached(path);
            } catch (Exception e) {
                System.err.println("[ChatService] Lỗi trích PDF bookId=" + b.getId() + ": " + e.getMessage());
                continue;
            }
            if (pdfText == null || pdfText.isBlank() || pdfText.startsWith("Không đọc được"))
                continue;

            String cleaned = filterBoilerplateFromPdf(pdfText);
            String source = cleaned.isBlank() ? pdfText : cleaned;
            int allowedChars = Math.min(6_000, MAX_TOTAL_CHARS - totalCharsAdded);

            String chunks = retrieveTopChunks(source, userMessage, TOP_K_CHUNKS);
            if (chunks.isBlank())
                continue;

            String snippet = safeSubstring(chunks, allowedChars);
            String label = (b.getPdfName() != null && !b.getPdfName().isBlank())
                    ? b.getPdfName()
                    : b.getTitle();
            sb.append("=== TRÍCH ĐOẠN PDF (\"").append(b.getTitle()).append("\" | ")
                    .append(label).append(") ===\n").append(snippet).append("\n");

            totalCharsAdded += snippet.length();
            added++;
        }
        return added > 0;
    }

    /**
     * Tìm kiếm toàn bộ sách có PDF để lấy chunk liên quan đến query.
     * Dừng sớm khi tìm đủ 2 sách để tránh quét toàn bộ tập sách.
     *
     * @param sb          StringBuilder để append kết quả
     * @param allBooks    toàn bộ sách
     * @param userMessage câu hỏi của user
     * @return true nếu tìm được chunk liên quan
     */
    private boolean appendPdfChunksFromAllBooks(StringBuilder sb,
            List<Book> allBooks, String userMessage) {
        String nq = normalizeSearch(userMessage);
        String[] queryTokens = Arrays.stream(nq.split("\\s+"))
                .filter(t -> t.length() >= 4 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);
        if (queryTokens.length == 0)
            return false;

        int minMatch = Math.min(2, (int) Math.ceil(queryTokens.length / 2.0));
        List<Book> matchingBooks = new ArrayList<>();

        for (Book b : allBooks) {
            if (matchingBooks.size() >= 2)
                break; // dừng sớm khi đủ 2 sách
            String path = b.getPdfPath();
            if (path == null || path.isBlank() || !pdfReaderService.isReadable(path))
                continue;

            try {
                String pdfText = extractPdfCached(path);
                if (pdfText == null || pdfText.startsWith("Không đọc được"))
                    continue;
                String cleaned = filterBoilerplateFromPdf(pdfText);
                String np = normalizeSearch(cleaned.isBlank() ? pdfText : cleaned);
                long count = Arrays.stream(queryTokens).filter(np::contains).count();
                if (count >= minMatch)
                    matchingBooks.add(b);
            } catch (Exception e) {
                System.err.println("[ChatService] Quét PDF bookId=" + b.getId() + ": " + e.getMessage());
            }
        }
        if (matchingBooks.isEmpty())
            return false;
        return appendPdfChunks(sb, matchingBooks, userMessage);
    }

    // GEMINI PAYLOAD BUILDING

    /**
     * Đóng gói context và câu hỏi theo chuẩn XML để AI phân biệt rõ ràng
     * dữ liệu nghiệp vụ và câu hỏi của user.
     *
     * @param context   nội dung context từ RAG pipeline
     * @param userQuery câu hỏi của user
     * @return chuỗi XML chứa internal_context và user_query
     */
    private String buildXmlUserPayload(String context, String userQuery) {
        return "<internal_context>\n" + context + "\n</internal_context>\n\n"
                + "<user_query>\n" + userQuery + "\n</user_query>";
    }

    /**
     * Xây dựng JSON payload gửi Gemini API cho chat có lịch sử hội thoại.
     *
     * <p>
     * Cấu trúc:
     * <ul>
     * <li>system_instruction: role + formatting rules (BASE_INSTRUCTION)</li>
     * <li>contents: lịch sử hội thoại + tin nhắn mới bọc trong XML context</li>
     * <li>generationConfig: temperature=0.1 để tránh hallucination</li>
     * </ul>
     *
     * <p>
     * Lưu ý: History model messages được rút gọn xuống 800 ký tự để
     * tiết kiệm context window nhưng vẫn đủ ngữ cảnh cho follow-up.
     *
     * @param history           lịch sử hội thoại từ DB
     * @param contextPayload    context RAG đã dựng
     * @param newMessage        tin nhắn mới của user
     * @param systemInstruction system prompt
     * @return JSON string payload
     */
    private String buildGeminiPayload(List<ChatMessage> history, String contextPayload,
            String newMessage, String systemInstruction) throws Exception {
        List<Map<String, Object>> contents = new ArrayList<>();
        int historyStart = Math.max(0, history.size() - maxHistoryMessages);

        // Đảm bảo lượt đầu tiên là "user" theo yêu cầu Gemini API
        while (historyStart < history.size()
                && !"user".equals(history.get(historyStart).getRole())) {
            historyStart++;
        }

        String lastRole = null;
        for (int i = historyStart; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            String role = m.getRole();
            String msgText = m.getMessage() != null ? m.getMessage() : "";

            // Rút gọn model response dài để tiết kiệm context window
            if ("model".equals(role) && msgText.length() > 800)
                msgText = msgText.substring(0, 800) + "…[rút gọn]";

            // Gộp liên tiếp cùng role thành một turn (tránh lỗi API)
            if (role.equals(lastRole) && !contents.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parts = (List<Map<String, Object>>) contents.get(contents.size() - 1)
                        .get("parts");
                Map<String, Object> textMap = parts.get(0);
                textMap.put("text", textMap.get("text") + "\n" + msgText);
            } else {
                Map<String, Object> content = new LinkedHashMap<>();
                content.put("role", role);
                List<Map<String, Object>> parts = new ArrayList<>();
                parts.add(new LinkedHashMap<>(Map.of("text", msgText)));
                content.put("parts", parts);
                contents.add(content);
                lastRole = role;
            }
        }

        // Bọc tin nhắn mới trong XML context theo chuẩn RAG
        String finalMessage = (newMessage != null && !newMessage.isBlank()) ? newMessage : "Xin chào";
        String userPayload = buildXmlUserPayload(trimSystemInstruction(contextPayload), finalMessage);

        if (contents.isEmpty()) {
            contents.add(buildUserTurn(userPayload));
        } else {
            Map<String, Object> lastContent = contents.get(contents.size() - 1);
            if ("user".equals(lastContent.get("role"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parts = (List<Map<String, Object>>) lastContent.get("parts");
                parts.get(0).put("text", userPayload);
            } else {
                contents.add(buildUserTurn(userPayload));
            }
        }
        return buildPayloadJson(systemInstruction, contents);
    }

    /**
     * Xây dựng JSON payload đơn giản (không có lịch sử hội thoại).
     * Dùng cho askAboutPdf và các tác vụ single-turn.
     *
     * @param system      system prompt
     * @param userMessage nội dung user message (đã bao gồm XML context)
     * @return JSON string payload
     */
    private String buildSimplePayload(String system, String userMessage) throws Exception {
        List<Map<String, Object>> contents = List.of(buildUserTurn(userMessage));
        return buildPayloadJson(system, contents);
    }

    /**
     * Helper: tạo một user turn cho contents array.
     */
    private Map<String, Object> buildUserTurn(String text) {
        Map<String, Object> turn = new LinkedHashMap<>();
        turn.put("role", "user");
        turn.put("parts", List.of(Map.of("text", text)));
        return turn;
    }

    /**
     * Helper: serialize payload JSON cuối cùng với generationConfig chuẩn.
     */
    private String buildPayloadJson(String system,
            List<Map<String, Object>> contents) throws Exception {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("topP", 0.85);
        generationConfig.put("topK", 40);
        generationConfig.put("maxOutputTokens", 2048);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", system))));
        payload.put("contents", contents);
        payload.put("generationConfig", generationConfig);
        return mapper.writeValueAsString(payload);
    }

    // GEMINI API CALLS

    /**
     * Gọi Gemini API (blocking) với cơ chế fallback qua nhiều model.
     *
     * <p>
     * Chỉ retry khi gặp lỗi 429 (quota) hoặc 503 (quá tải).
     * Không retry lỗi 404 (model không tồn tại) vì retry vô nghĩa.
     *
     * @param payload JSON payload
     * @return response body string từ Gemini
     */
    private String callGeminiWithFallback(String payload) {
        List<String> models = buildModelList();
        RuntimeException lastError = null;
        for (String model : models) {
            try {
                return callGemini(model, payload);
            } catch (RuntimeException ex) {
                lastError = ex;
                if (!isRetryableGeminiError(ex))
                    throw ex;
                System.err.println("[ChatService] Model " + model + " quá tải: " + ex.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw lastError != null ? lastError
                : new RuntimeException("Tất cả Gemini model tạm thời không khả dụng.");
    }

    /**
     * Gọi Gemini API blocking cho một model cụ thể.
     * Dùng subscribeOn(boundedElastic) để tránh block Netty I/O thread.
     *
     * @param model   tên model Gemini (ví dụ: gemini-2.0-flash)
     * @param payload JSON payload
     * @return response body string
     */
    private String callGemini(String model, String payload) {
        String url = GEMINI_BASE + model + ":generateContent?key=" + geminiApiKey;
        return webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.isError(),
                        resp -> resp.bodyToMono(String.class)
                                .map(body -> new RuntimeException(
                                        "Gemini API Lỗi " + resp.statusCode() + ": " + body)))
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic())
                .block();
    }

    /**
     * Gọi Gemini API dạng stream (SSE) với fallback qua nhiều model.
     *
     * <p>
     * Chỉ fallback khi 429 hoặc 503. Không fallback khi 404.
     *
     * @param payload JSON payload
     * @return Flux stream các text chunk từ Gemini
     */
    private Flux<String> callGeminiStreamWithFallback(String payload) {
        List<String> models = buildModelList();
        Flux<String> flux = callGeminiStream(models.get(0), payload)
                .doOnSubscribe(s -> System.out.println(
                        "[ChatService] Stream dùng model: " + models.get(0)));

        for (int i = 1; i < models.size(); i++) {
            final String nextModel = models.get(i);
            flux = flux.onErrorResume(ex -> {
                String msg = ex.getMessage() != null ? ex.getMessage() : "";
                if (msg.contains("404") || msg.contains("NOT_FOUND")) {
                    System.err.println("[ChatService] Model 404 — không fallback: " + msg);
                    return Flux.error(ex);
                }
                System.err.println("[ChatService] Stream fallback sang: " + nextModel
                        + " | Lý do: " + msg.substring(0, Math.min(80, msg.length())));
                return Flux.defer(() -> callGeminiStream(nextModel, payload))
                        .delaySubscription(java.time.Duration.ofMillis(500));
            });
        }
        return flux.onErrorResume(ex -> {
            System.err.println("[ChatService] Tất cả stream model thất bại: " + ex.getMessage());
            return Flux.error(ex);
        });
    }

    /**
     * Gọi Gemini streaming API và parse SSE response thành Flux text chunks.
     *
     * @param model   tên model Gemini
     * @param payload JSON payload
     * @return Flux của các text chunk
     */
    private Flux<String> callGeminiStream(String model, String payload) {
        String url = GEMINI_BASE + model + ":streamGenerateContent?alt=sse&key=" + geminiApiKey;
        return webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> status.isError(),
                        resp -> resp.bodyToMono(String.class)
                                .map(body -> new RuntimeException(
                                        "Gemini Stream Lỗi " + resp.statusCode() + ": " + body)))
                .bodyToFlux(String.class)
                .flatMap(responseBody -> {
                    List<String> chunks = new ArrayList<>();
                    for (String line : responseBody.split("\n")) {
                        String t = line.trim();
                        if (t.isBlank())
                            continue;
                        String json = t.startsWith("data:") ? t.substring(5).trim() : t;
                        if ("[DONE]".equals(json))
                            continue;
                        try {
                            if (json.startsWith("{")) {
                                String text = extractTextFromGeminiJson(json);
                                if (text != null)
                                    chunks.add(text);
                            }
                        } catch (Exception e) {
                            System.err.println("[ChatService] Parse SSE JSON lỗi: " + e.getMessage());
                        }
                    }
                    return Flux.fromIterable(chunks);
                });
    }

    /**
     * Parse response body JSON của Gemini và trích xuất text phản hồi.
     *
     * @param responseBody JSON string từ Gemini API
     * @return text phản hồi của model
     * @throws RuntimeException nếu cấu trúc JSON không hợp lệ
     */
    @SuppressWarnings("unchecked")
    private String parseGeminiResponse(String responseBody) throws Exception {
        Map<String, Object> response = mapper.readValue(responseBody, Map.class);
        if (response.containsKey("error"))
            throw new RuntimeException("Lỗi Gemini: " + response.get("error"));

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty())
            throw new RuntimeException("Mô hình không thể phản hồi.");

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null)
            throw new RuntimeException("Cấu trúc JSON phản hồi trống.");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty() || parts.get(0).get("text") == null)
            throw new RuntimeException("Không tìm thấy text trong phản hồi.");

        return (String) parts.get(0).get("text");
    }

    /**
     * Helper: trích xuất text từ một JSON chunk của SSE stream.
     * Trả về null nếu không tìm thấy text.
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiJson(String json) {
        try {
            Map<String, Object> response = mapper.readValue(json, Map.class);
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty())
                return null;
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null)
                return null;
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty())
                return null;
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Xây dựng danh sách model theo thứ tự ưu tiên: primary model trước,
     * fallback models theo sau.
     *
     * @return danh sách tên model không trùng lặp
     */
    private List<String> buildModelList() {
        List<String> models = new ArrayList<>();
        models.add(geminiModel);
        if (fallbackModels != null && !fallbackModels.isBlank()) {
            for (String m : fallbackModels.split(",")) {
                String t = m.trim();
                if (!t.isEmpty() && !models.contains(t))
                    models.add(t);
            }
        }
        return models;
    }

    /**
     * Kiểm tra lỗi Gemini có đáng retry không.
     * Chỉ retry 429 (quota hết) và 503 (server quá tải).
     *
     * @param ex exception từ Gemini API call
     * @return true nếu nên retry
     */
    private boolean isRetryableGeminiError(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        return msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("503");
    }

    // FOLLOW-UP & CONTEXT RESOLUTION

    /**
     * Bổ sung ngữ cảnh từ lịch sử chat vào câu hỏi hiện tại nếu đây
     * là câu hỏi tiếp nối (follow-up).
     *
     * <p>
     * BUG FIX: Chỉ lấy messages của role "user" từ history (không lấy
     * model response) để tránh nhiễu context. Giới hạn topic 80 ký tự.
     *
     * <p>
     * Không merge nếu câu hỏi là PRICE_RANGE (đã có số liệu cụ thể)
     * hoặc là câu chào/cảm ơn (để tránh merge ngữ cảnh cũ).
     *
     * @param message tin nhắn hiện tại của user
     * @param history lịch sử hội thoại
     * @return effective message (có thể đã bổ sung ngữ cảnh)
     */
    private String resolveEffectiveMessage(String message, List<ChatMessage> history) {
        String n = normalizeSearch(message);

        // Không merge với câu hỏi khoảng giá có số liệu rõ ràng
        if (n.matches(".*(duoi|tren|tu.*den|khoang|xap xi|bang khoang).*(\\d+|k\\b|ngan|dong|trieu).*")
                || n.matches(".*(sach|cuon).*(gia|tien).*(tren|duoi|tu|khoang|bang).*(\\d+|k\\b|ngan|dong|trieu).*")
                || n.matches(".*(gia|tien).*(tren|duoi|tu|khoang).*(\\d+|k\\b).*")
                || n.matches(".*(gia tien|muc gia|tam gia).*(duoi|tren|tu|den|khoang).*")
                || n.matches(".*(duoi|tren|tu|khoang)\\s*(\\d+).*"))
            return message;

        // BUG FIX: Không merge khi câu đã chứa tên tác giả cụ thể.
        // Nếu merge, effectiveMessage sẽ chứa cả tác giả cũ lẫn tác giả mới,
        // khiến extractAuthorNameFromQuery chọn nhầm tác giả từ lịch sử.
        if (!extractAuthorNameFromQuery(message).isBlank())
            return message;

        if (isFollowUpQuestion(message) && !history.isEmpty()) {
            String topic = extractTopicFromHistory(history, 2);
            if (!topic.isBlank())
                return topic + " " + message;
        }
        return message;
    }

    /**
     * Kiểm tra câu hỏi có phải là follow-up không dựa trên từ khóa
     * hoặc độ ngắn của câu.
     *
     * <p>
     * BUG FIX: Loại trừ câu chào/cảm ơn để tránh nhận nhầm
     * "cảm ơn bạn" là follow-up và merge với topic cũ.
     *
     * @param msg tin nhắn cần kiểm tra
     * @return true nếu là follow-up question
     */
    private boolean isFollowUpQuestion(String msg) {
        if (msg == null || msg.isBlank())
            return false;
        String n = normalizeSearch(msg);

        // Loại trừ câu chào và cảm ơn để tránh merge nhầm
        if (n.matches(".*(xin chao|chao ban|cam on|thank|ok roi|duoc roi|bye|tam biet|hi ban|hello).*"))
            return false;

        boolean hasFollowUpKeywords = n.matches(
                ".*(con.*nao|con gi|cuon khac|sach khac|cai khac|loai khac|them cai|them cuon|"
                        + "the con|vay con|con nua|gi nua|cuon do|sach do|no thi|cai do|"
                        + "the thi|vay thi|mua di|dat hang di|bao nhieu nua|gia nua|tac gia nua).*");
        // BUG FIX: Giảm ngưỡng từ 6 xuống 4 để tránh nhận nhầm câu hỏi
        // đầy đủ (ví dụ: "thông tin sách của tác giả X") là follow-up.
        boolean isVeryShort = msg.trim().split("\\s+").length <= 4;
        return hasFollowUpKeywords || isVeryShort;
    }

    /**
     * Trích xuất topic từ N messages user gần nhất trong lịch sử.
     *
     * <p>
     * BUG FIX: Chỉ lấy messages của role "user", bỏ qua model responses
     * để tránh nhiễu. Giới hạn 80 ký tự để không làm payload quá dài.
     *
     * @param history lịch sử hội thoại
     * @param recentN số lượng message gần nhất cần xem xét
     * @return chuỗi topic (đã trim), rỗng nếu không có
     */
    private String extractTopicFromHistory(List<ChatMessage> history, int recentN) {
        if (history == null || history.size() < 2)
            return "";
        int end = history.size() - 1; // Bỏ message cuối (là message hiện tại vừa save)
        int start = Math.max(0, end - recentN);
        StringBuilder ctx = new StringBuilder();
        for (int i = start; i < end; i++) {
            ChatMessage m = history.get(i);
            // Chỉ lấy user messages để tránh nhiễu từ AI responses
            if (!"user".equals(m.getRole()))
                continue;
            String content = m.getMessage();
            if (content != null && !content.isBlank())
                ctx.append(content).append(" ");
        }
        String topic = ctx.toString().trim();
        // Giới hạn 80 ký tự để không làm effectiveMessage quá dài
        return topic.length() > 80 ? topic.substring(0, 80) : topic;
    }

    // BOOK SEARCH & SCORING

    /**
     * Kiểm tra câu hỏi có liên quan đến nội dung sách không
     * (phân biệt với câu hỏi về metadata như giá, tác giả, kho).
     *
     * @param normalizedMsg câu hỏi đã normalize
     * @return true nếu là content question
     */
    private boolean isContentQuestion(String normalizedMsg) {
        if (normalizedMsg == null)
            return false;
        // Không phải content question nếu hỏi về metadata giá/kho
        if (normalizedMsg.matches(
                ".*(gia cao nhat|dat nhat|mac nhat|re nhat|gia thap nhat|gia bao nhieu|"
                        + "bao nhieu tien|sap xep gia|so sanh gia).*"))
            return false;

        String[] contentKeywords = {
                "y nghia", "giai thich", "la gi", "nhu the nao", "noi dung",
                "chuong", "phan ", "khai niem", "dinh nghia", "tu luyen",
                "nhan vat", "cot truyen", "tom tat", "phan tich", "dac diem",
                "nguyen ly", "phuong phap", "ky thuat", "cach thuc",
                "tai sao", "vi sao", "quan he", "moi lien he",
                "so sanh", "khac nhau", "giong nhau", "anh huong", "tac dong",
                "qua trinh", "co che", "nguyen nhan", "ket qua",
                "bai hoc", "thong diep", "tinh than", "triet ly", "dao ly",
                "la ai", "noi gi", "viet gi", "de cap", "dung den"
        };
        for (String kw : contentKeywords)
            if (normalizedMsg.contains(kw))
                return true;

        if (normalizedMsg.matches(".*(la ai|la gi|la cai gi|nghia la gi).*"))
            return true;
        if (isCatalogQuestion(normalizedMsg))
            return false;
        if (normalizedMsg.contains("?")) {
            boolean isMeta = normalizedMsg.matches(
                    ".*(gia|bao nhieu|con hang|con may|ton kho|tac gia|nguoi viet|xuat ban).*");
            return !isMeta;
        }
        return false;
    }

    /**
     * Kiểm tra câu hỏi có phải là tìm kiếm danh mục sách không
     * (liệt kê sách, tìm sách theo chủ đề, gợi ý sách...).
     *
     * @param normalizedMsg câu hỏi đã normalize
     * @return true nếu là catalog question
     */
    private boolean isCatalogQuestion(String normalizedMsg) {
        if (normalizedMsg == null)
            return false;
        return normalizedMsg.matches(
                ".*(co sach|tim sach|goi y sach|sach nao|co gi|ban gi|sach hay|"
                        + "danh sach|xem sach|mua sach|dat mua|sach ve).*");
    }

    /**
     * Kiểm tra câu hỏi có hỏi về thông tin chi tiết một cuốn sách cụ thể không.
     *
     * @param userMessage câu hỏi gốc
     * @param allBooks    danh sách sách để so khớp tên
     * @return true nếu là câu hỏi thông tin sách cụ thể
     */
    private boolean isSpecificBookInfoQuestion(String userMessage, List<Book> allBooks) {
        if (userMessage == null)
            return false;
        String n = normalizeSearch(userMessage);
        if (extractBookTitleFromQuestion(n, allBooks) != null)
            return true;
        boolean asksInfo = n.matches(
                ".*\\b(thong tin|chi tiet|mo ta|gioi thieu|gia|bao nhieu|con hang|ton kho|"
                        + "tac gia|nguoi viet|xuat ban)\\b.*");
        boolean mentionsBook = n.matches(".*\\b(sach|cuon|book)\\b.*");
        return asksInfo && mentionsBook;
    }

    /**
     * Trích xuất tên sách từ câu hỏi bằng cách loại bỏ các từ phụ trợ
     * và so khớp với danh sách sách trong DB.
     *
     * @param normalizedQuery câu hỏi đã normalize
     * @param allBooks        danh sách sách để so khớp
     * @return tên sách nếu tìm thấy, null nếu không
     */
    private String extractBookTitleFromQuestion(String normalizedQuery, List<Book> allBooks) {
        if (normalizedQuery == null || normalizedQuery.isBlank())
            return null;
        String cleaned = normalizedQuery.replaceAll(
                "\\b(cho toi biet|cho minh biet|cho toi|cho minh|xem|ve|cua|thong tin|"
                        + "chi tiet|mo ta|gioi thieu|gia|bao nhieu|tac gia|nguoi viet|con hang|"
                        + "ton kho|sach|cuon|book|tom tat|doc|pdf|noi dung|chuong|tiet|phan|"
                        + "hoi|muon|can|hay|giup|nhe|nha|ad|mac|dat|re)\\b",
                " ")
                .replaceAll("\\s+", " ").trim();
        if (cleaned.length() < 3)
            return null;
        return allBooks.stream()
                .map(Book::getTitle)
                .filter(title -> {
                    String nt = normalizeSearch(title);
                    return nt.contains(cleaned) || cleaned.contains(nt);
                })
                .max(Comparator.comparingInt(String::length))
                .orElse(null);
    }

    /**
     * Tìm sách khớp nhất với câu hỏi thông tin chi tiết.
     * Ưu tiên exact match tên trước, fallback về relevance search.
     *
     * @param allBooks    danh sách sách
     * @param userMessage câu hỏi của user
     * @return sách phù hợp nhất hoặc null
     */
    private Book findSpecificBookInfoMatch(List<Book> allBooks, String userMessage) {
        Book best = findBestBookFromQuery(allBooks, userMessage);
        if (best != null)
            return best;
        if (!isSpecificBookInfoQuestion(userMessage, allBooks))
            return null;
        String extracted = extractBookTitleFromQuestion(normalizeSearch(userMessage), allBooks);
        if (extracted != null) {
            String nt = normalizeSearch(extracted);
            Book exact = allBooks.stream()
                    .filter(b -> normalizeSearch(b.getTitle()).equals(nt)).findFirst().orElse(null);
            if (exact != null)
                return exact;
            return allBooks.stream()
                    .filter(b -> b.getTitle().toLowerCase(Locale.ROOT)
                            .contains(extracted.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
        }
        List<Book> matched = findRelevantBooks(allBooks, userMessage, true);
        return matched.size() == 1 ? matched.get(0) : null;
    }

    /**
     * Tìm sách có điểm khớp cao nhất với query bằng cách scoring
     * tiêu đề, danh mục, tác giả và mô tả.
     *
     * @param allBooks    danh sách sách
     * @param userMessage câu hỏi của user
     * @return sách tốt nhất hoặc null nếu không đủ ngưỡng điểm (28)
     */
    private Book findBestBookFromQuery(List<Book> allBooks, String userMessage) {
        if (allBooks == null || allBooks.isEmpty()
                || userMessage == null || userMessage.isBlank())
            return null;
        String nq = normalizeSearch(userMessage);
        List<String> keywords = extractSearchKeywords(userMessage);
        if (keywords.isEmpty())
            return null;

        String phrase = String.join(" ", keywords);
        Book best = null;
        int bestScore = 0;

        for (Book b : allBooks) {
            int score = scoreBookMatch(b, keywords, userMessage);
            String title = normalizeSearch(b.getTitle());
            if (phrase.length() >= 4 && title.contains(phrase))
                score += 50;
            if (title.length() >= 3 && nq.contains(title))
                score += 40;
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return bestScore >= 28 ? best : null;
    }

    /**
     * Tìm danh sách sách liên quan đến câu hỏi nội dung.
     * Ưu tiên sách có PDF đọc được lên trước.
     *
     * @param allBooks    danh sách sách
     * @param userMessage câu hỏi của user
     * @return danh sách sách đã sort (PDF trước, không PDF sau)
     */
    private List<Book> findBooksForContentQuestion(List<Book> allBooks, String userMessage) {
        List<String> keywords = extractSearchKeywords(userMessage);
        List<Map.Entry<Book, Integer>> scored = new ArrayList<>();
        for (Book b : allBooks) {
            int score = scoreBookMatch(b, keywords, userMessage);
            if (score >= 20)
                scored.add(Map.entry(b, score));
        }
        scored.sort(Comparator.comparingInt(Map.Entry<Book, Integer>::getValue).reversed());
        List<Book> byMetadata = scored.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        List<Book> withPdf = byMetadata.stream()
                .filter(b -> b.getPdfPath() != null && pdfReaderService.isReadable(b.getPdfPath()))
                .collect(Collectors.toList());
        List<Book> withoutPdf = byMetadata.stream()
                .filter(b -> b.getPdfPath() == null || !pdfReaderService.isReadable(b.getPdfPath()))
                .collect(Collectors.toList());
        List<Book> result = new ArrayList<>(withPdf);
        result.addAll(withoutPdf);
        return result;
    }

    /**
     * Tìm danh sách sách liên quan đến query bằng keyword scoring.
     * Nếu query là generic (không có từ khóa cụ thể), trả về toàn bộ sách.
     *
     * @param books       danh sách sách để tìm kiếm
     * @param userMessage câu hỏi của user
     * @param strict      nếu true, yêu cầu điểm >= 20; false cho kết quả rộng hơn
     * @return danh sách sách đã sort theo điểm giảm dần
     */
    private List<Book> findRelevantBooks(List<Book> books, String userMessage, boolean strict) {
        if (books == null || books.isEmpty())
            return List.of();
        String q = userMessage != null ? userMessage.trim() : "";
        if (q.length() < 2)
            return List.of();
        if (isGenericBookQuery(q))
            return books.stream().limit(maxBooksInPrompt).collect(Collectors.toList());

        List<String> keywords = extractSearchKeywords(userMessage);
        if (keywords.isEmpty())
            return List.of();

        List<Map.Entry<Book, Integer>> scored = new ArrayList<>();
        for (Book b : books) {
            int score = scoreBookMatch(b, keywords, q);
            if (score >= 20)
                scored.add(Map.entry(b, score));
        }
        scored.sort(Comparator.comparingInt(Map.Entry<Book, Integer>::getValue).reversed());
        return scored.stream().map(Map.Entry::getKey).limit(maxBooksInPrompt).collect(Collectors.toList());
    }

    /**
     * Tính điểm khớp của một cuốn sách với danh sách từ khóa.
     *
     * <p>
     * Trọng số: phrase match tiêu đề (+80) > token tiêu đề (+28/35) >
     * danh mục (+22) > tác giả (+18) > mô tả (+8).
     *
     * @param b         sách cần tính điểm
     * @param keywords  danh sách từ khóa tìm kiếm
     * @param fullQuery câu hỏi đầy đủ (để boost phrase match)
     * @return điểm tổng
     */
    private int scoreBookMatch(Book b, List<String> keywords, String fullQuery) {
        String title = normalizeSearch(b.getTitle());
        String author = normalizeSearch(b.getAuthor() != null ? b.getAuthor() : "");
        String category = normalizeSearch(b.getCategory() != null ? b.getCategory() : "");
        String desc = normalizeSearch(b.getDescription() != null ? b.getDescription() : "");

        int score = 0;
        String phrase = String.join(" ", keywords);
        if (phrase.length() >= 4) {
            if (title.contains(phrase))
                score += 80;
            if (category.contains(phrase))
                score += 50;
        }
        for (String token : keywords) {
            if (token.length() < 2 || STOP_WORDS.contains(token))
                continue;
            if (title.contains(token))
                // BUG FIX: Token 2 ký tự (ví dụ "vu", "thi") rất dễ gây false positive
                // vì khớp với substring ngẫu nhiên trong title (ví dụ "thi" trong "thiên").
                // Giảm điểm token ngắn (<= 2 ký tự) để cần nhiều từ khóa hơn mới đạt ngưỡng 28.
                score += token.length() >= 5 ? 35 : (token.length() >= 3 ? 28 : 12);
            if (category.contains(token))
                score += 22;
            if (author.contains(token))
                score += 18;
            if (desc.contains(token))
                score += 8;
        }
        return score;
    }

    /**
     * Trích xuất danh sách từ khóa có nghĩa từ câu hỏi.
     * Loại bỏ stop words và từ quá ngắn (< 2 ký tự).
     *
     * @param userMessage câu hỏi của user
     * @return danh sách từ khóa phân biệt (distinct)
     */
    private List<String> extractSearchKeywords(String userMessage) {
        if (userMessage == null || userMessage.isBlank())
            return List.of();
        return Arrays.stream(normalizeSearch(userMessage).split("\\s+"))
                .filter(t -> t.length() >= 2 && !STOP_WORDS.contains(t))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Trích xuất tên tác giả từ câu hỏi dạng "sách của [tên tác giả]",
     * "tác giả là [tên]", "ai viết [sách]"...
     *
     * @param userMessage câu hỏi gốc
     * @return tên tác giả đã normalize, rỗng nếu không tìm thấy
     */
    private String extractAuthorNameFromQuery(String userMessage) {
        if (userMessage == null)
            return "";
        String n = normalizeSearch(userMessage);
        // BUG FIX: Thêm "sach cua" và "cuon cua" để nhận dạng dạng
        // "những cuốn sách của Vũ Thị F" / "sách của tác giả X".
        // Đặt "tac gia la" và "tac gia" trước "sach cua" để tránh nhận nhầm
        // khi câu vừa chứa "tác giả" vừa chứa "sách của".
        String[] patterns = {
                "tac gia la", "tac gia",
                "nguoi viet la", "nguoi viet",
                "ai viet la",
                "sach cua tac gia", "cuon cua tac gia",
                "sach cua", "cuon cua",
                "boi"
        };
        for (String pat : patterns) {
            int idx = n.indexOf(pat);
            if (idx >= 0) {
                String after = n.substring(idx + pat.length()).trim()
                        .replaceAll("^(la|ten|la ten)\\s+", "").trim();
                // Loại bỏ các từ phụ trợ thừa ở đầu (ví dụ: "la tac gia X")
                after = after.replaceAll("^(tac gia|nguoi viet|ai viet)\\s+", "").trim();
                if (!after.isBlank())
                    return after;
            }
        }
        return "";
    }

    /**
     * Tìm sách theo tên tác giả — yêu cầu ít nhất 2 token của tên
     * khớp với trường author trong DB.
     *
     * @param allBooks    danh sách sách
     * @param authorQuery chuỗi tên tác giả cần tìm
     * @return danh sách sách của tác giả
     */
    private List<Book> findBooksByAuthor(List<Book> allBooks, String authorQuery) {
        if (allBooks == null || authorQuery == null || authorQuery.isBlank())
            return List.of();
        String nq = normalizeSearch(authorQuery);
        List<String> authorTokens = Arrays.stream(nq.split("\\s+"))
                .filter(t -> !t.isEmpty() && !STOP_WORDS.contains(t))
                .collect(Collectors.toList());
        if (authorTokens.isEmpty())
            return List.of();
        return allBooks.stream()
                .filter(b -> {
                    if (b.getAuthor() == null)
                        return false;
                    String na = normalizeSearch(b.getAuthor());
                    long matchCount = authorTokens.stream().filter(na::contains).count();
                    return matchCount >= Math.min(2, authorTokens.size());
                })
                .collect(Collectors.toList());
    }

    /**
     * Kiểm tra query có phải là câu hỏi chung về sách không có từ khóa
     * chuyên ngành cụ thể (ví dụ: "có sách gì?", "gợi ý sách").
     *
     * @param q câu hỏi của user
     * @return true nếu là generic book query
     */
    private boolean isGenericBookQuery(String q) {
        if (q == null || q.isBlank())
            return false;
        String n = normalizeSearch(q);
        return n.matches(".*\\b(sach|danh sach|co gi|ban gi|goi y|xem sach)\\b.*")
                && !n.matches(
                        ".*\\b(java|python|lap trinh|lich su|van hoc|toan|van|anh|sinh|ky nang|"
                                + "kinh doanh|tam ly|triet hoc|khoa hoc|the thao|ban hang|marketing|"
                                + "kinh te|giao duc|y hoc|cong nghe)\\b.*");
    }

    /**
     * Trích xuất topic chính từ câu hỏi bằng cách loại bỏ stop words
     * và ghép các token còn lại.
     *
     * @param userMessage câu hỏi gốc
     * @return chuỗi topic (đã trim)
     */
    private String extractTopicFromQuestion(String userMessage) {
        String n = normalizeSearch(userMessage);
        return Arrays.stream(n.split("\\s+"))
                .filter(token -> token.length() >= 2 && !STOP_WORDS.contains(token))
                .collect(Collectors.joining(" ")).trim();
    }

    // PRICE PARSING

    /**
     * Parse khoảng giá từ câu tự nhiên tiếng Việt.
     *
     * <p>
     * Hỗ trợ các dạng:
     * <ul>
     * <li>"trên 100k" / "dưới 200.000đ"</li>
     * <li>"từ 50k đến 150k"</li>
     * <li>"khoảng 100.000đ" → ±25%</li>
     * </ul>
     *
     * @param userMessage câu hỏi chứa thông tin giá
     * @return long[]{minPrice, maxPrice} — -1 nghĩa là không giới hạn phía đó
     */
    private long[] parsePriceRange(String userMessage) {
        if (userMessage == null)
            return new long[] { -1, -1 };
        // Chuẩn hóa: bỏ dấu chấm ngàn, "k"/"K" → "000"
        String msg = userMessage.toLowerCase()
                .replaceAll("(\\d+)\\.(\\d{3})", "$1$2")
                .replaceAll("(\\d+)\\s*k\\b", "$1000");

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(msg);
        List<Long> nums = new ArrayList<>();
        while (m.find()) {
            long v = Long.parseLong(m.group(1));
            if (v > 0 && v < 1000)
                v *= 1000; // tự động nhân 1000 cho giá trị quá nhỏ
            if (v >= 1000)
                nums.add(v);
        }

        String n = normalizeSearch(userMessage);
        long min = -1, max = -1;
        if (n.matches(".*(tu|trong khoang|trong muc).*(den|toi).*") && nums.size() >= 2) {
            min = Math.min(nums.get(0), nums.get(1));
            max = Math.max(nums.get(0), nums.get(1));
        } else if (n.matches(".*(khoang|xap xi|bang khoang|gan).*") && !nums.isEmpty()) {
            long v = nums.get(0);
            min = (long) (v * 0.75);
            max = (long) (v * 1.25);
        } else if (n.matches(".*(duoi|khong qua|toi da|it hon|nho hon|thap hon).*") && !nums.isEmpty()) {
            max = nums.get(0);
        } else if (n.matches(".*(tren|it nhat|toi thieu|lon hon|cao hon|nhieu hon).*") && !nums.isEmpty()) {
            min = nums.get(0);
        } else if (!nums.isEmpty()) {
            // Fallback: phán đoán theo từ ngữ còn lại
            if (n.contains("tren") || n.contains("cao") || n.contains("dat") || n.contains("mac"))
                min = nums.get(0);
            else if (n.contains("duoi") || n.contains("re") || n.contains("thap"))
                max = nums.get(0);
            else
                min = nums.get(0);
        }
        return new long[] { min, max };
    }

    /**
     * Tạo label mô tả khoảng giá (ví dụ: "TỪ 50.000đ ĐẾN 150.000đ").
     * Dùng chung cho handlePriceRange và formatPriceRangeAnswer.
     *
     * @param min giá tối thiểu (-1 = không giới hạn)
     * @param max giá tối đa (-1 = không giới hạn)
     * @return chuỗi label đã format
     */
    private String buildPriceRangeLabel(long min, long max) {
        if (min >= 0 && max >= 0)
            return "TỪ " + formatPrice(BigDecimal.valueOf(min))
                    + " ĐẾN " + formatPrice(BigDecimal.valueOf(max));
        if (min >= 0)
            return "TRÊN " + formatPrice(BigDecimal.valueOf(min));
        if (max >= 0)
            return "DƯỚI " + formatPrice(BigDecimal.valueOf(max));
        return "TẤT CẢ MỨC GIÁ";
    }

    // BOILERPLATE FILTER

    /**
     * Lọc bỏ các dòng boilerplate từ văn bản PDF (thông tin xuất bản, địa chỉ,
     * số điện thoại, ISBN...) để giữ lại phần nội dung tri thức thuần túy.
     *
     * @param rawPdfText văn bản PDF thô
     * @return văn bản đã lọc boilerplate
     */
    private String filterBoilerplateFromPdf(String rawPdfText) {
        if (rawPdfText == null || rawPdfText.isBlank())
            return "";
        String[] lines = rawPdfText.split("\\r?\\n");
        StringBuilder filtered = new StringBuilder();
        StringBuilder blockBuf = new StringBuilder();
        int consecutiveBoilerplate = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 4) {
                // Dòng trắng: flush block buffer nếu không phải boilerplate
                if (!blockBuf.isEmpty()) {
                    String block = blockBuf.toString().trim();
                    if (!isPublisherInfoBlock(block))
                        filtered.append(block).append("\n\n");
                    blockBuf.setLength(0);
                    consecutiveBoilerplate = 0;
                }
                continue;
            }
            String normalized = normalizeSearch(trimmed);
            if (isBoilerplateSentence(normalized)) {
                consecutiveBoilerplate++;
                blockBuf.append(trimmed).append("\n");
            } else {
                if (consecutiveBoilerplate >= 3) {
                    blockBuf.setLength(0); // block chủ yếu boilerplate → bỏ
                } else if (!blockBuf.isEmpty()) {
                    filtered.append(blockBuf).append("\n");
                    blockBuf.setLength(0);
                }
                filtered.append(line).append("\n");
                consecutiveBoilerplate = 0;
            }
        }
        // Flush buffer cuối
        if (!blockBuf.isEmpty()) {
            String block = blockBuf.toString().trim();
            if (!isPublisherInfoBlock(block))
                filtered.append(block);
        }
        return filtered.toString().trim();
    }

    /**
     * Kiểm tra một câu đơn lẻ có phải là boilerplate xuất bản không.
     * Bao gồm: địa chỉ, số điện thoại, email, tên NXB, ISBN, tên biên tập...
     *
     * @param normalizedSentence câu đã normalize
     * @return true nếu là boilerplate
     */
    private boolean isBoilerplateSentence(String normalizedSentence) {
        if (normalizedSentence == null || normalizedSentence.isBlank())
            return true;
        if (normalizedSentence.matches(
                ".*(nha xuat ban|nxb|ban quyen|all rights reserved|copyright|isbn|issn|"
                        + "tru so chinh|chi nhanh|so dien thoai|dien thoai|tel|fax|email|website|http|www\\.|"
                        + "in lan|tai ban|xuat ban lan|lien he|hop dong|giay phep|gkxb|in tai|"
                        + "marketing@|info@|support@|sales@|publisher|"
                        + "giam doc|tong bien tap|bien tap|ve bia|trinh bay|sua ban in|chinh sua|"
                        + "chiu trach nhiem|ky duyet|nguoi ky|chu biet|chuc vu|"
                        + "dia chi|so nha|ngo |duong |phuong |quan |thanh pho|tp\\.|"
                        + "khu cong nghiep|kcn|tang |toa |co so|xuong sx|"
                        + "cong ty co phan|cong ty tnhh|joint stock|"
                        + "[0-9]{9,12}|[0-9]{2,4}[-.][0-9]{3,4}[-.][0-9]{3,4})"))
            return true;
        // Dòng chỉ toàn số và ký tự đặc biệt
        if (normalizedSentence.matches("^[\\d\\s\\p{Punct}]{1,20}$"))
            return true;
        // Tỉ lệ chữ quá thấp (nhiều số/ký hiệu)
        int letterCount = 0;
        for (char c : normalizedSentence.toCharArray())
            if (Character.isLetter(c))
                letterCount++;
        return normalizedSentence.length() > 8
                && (double) letterCount / normalizedSentence.length() < 0.40;
    }

    /**
     * Kiểm tra một đoạn văn bản có phải là block thông tin xuất bản không.
     * Dựa trên việc đếm số tín hiệu boilerplate; cần >= 2 để xác nhận.
     *
     * @param block đoạn văn bản cần kiểm tra
     * @return true nếu là publisher info block
     */
    private boolean isPublisherInfoBlock(String block) {
        if (block == null || block.isBlank())
            return false;
        String n = normalizeSearch(block);
        int signals = 0;
        if (n.contains("chiu trach nhiem") || n.contains("giam doc") || n.contains("tong bien tap"))
            signals++;
        if (n.contains("bien tap") || n.contains("ve bia") || n.contains("trinh bay") || n.contains("sua ban in"))
            signals++;
        if (n.contains("nha xuat ban") || n.contains("nxb") || n.contains("isbn") || n.contains("issn"))
            signals++;
        if (n.contains("dia chi") || n.contains("email") || n.contains("website") || n.contains("dien thoai"))
            signals++;
        if (n.contains("in lan") || n.contains("xuat ban lan") || n.contains("in tai") || n.contains("kcn"))
            signals++;
        if (n.contains("cong ty co phan") || n.contains("cong ty tnhh") || n.contains("truyen thong"))
            signals++;
        return signals >= 2;
    }

    // LOCAL FALLBACK ANSWER (khi Gemini không khả dụng)

    /**
     * Xây dựng câu trả lời local dựa trên dữ liệu DB, không cần Gemini.
     * Được gọi khi Gemini API hoàn toàn không khả dụng (429, 503, network...).
     *
     * @param userMessage câu hỏi của user
     * @param books       danh sách sách active
     * @return câu trả lời đã format, hoặc null nếu không xử lý được
     */
    private String buildSmartLocalAnswer(String userMessage, List<Book> books) {
        if (userMessage == null || userMessage.isBlank())
            return null;
        try {
            ChatIntent intent = detectIntent(userMessage, books);

            if (intent == ChatIntent.PRICE_SORT) {
                String n = normalizeSearch(userMessage);
                boolean desc = !n.matches(".*(re nhat|gia thap nhat|gia re nhat|thap nhat).*");
                List<Book> sorted = books.stream()
                        .filter(b -> b.getPrice() != null)
                        .sorted(desc ? Comparator.comparing(Book::getPrice).reversed()
                                : Comparator.comparing(Book::getPrice))
                        .limit(10).collect(Collectors.toList());
                if (sorted.isEmpty())
                    return "Hiện chưa có tác phẩm nào khả dụng để tra cứu giá.";
                StringBuilder sb = new StringBuilder();
                sb.append("Dưới đây là danh sách sách theo giá ")
                        .append(desc ? "(Cao → Thấp)" : "(Thấp → Cao)").append(":\n\n---\n\n## Kết quả\n");
                int i = 1;
                for (Book b : sorted)
                    sb.append(i++).append(". **").append(b.getTitle()).append("** — **")
                            .append(formatPrice(b.getPrice())).append("** | Kho: ").append(b.getQuantity())
                            .append("\n");
                return sb.toString().trim();
            }

            if (intent == ChatIntent.PRICE_RANGE) {
                long[] range = parsePriceRange(userMessage);
                long min = range[0], max = range[1];
                List<Book> filtered = books.stream()
                        .filter(b -> b.getPrice() != null)
                        .filter(b -> {
                            long price = b.getPrice().longValue();
                            return (min < 0 || price >= min) && (max < 0 || price <= max);
                        })
                        .sorted(Comparator.comparing(Book::getPrice))
                        .limit(20)
                        .collect(Collectors.toList());

                if (filtered.isEmpty()) {
                    String priceDesc = buildPriceRangeLabel(min, max).toLowerCase();
                    return "❌ Rất tiếc, không tìm thấy cuốn sách nào " + priceDesc
                            + ".\n\nHãy thử mức giá khác hoặc tìm kiếm bằng tên sách nhé!";
                }
                return formatPriceRangeAnswer(userMessage, filtered, min, max);
            }

            Book specific = findSpecificBookInfoMatch(books, userMessage);
            if (specific != null) {
                String pdfText = null;
                if (isContentQuestion(normalizeSearch(userMessage))
                        && specific.getPdfPath() != null
                        && pdfReaderService.isReadable(specific.getPdfPath())) {
                    pdfText = extractPdfCached(specific.getPdfPath());
                    if (pdfText != null && pdfText.startsWith("Không đọc được"))
                        pdfText = null;
                }
                return formatDetailedBookAnswer(specific, userMessage, pdfText, false);
            }

            if (intent == ChatIntent.CONTENT_QUESTION) {
                List<Book> candidates = findBooksForContentQuestion(books, userMessage);
                if (!candidates.isEmpty()) {
                    Book primary = candidates.get(0);
                    if (primary.getPdfPath() != null && pdfReaderService.isReadable(primary.getPdfPath())) {
                        String pdfText = extractPdfCached(primary.getPdfPath());
                        if (pdfText != null && !pdfText.startsWith("Không đọc được"))
                            return formatDetailedBookAnswer(primary, userMessage, pdfText, candidates.size() > 1);
                    }
                    String answer = formatDetailedBookAnswer(primary, userMessage, null, candidates.size() > 1);
                    boolean hasDesc = primary.getDescription() != null && !primary.getDescription().isBlank();
                    if (!hasDesc)
                        answer += "\n\n> *Tác phẩm chưa được cập nhật file đính kèm lẫn mô tả.*";
                    return answer;
                }
                return "Mình chưa tìm thấy phân đoạn tri thức phù hợp. Hãy chỉ định tên cuốn sách cụ thể nhé!";
            }

            Book single = findBestBookFromQuery(books, userMessage);
            if (single != null && isSpecificBookInfoQuestion(userMessage, books)) {
                String pdfText = null;
                if (single.getPdfPath() != null && pdfReaderService.isReadable(single.getPdfPath()))
                    pdfText = extractPdfCached(single.getPdfPath());
                return formatDetailedBookAnswer(single, userMessage, pdfText, false);
            }

            List<Book> matched = findRelevantBooks(books, userMessage, true);
            if (matched.isEmpty()) {
                String authorName = extractAuthorNameFromQuery(userMessage);
                if (!authorName.isBlank())
                    matched = findBooksByAuthor(books, authorName);
            }
            if (matched.isEmpty()) {
                String topic = String.join(" ", extractSearchKeywords(userMessage));
                String searchTag = topic.isBlank() ? ""
                        : buildActionButton("search", "books", null, topic.replace("\"", "'"),
                                "Tìm kiếm \"" + topic + "\"", "🔍") + "\n";
                return ("Mình chưa tìm thấy sách khớp với mô tả của bạn.\n\n"
                        + "Hãy thử tên cụ thể hơn, ví dụ: *Lập trình Java*, *Tâm lý học*.\n\n---\n"
                        + "**Bước tiếp theo:**\n"
                        + buildActionButton("navigate", "books", null, null, "Xem toàn bộ kệ sách", "") + "\n"
                        + searchTag).trim();
            }
            if (matched.size() == 1) {
                String pdfText = null;
                if (matched.get(0).getPdfPath() != null
                        && pdfReaderService.isReadable(matched.get(0).getPdfPath()))
                    pdfText = extractPdfCached(matched.get(0).getPdfPath());
                return formatDetailedBookAnswer(matched.get(0), userMessage, pdfText, false);
            }
            return formatBookListAnswer(userMessage, matched);

        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi buildSmartLocalAnswer: " + e.getMessage());
            return null;
        }
    }

    // FORMAT ANSWER (Local fallback templates)

    /**
     * Format câu trả lời chi tiết về một cuốn sách cụ thể.
     *
     * <p>
     * Có hai chế độ:
     * <ul>
     * <li>isPdfContentQuery=true: phân tích tri thức từ PDF (Luận điểm, Phương
     * pháp...)</li>
     * <li>isPdfContentQuery=false: thông tin metadata (giá, tác giả, mô tả,
     * kho)</li>
     * </ul>
     *
     * @param b           cuốn sách cần trình bày
     * @param userMessage câu hỏi gốc
     * @param pdfText     nội dung PDF đã extract (null nếu không có)
     * @param hasMore     có nhiều sách tương tự không (để gợi ý thêm)
     * @return chuỗi markdown đã format
     */
    private String formatDetailedBookAnswer(Book b, String userMessage,
            String pdfText, boolean hasMore) {
        boolean isPdfContentQuery = pdfText != null && !pdfText.isBlank()
                && isContentQuestion(normalizeSearch(userMessage));
        StringBuilder sb = new StringBuilder();

        if (isPdfContentQuery) {
            String cleanedPdf = filterBoilerplateFromPdf(pdfText);
            String sourceText = cleanedPdf.isBlank() ? pdfText : cleanedPdf;
            String chunks = retrieveTopChunks(sourceText, userMessage, TOP_K_CHUNKS);
            String snippet = chunks.isBlank()
                    ? safeSubstring(sourceText, 3_000)
                    : safeSubstring(chunks, 4_000);

            sb.append("Phản hồi này được phân tích và trích xuất dựa trên nội dung gốc của **")
                    .append(b.getTitle()).append("**.\n\n");
            sb.append("---\n\n## Luận điểm chính\n\n")
                    .append(extractMainThesisFromSnippet(snippet, userMessage)).append("\n\n");
            sb.append("---\n\n## Kiến thức kỹ thuật / Phương pháp luận\n\n")
                    .append(formatPdfSnippetAsBullets(snippet, 6)).append("\n");
            sb.append("---\n\n## Ứng dụng thực tế\n\n")
                    .append(extractPracticalApplicationFromSnippet(snippet)).append("\n\n");
            sb.append("---\n\n## Tóm tắt cốt lõi\n\n");
            for (String p : extractKeyPointsFromSnippet(snippet, 3))
                sb.append("* ").append(p).append("\n");
            sb.append("\n---\n\n> *Phân tích trích xuất hoàn toàn từ tài liệu đính kèm. "
                    + "Vui lòng đối chiếu với văn bản gốc.*\n");
            appendActionTriggersForBook(sb, b);
        } else {
            sb.append("Dưới đây là thông tin chi tiết về **").append(b.getTitle()).append("**:\n\n");
            sb.append("---\n\n## Thông tin chung\n\n");
            sb.append("* **Tên tác phẩm:** ").append(b.getTitle()).append("\n");
            sb.append("* **Tác giả:** ").append(b.getAuthor() != null ? b.getAuthor() : "Đang cập nhật").append("\n");
            sb.append("* **Danh mục:** ").append(b.getCategory() != null ? b.getCategory() : "—").append("\n\n");
            sb.append("---\n\n## Giá bán & Tình trạng kho\n\n");
            sb.append("* **Giá ưu đãi hiện tại:** **").append(formatPrice(b.getPrice())).append("**\n");
            sb.append("* **Tình trạng:** ")
                    .append(b.getQuantity() > 0
                            ? "Còn khả dụng **" + b.getQuantity() + "** cuốn"
                            : "**Tạm cháy hàng**")
                    .append("\n");
            if (b.getDescription() != null && !b.getDescription().isBlank())
                sb.append("\n---\n\n## Giới thiệu tổng quan\n\n").append(b.getDescription().trim()).append("\n");
            if (pdfText != null && !pdfText.isBlank()) {
                String chunks = retrieveTopChunks(pdfText, userMessage, 3);
                String snippet = chunks.isBlank() ? safeSubstring(pdfText, 2_000) : safeSubstring(chunks, 2_000);
                if (!snippet.isBlank())
                    sb.append("\n---\n\n## Tóm tắt nội dung PDF\n\n")
                            .append(formatPdfSnippetAsBullets(snippet, 5)).append("\n");
            }
            if (hasMore)
                sb.append("\n---\n\n## Gợi ý thêm\n\n"
                        + "Kho còn một số đầu sách tương tự, bạn muốn thu hẹp tìm kiếm không?\n");
            appendActionTriggersForBook(sb, b);
        }
        return sb.toString().trim();
    }

    /**
     * Format câu trả lời khi tìm thấy nhiều sách phù hợp (danh sách gợi ý).
     *
     * @param userMessage câu hỏi của user (để trích xuất topic hiển thị)
     * @param matched     danh sách sách phù hợp
     * @return chuỗi markdown đã format
     */
    private String formatBookListAnswer(String userMessage, List<Book> matched) {
        StringBuilder sb = new StringBuilder();
        String topic = String.join(" ", extractSearchKeywords(userMessage));
        if (topic.isBlank())
            topic = extractTopicFromQuestion(userMessage);

        sb.append("Mình đã tìm thấy **").append(matched.size()).append("** cuốn sách");
        if (!topic.isBlank())
            sb.append(" về **\"").append(topic).append("\"**");
        sb.append(" phù hợp với bạn:\n\n---\n\n## Danh mục đề xuất\n\n");

        int i = 1;
        for (Book b : matched.stream().limit(5).collect(Collectors.toList())) {
            sb.append(i++).append(". **").append(b.getTitle()).append("**\n");
            sb.append("   - **Tác giả:** ")
                    .append(b.getAuthor() != null ? b.getAuthor() : "Chưa cập nhật").append("\n");
            sb.append("   - **Chuyên mục:** ")
                    .append(b.getCategory() != null ? b.getCategory() : "—").append("\n");
            sb.append("   - **Giá bán:** **").append(formatPrice(b.getPrice())).append("**\n");
            if (b.getDescription() != null && !b.getDescription().isBlank()) {
                String desc = b.getDescription().trim();
                if (desc.length() > 120)
                    desc = desc.substring(0, 120) + "...";
                sb.append("   - **Đặc điểm:** ").append(desc).append("\n");
            }
            sb.append("\n");
        }
        appendActionTriggersForList(sb, userMessage, matched);
        return sb.toString().trim();
    }

    /**
     * Format câu trả lời cho kết quả tìm kiếm khoảng giá.
     *
     * @param userMessage câu hỏi gốc
     * @param books       danh sách sách trong khoảng giá
     * @param min         giá tối thiểu (-1 = không giới hạn)
     * @param max         giá tối đa (-1 = không giới hạn)
     * @return chuỗi markdown đã format
     */
    private String formatPriceRangeAnswer(String userMessage, List<Book> books, long min, long max) {
        StringBuilder sb = new StringBuilder();
        String priceLabel;
        if (min >= 0 && max >= 0)
            priceLabel = "từ **" + formatPrice(BigDecimal.valueOf(min))
                    + "** đến **" + formatPrice(BigDecimal.valueOf(max)) + "**";
        else if (min >= 0)
            priceLabel = "trên **" + formatPrice(BigDecimal.valueOf(min)) + "**";
        else if (max >= 0)
            priceLabel = "dưới **" + formatPrice(BigDecimal.valueOf(max)) + "**";
        else
            priceLabel = "tất cả mức giá";

        sb.append("✨ Mình tìm thấy **").append(books.size())
                .append("** cuốn sách có giá ").append(priceLabel).append(":\n\n");
        sb.append("---\n\n## Danh sách sách\n\n");

        int i = 1;
        for (Book b : books) {
            sb.append(i++).append(". **").append(b.getTitle()).append("**\n");
            sb.append("   - **Tác giả:** ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ").append("\n");
            sb.append("   - **Giá:** **").append(formatPrice(b.getPrice())).append("**\n");
            sb.append("   - **Tồn kho:** ")
                    .append(b.getQuantity() > 0 ? b.getQuantity() + " cuốn" : "Hết hàng")
                    .append("\n\n");
        }
        sb.append("---\n\n> 💡 Bạn muốn xem chi tiết cuốn nào? Hãy gõ tên sách hoặc nhấn nút bên dưới!\n");
        appendActionTriggersForPriceRange(sb, min, max, books);
        return sb.toString().trim();
    }

    /**
     * Xây dựng câu trả lời local cho tính năng hỏi đáp PDF khi Gemini không khả
     * dụng.
     *
     * @param question câu hỏi về nội dung PDF
     * @param pdfText  văn bản PDF đã extract
     * @param pdfPath  đường dẫn file PDF (để tìm sách liên kết)
     * @return chuỗi markdown đã format
     */
    private String buildPdfLocalAnswer(String question, String pdfText, String pdfPath) {
        String nq = normalizeSearch(question != null ? question : "");
        boolean wantSum = nq.matches(".*(tom tat|tong hop|summary|noi dung chinh|outline|muc luc).*");
        Book linked = findBookByPdfPath(pdfPath);
        String docTitle = (linked != null && linked.getTitle() != null) ? linked.getTitle() : "tài liệu đính kèm";

        String cleanedPdf = filterBoilerplateFromPdf(pdfText);
        String sourceText = cleanedPdf.isBlank() ? pdfText : cleanedPdf;
        int topK = wantSum ? 8 : TOP_K_CHUNKS;
        String chunks = retrieveTopChunks(sourceText, question, topK);
        String snippet = chunks.isBlank()
                ? safeSubstring(sourceText, 3_500)
                : safeSubstring(chunks, wantSum ? 6_000 : 4_000);
        if (snippet.length() < 200)
            snippet = extractMeaningfulSentences(sourceText, 5);

        StringBuilder sb = new StringBuilder();
        sb.append("Dưới đây là phân tích nội dung từ **").append(docTitle).append("**:\n\n");
        sb.append("---\n\n## Luận điểm chính\n\n")
                .append(extractMainThesisFromSnippet(snippet, question)).append("\n\n");
        sb.append("---\n\n## Nội dung trích yếu\n\n")
                .append(formatPdfSnippetAsBullets(snippet, 6)).append("\n");
        sb.append("---\n\n## Ứng dụng thực tế\n\n")
                .append(extractPracticalApplicationFromSnippet(snippet)).append("\n\n");
        sb.append("---\n\n> ℹ️ *Tóm tắt dựa trên nội dung tài liệu đính kèm. "
                + "Đối chiếu văn bản gốc cho các quyết định quan trọng.*\n");
        return sb.toString().trim();
    }

    // PDF SNIPPET FORMATTERS

    /**
     * Format đoạn trích PDF thành danh sách bullet points.
     * Mỗi câu thành một bullet, loại bỏ câu quá ngắn và boilerplate.
     *
     * @param snippet  đoạn văn bản cần format
     * @param maxItems số bullet point tối đa
     * @return chuỗi markdown bullets
     */
    private String formatPdfSnippetAsBullets(String snippet, int maxItems) {
        if (snippet == null || snippet.isBlank())
            return "* Không có dữ liệu để phân tích.\n";
        String cleaned = snippet.replaceAll("={3,}", "").replaceAll("\\[.*?\\]", "").trim();
        String[] sentences = cleaned.split("(?<=[.!?])\\s+");
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (String sentence : sentences) {
            String t = sentence.trim();
            if (t.length() < 30)
                continue;
            if (t.matches(".*[-–—]{2,}.*"))
                continue;
            if (isBoilerplateSentence(normalizeSearch(t)))
                continue;
            if (t.length() > 280)
                t = t.substring(0, 277) + "…";
            out.append("* ").append(t).append("\n");
            if (++n >= maxItems)
                break;
        }
        if (n == 0) {
            String fallback = cleaned.replaceAll("\n", " ").replaceAll("\\s+", " ");
            out.append("* ").append(safeSubstring(fallback, 400)).append("\n");
        }
        return out.toString();
    }

    /**
     * Trích xuất câu luận điểm chính từ đoạn snippet.
     * Tìm câu đầu tiên có độ dài 50-500 ký tự và không phải boilerplate.
     *
     * @param snippet  đoạn văn bản
     * @param question câu hỏi của user (chưa dùng, để sau tích hợp matching)
     * @return câu luận điểm chính
     */
    private String extractMainThesisFromSnippet(String snippet, String question) {
        if (snippet == null || snippet.isBlank())
            return "Tài liệu trình bày các khái niệm và phương pháp liên quan đến chủ đề bạn hỏi.";
        for (String s : snippet.split("(?<=[.!?])\\s+")) {
            String t = s.trim();
            if (t.length() >= 50 && t.length() <= 500 && !isBoilerplateSentence(normalizeSearch(t)))
                return t;
        }
        return safeSubstring(snippet.replaceAll("\\s+", " ").trim(), 300);
    }

    /**
     * Trích xuất câu mô tả ứng dụng thực tế từ đoạn snippet.
     * Ưu tiên câu chứa từ khóa "áp dụng", "quy trình", "cải thiện"...
     *
     * @param snippet đoạn văn bản
     * @return câu ứng dụng thực tế
     */
    private String extractPracticalApplicationFromSnippet(String snippet) {
        if (snippet == null || snippet.isBlank())
            return "Kiến thức trong tài liệu có thể áp dụng trực tiếp vào thực tế.";
        String[] sentences = snippet.split("(?<=[.!?])\\s+");
        for (String s : sentences) {
            String norm = normalizeSearch(s);
            if (norm.matches(
                    ".*(ap dung|thuc te|su dung|trien khai|can lam|nen lam|cach|buoc|"
                            + "quy trinh|ket qua|mang lai|giup|tang|giam|cai thien).*")
                    && s.trim().length() >= 40)
                return s.trim();
        }
        String last = sentences[sentences.length - 1].trim();
        return last.length() >= 30 ? last
                : "Tài liệu cung cấp các hướng dẫn thực tế có thể triển khai ngay.";
    }

    /**
     * Trích xuất N câu điểm chính từ snippet để hiển thị dạng tóm tắt.
     *
     * @param snippet đoạn văn bản
     * @param count   số câu cần trích xuất
     * @return mảng các câu điểm chính (luôn đủ count phần tử)
     */
    private String[] extractKeyPointsFromSnippet(String snippet, int count) {
        String[] fallback = {
                "Tài liệu cung cấp các khái niệm nền tảng cần nắm vững.",
                "Các phương pháp được đề xuất có tính ứng dụng cao.",
                "Cần đối chiếu với văn bản gốc để có thông tin đầy đủ và chính xác."
        };
        if (snippet == null || snippet.isBlank())
            return fallback;
        String[] sentences = snippet.split("(?<=[.!?])\\s+");
        List<String> points = new ArrayList<>();
        for (String s : sentences) {
            String t = s.trim();
            if (t.length() >= 40 && t.length() <= 200) {
                points.add(t);
                if (points.size() >= count)
                    break;
            }
        }
        while (points.size() < count)
            points.add(points.size() < fallback.length ? fallback[points.size()]
                    : "Điểm cốt lõi từ tài liệu.");
        return points.toArray(new String[0]);
    }

    /**
     * Trích xuất N câu có nghĩa (không phải boilerplate, đủ dài) từ văn bản.
     * Dùng khi snippet quá ngắn để đảm bảo có đủ nội dung hiển thị.
     *
     * @param text     văn bản nguồn
     * @param maxItems số câu tối đa
     * @return chuỗi các câu ghép lại
     */
    private String extractMeaningfulSentences(String text, int maxItems) {
        if (text == null || text.isBlank())
            return "";
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (String s : sentences) {
            String t = s.trim();
            if (t.length() >= 40 && !isBoilerplateSentence(normalizeSearch(t))) {
                out.append(t).append(" ");
                if (++count >= maxItems)
                    break;
            }
        }
        return out.toString().trim();
    }

    // ACTION TRIGGERS (UI Buttons)

    /**
     * Kiểm tra text đã có ActionTrigger buttons chưa để tránh thêm trùng lặp.
     *
     * @param text chuỗi cần kiểm tra
     * @return true nếu đã có ActionTrigger
     */
    private boolean hasActionTriggers(String text) {
        if (text == null)
            return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("<actiontrigger") || lower.contains("class=\"action-trigger\"");
    }

    /**
     * Bổ sung ActionTrigger buttons vào cuối phản hồi AI.
     * Tự động phát hiện intent + sách liên quan và thêm buttons phù hợp.
     * Không thêm nếu đã có buttons rồi.
     *
     * @param aiText      phản hồi của AI
     * @param userMessage câu hỏi gốc của user
     * @param books       danh sách sách để tìm sách liên quan
     * @return aiText đã được enrich với buttons
     */
    private String enrichWithActionTriggers(String aiText, String userMessage, List<Book> books) {
        if (aiText == null || aiText.isBlank() || hasActionTriggers(aiText))
            return aiText;

        ChatIntent intent = detectIntent(userMessage, books);

        // Xử lý theo intent trước để gợi ý chính xác hơn
        switch (intent) {
            case PRICE_SORT -> {
                StringBuilder sb = new StringBuilder(aiText);
                appendActionTriggersForPriceSort(sb, userMessage, books);
                return sb.toString().trim();
            }
            case PRICE_RANGE -> {
                StringBuilder sb = new StringBuilder(aiText);
                long[] range = parsePriceRange(userMessage);
                appendActionTriggersForPriceRange(sb, range[0], range[1], books);
                return sb.toString().trim();
            }
            case ORDER -> {
                StringBuilder sb = new StringBuilder(aiText);
                appendActionTriggersForOrder(sb);
                return sb.toString().trim();
            }
            default -> {
                // Fallback: tìm sách liên quan rồi gán button phù hợp
                Book specific = findBestBookFromQuery(books, userMessage);
                if (specific == null)
                    specific = findSpecificBookInfoMatch(books, userMessage);
                if (specific != null) {
                    StringBuilder sb = new StringBuilder(aiText);
                    appendActionTriggersForBook(sb, specific, intent);
                    return sb.toString().trim();
                }
                List<Book> matched = findRelevantBooks(books, userMessage, true);
                if (!matched.isEmpty()) {
                    StringBuilder sb = new StringBuilder(aiText);
                    appendActionTriggersForList(sb, userMessage, matched);
                    return sb.toString().trim();
                }
                // Không tìm thấy sách nào → gợi ý điều hướng chung
                StringBuilder sb = new StringBuilder(aiText);
                appendActionTriggersGeneral(sb);
                return sb.toString().trim();
            }
        }
    }

    /**
     * Thêm tối đa 3 ActionTrigger buttons cho một cuốn sách cụ thể.
     * Thứ tự ưu tiên: Đặt mua → Xem chi tiết → Tác giả/Thể loại → So sánh giá.
     * Nếu hết hàng: bỏ nút mua, thêm gợi ý sách tương tự.
     *
     * @param sb     StringBuilder để append buttons
     * @param b      cuốn sách cần tạo buttons
     * @param intent intent hiện tại để tùy chỉnh thứ tự gợi ý
     */
    private void appendActionTriggersForBook(StringBuilder sb, Book b, ChatIntent intent) {
        if (b == null || b.getId() == null)
            return;
        String bookId = String.valueOf(b.getId());

        // Xây pool ưu tiên, chọn tối đa 3 button phù hợp nhất với ngữ cảnh
        List<String> pool = new ArrayList<>();

        // Ưu tiên 1 — Hành động mua hàng (chỉ khi còn hàng)
        if (b.getQuantity() > 0)
            pool.add(buildActionButton("order", "cart", bookId, null, "Đặt mua ngay", "🛒"));

        // Ưu tiên 2 — Xem chi tiết (luôn có, nhưng đứng sau mua hàng)
        pool.add(buildActionButton("view-detail", "book-detail", bookId, null, "Xem chi tiết", "✅"));

        // Ưu tiên 3 — Tác giả (nếu intent là catalog/content thì đưa lên sớm)
        if (b.getAuthor() != null && !b.getAuthor().isBlank()) {
            String btn = buildActionButton("search", "books", null, b.getAuthor(), "Sách cùng tác giả", "✍️");
            if (intent == ChatIntent.CATALOG_SEARCH || intent == ChatIntent.CONTENT_QUESTION)
                pool.add(1, btn); // đẩy lên vị trí 2
            else
                pool.add(btn);
        }

        // Ưu tiên 4 — Thể loại (nếu chưa đủ 3 và có dữ liệu)
        if (b.getCategory() != null && !b.getCategory().isBlank())
            pool.add(buildActionButton("search", "books", null, b.getCategory(), "Sách cùng thể loại", "📂"));

        // Ưu tiên 5 — So sánh giá (chỉ thêm nếu intent liên quan đến giá)
        if (intent == ChatIntent.PRICE_INFO || intent == ChatIntent.PRICE_SORT
                || intent == ChatIntent.PRICE_RANGE)
            pool.add(buildActionButton("price-sort", "books", null, "asc", "So sánh giá toàn kệ", "💰"));

        // Ưu tiên 6 — Hết hàng thì gợi ý xem sách tương tự
        if (b.getQuantity() <= 0)
            pool.add(buildActionButton("navigate", "books", null, null, "Xem sách tương tự", "📚"));

        appendButtonPool(sb, pool, 3);
    }

    /**
     * Overload không cần intent — dùng cho các nơi gọi cũ
     * (formatDetailedBookAnswer...).
     *
     * @param sb StringBuilder để append buttons
     * @param b  cuốn sách cần tạo buttons
     */
    private void appendActionTriggersForBook(StringBuilder sb, Book b) {
        appendActionTriggersForBook(sb, b, ChatIntent.GENERAL);
    }

    /**
     * Thêm ActionTrigger buttons cho danh sách nhiều sách.
     * <p>
     * Gợi ý bổ sung so với phiên bản cũ:
     * <ul>
     * <li>Sách rẻ nhất / đắt nhất</li>
     * <li>Lọc theo khoảng giá</li>
     * <li>Gợi ý sách hay</li>
     * <li>Xem chi tiết 3 sách đầu (thay vì chỉ 1)</li>
     * </ul>
     *
     * @param sb          StringBuilder để append buttons
     * @param userMessage câu hỏi gốc (để trích xuất topic search)
     * @param matched     danh sách sách phù hợp
     */
    private void appendActionTriggersForList(StringBuilder sb,
            String userMessage, List<Book> matched) {
        if (matched == null || matched.isEmpty())
            return;
        if (matched.size() == 1) {
            appendActionTriggersForBook(sb, matched.get(0));
            return;
        }

        List<String> pool = new ArrayList<>();

        // Ưu tiên 1 — Chi tiết sách khớp nhất
        Book top = matched.get(0);
        if (top != null && top.getId() != null) {
            String shortTitle = top.getTitle().length() > 20
                    ? top.getTitle().substring(0, 17) + "..."
                    : top.getTitle();
            pool.add(buildActionButton("view-detail", "book-detail",
                    String.valueOf(top.getId()), null, "📖 " + shortTitle, ""));
        }

        // Ưu tiên 2 — Tìm kiếm theo topic nếu có
        String topic = String.join(" ", extractSearchKeywords(userMessage));
        if (!topic.isBlank()) {
            String safeTopic = topic.length() > 25 ? topic.substring(0, 25) : topic;
            pool.add(buildActionButton("search", "books", null, safeTopic,
                    "Tìm \"" + safeTopic + "\"", "🔍"));
        }

        // Ưu tiên 3 — Nếu có >= 2 sách: chi tiết sách thứ 2, nếu không thì xem toàn bộ
        if (matched.size() >= 2 && matched.get(1) != null && matched.get(1).getId() != null) {
            String shortTitle2 = matched.get(1).getTitle().length() > 20
                    ? matched.get(1).getTitle().substring(0, 17) + "..."
                    : matched.get(1).getTitle();
            pool.add(buildActionButton("view-detail", "book-detail",
                    String.valueOf(matched.get(1).getId()), null, "📖 " + shortTitle2, ""));
        }

        // Fallback — nếu chưa đủ 3
        pool.add(buildActionButton("navigate", "books", null, null, "Xem toàn bộ sách", "📚"));
        pool.add(buildActionButton("price-sort", "books", null, "asc", "Sách giá rẻ nhất", "💸"));

        appendButtonPool(sb, pool, 3);
    }

    /**
     * Gợi ý sau kết quả sắp xếp theo giá.
     * Cho phép đổi chiều sort, lọc khoảng giá, xem theo thể loại.
     *
     * @param sb          StringBuilder để append
     * @param userMessage câu hỏi gốc (để xác định chiều sort hiện tại)
     * @param books       danh sách sách (lấy sách đầu/cuối gợi ý)
     */
    private void appendActionTriggersForPriceSort(StringBuilder sb,
            String userMessage, List<Book> books) {
        String n = normalizeSearch(userMessage);
        boolean currentlyDesc = !n.matches(".*(re nhat|gia thap nhat|gia re nhat|thap nhat).*");

        List<String> pool = new ArrayList<>();

        // Ưu tiên 1 — Đổi chiều sort (hành động liên quan nhất)
        if (currentlyDesc)
            pool.add(buildActionButton("price-sort", "books", null, "asc", "Xem giá thấp → cao", "📈"));
        else
            pool.add(buildActionButton("price-sort", "books", null, "desc", "Xem giá cao → thấp", "📉"));

        // Ưu tiên 2 — Xem chi tiết sách đầu danh sách (phù hợp nhất với sort hiện tại)
        if (books != null && !books.isEmpty()) {
            List<Book> sorted = books.stream()
                    .filter(b -> b.getPrice() != null)
                    .sorted(currentlyDesc
                            ? Comparator.comparing(Book::getPrice).reversed()
                            : Comparator.comparing(Book::getPrice))
                    .limit(1).collect(Collectors.toList());
            if (!sorted.isEmpty() && sorted.get(0).getId() != null) {
                String shortTitle = sorted.get(0).getTitle().length() > 20
                        ? sorted.get(0).getTitle().substring(0, 17) + "..."
                        : sorted.get(0).getTitle();
                pool.add(buildActionButton("view-detail", "book-detail",
                        String.valueOf(sorted.get(0).getId()), null, "📖 " + shortTitle, ""));
            }
        }

        // Ưu tiên 3 — Lọc khoảng giá phổ biến (lọc thấp nếu đang xem giá cao, và ngược
        // lại)
        if (currentlyDesc)
            pool.add(buildActionButton("price-range", "books", null, "lt100", "Dưới 100.000đ", "💰"));
        else
            pool.add(buildActionButton("price-range", "books", null, "gt200", "Trên 200.000đ", "💎"));

        // Fallback
        pool.add(buildActionButton("navigate", "books", null, null, "Xem toàn bộ kệ sách", "📚"));

        appendButtonPool(sb, pool, 3);
    }

    /**
     * Gợi ý sau kết quả lọc theo khoảng giá.
     * Cho phép mở rộng/thu hẹp khoảng giá và xem sách liền kề.
     *
     * @param sb    StringBuilder để append
     * @param min   giá tối thiểu hiện tại (-1 = không giới hạn)
     * @param max   giá tối đa hiện tại (-1 = không giới hạn)
     * @param books danh sách sách để lấy gợi ý đầu tiên
     */
    private void appendActionTriggersForPriceRange(StringBuilder sb,
            long min, long max, List<Book> books) {
        List<String> pool = new ArrayList<>();

        // Ưu tiên 1 — Chi tiết sách đầu tiên trong kết quả (hành động trực tiếp nhất)
        if (books != null && !books.isEmpty()) {
            Book first = books.get(0);
            if (first != null && first.getId() != null) {
                String shortTitle = first.getTitle().length() > 20
                        ? first.getTitle().substring(0, 17) + "..."
                        : first.getTitle();
                pool.add(buildActionButton("view-detail", "book-detail",
                        String.valueOf(first.getId()), null, "📖 " + shortTitle, ""));
            }
        }

        // Ưu tiên 2 — Mở rộng khoảng giá lên (nếu có giới hạn trên)
        if (max > 0) {
            long higher = (long) (max * 1.5);
            pool.add(buildActionButton("price-range", "books", null, "lt" + higher,
                    "Mở rộng đến " + formatPrice(BigDecimal.valueOf(higher)), "⬆️"));
        }

        // Ưu tiên 3 — Tìm rẻ hơn (nếu có giới hạn dưới) hoặc lọc cố định ngược chiều
        if (min > 0) {
            long lower = (long) (min * 0.6);
            pool.add(buildActionButton("price-range", "books", null, "lt" + lower,
                    "Tìm rẻ hơn (dưới " + formatPrice(BigDecimal.valueOf(lower)) + ")", "💸"));
        } else {
            // Không có min → gợi ý khoảng cao hơn
            pool.add(buildActionButton("price-range", "books", null, "gt200", "Trên 200.000đ", "💎"));
        }

        // Fallback — nếu chưa đủ 3
        pool.add(buildActionButton("price-sort", "books", null, "asc", "Xem toàn bộ giá thấp → cao", "📊"));
        pool.add(buildActionButton("navigate", "books", null, null, "Xem toàn bộ sách", "📚"));
        appendButtonPool(sb, pool, 3);
    }

    /**
     * Gợi ý sau trả lời về đơn hàng / vận chuyển.
     * Cho phép xem đơn hàng, tra cứu vận chuyển, tiếp tục mua sắm.
     *
     * @param sb StringBuilder để append
     */
    private void appendActionTriggersForOrder(StringBuilder sb) {
        List<String> pool = List.of(
                buildActionButton("navigate", "orders", null, null, "Lịch sử đơn hàng", "📦"),
                buildActionButton("navigate", "orders", null, "tracking", "Theo dõi vận chuyển", "🚚"),
                buildActionButton("navigate", "books", null, null, "Tiếp tục mua sắm", "🛍️"));
        appendButtonPool(sb, pool, 3);
    }

    private void appendActionTriggersGeneral(StringBuilder sb) {
        List<String> pool = List.of(
                buildActionButton("navigate", "books", null, null, "Xem toàn bộ kệ sách", "📚"),
                buildActionButton("price-sort", "books", null, "asc", "Sách giá rẻ nhất", "💸"),
                buildActionButton("navigate", "books", null, "recommend", "Sách được yêu thích", "⭐"));
        appendButtonPool(sb, pool, 3);
    }

    /**
     * Helper: lấy tối đa {@code max} button từ pool và append vào StringBuilder.
     * Mỗi button cách nhau bằng khoảng trắng, không xuống dòng giữa chừng.
     *
     * @param sb   StringBuilder đích
     * @param pool danh sách button HTML đã build sẵn (theo thứ tự ưu tiên)
     * @param max  số button tối đa hiển thị
     */
    private void appendButtonPool(StringBuilder sb, List<String> pool, int max) {
        if (pool == null || pool.isEmpty())
            return;
        sb.append("\n\n");
        pool.stream().limit(max).forEach(btn -> sb.append(btn).append(" "));
        sb.append("\n");
    }

    /**
     * Tạo HTML tag ActionTrigger để frontend render thành button.
     *
     * @param type   loại action (view-detail, order, search, navigate)
     * @param target đích đến (book-detail, cart, books...)
     * @param id     ID sách (null nếu không cần)
     * @param query  từ khóa tìm kiếm (null nếu không cần)
     * @param label  text hiển thị trên button
     * @param icon   emoji icon (null hoặc rỗng nếu không có)
     * @return chuỗi HTML ActionTrigger
     */
    private String buildActionButton(String type, String target, String id,
            String query, String label, String icon) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ActionTrigger type=\"").append(escapeHtml(type)).append("\"")
                .append(" target=\"").append(escapeHtml(target)).append("\"");
        if (id != null && !id.isBlank())
            sb.append(" id=\"").append(escapeHtml(id)).append("\"");
        if (query != null && !query.isBlank())
            sb.append(" query=\"").append(escapeHtml(query)).append("\"");
        sb.append(">");
        if (icon != null && !icon.isBlank())
            sb.append(icon).append(" ");
        sb.append(escapeHtmlContent(label)).append("</ActionTrigger>");
        return sb.toString();
    }

    // PDF UTILITIES

    /**
     * Đọc nội dung PDF với cache in-memory để tránh đọc lại file từ disk
     * trong cùng một JVM lifecycle.
     *
     * @param pdfPath đường dẫn file PDF
     * @return nội dung text của PDF
     */
    private String extractPdfCached(String pdfPath) {
        return pdfCache.computeIfAbsent(pdfPath, pdfReaderService::extractText);
    }

    /**
     * Tìm sách trong DB theo đường dẫn file PDF.
     * Hỗ trợ so khớp bằng exact path, suffix path, hoặc chỉ tên file.
     *
     * @param pdfPath đường dẫn file PDF cần tìm sách tương ứng
     * @return sách tương ứng hoặc null nếu không tìm thấy
     */
    private Book findBookByPdfPath(String pdfPath) {
        if (pdfPath == null || pdfPath.isBlank())
            return null;
        String norm = pdfPath.replace('\\', '/');
        String fileName = norm.contains("/") ? norm.substring(norm.lastIndexOf('/') + 1) : norm;
        return bookRepo.findByStatus(Book.BookStatus.ACTIVE).stream()
                .filter(b -> {
                    if (b.getPdfPath() == null)
                        return false;
                    String bp = b.getPdfPath().replace('\\', '/');
                    return bp.equals(norm) || bp.endsWith("/" + fileName) || bp.endsWith(fileName);
                })
                .findFirst().orElse(null);
    }

    /**
     * Kiểm tra kết nối và khả năng đọc PDF, trả về thông tin chi tiết.
     *
     * @param pdfPath đường dẫn file PDF
     * @param book    sách liên kết (null nếu không có)
     * @return Map kết quả kết nối
     */
    private Map<String, Object> buildPdfConnectionResult(String pdfPath, Book book) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pdfPathInDb", pdfPath);
        if (book != null) {
            result.put("bookId", book.getId());
            result.put("bookTitle", book.getTitle());
            result.put("pdfName", book.getPdfName());
        }
        if (pdfPath == null || pdfPath.isBlank()) {
            result.put("connected", false);
            result.put("message", "Chưa cấu hình đường dẫn pdf_path trong DB.");
            return result;
        }
        var resolved = pdfReaderService.resolvePdfPath(pdfPath);
        if (resolved == null) {
            result.put("connected", false);
            result.put("message", "Tệp PDF không tồn tại trên vùng lưu trữ của máy chủ.");
            return result;
        }
        result.put("connected", true);
        result.put("resolvedPath", resolved.toString());
        String preview = extractPdfCached(pdfPath);
        boolean ok = preview != null && !preview.startsWith("Không đọc được");
        result.put("readable", ok);
        result.put("previewLength", ok ? preview.length() : 0);
        result.put("message", ok ? "✅ Kết nối thành công. Đã đọc thông suốt file PDF." : preview);
        if (book == null) {
            Book found = findBookByPdfPath(pdfPath);
            if (found != null) {
                result.put("bookId", found.getId());
                result.put("bookTitle", found.getTitle());
            }
        }
        return result;
    }

    // BOOK LINE FORMATTERS

    /**
     * Format thông tin sách thành một dòng bullet point ngắn gọn,
     * dùng trong danh sách kết quả tìm kiếm.
     *
     * @param sb StringBuilder để append
     * @param b  sách cần format
     */
    private void appendBookLine(StringBuilder sb, Book b) {
        sb.append("* \"").append(b.getTitle()).append("\"")
                .append(" | ID: ").append(b.getId())
                .append(" | Tác giả: ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ")
                .append(" | Phân loại: ").append(b.getCategory() != null ? b.getCategory() : "—")
                .append(" | Giá: ").append(formatPrice(b.getPrice()))
                .append(" | Kho: ").append(b.getQuantity()).append(" cuốn")
                .append(b.getPdfPath() != null && !b.getPdfPath().isBlank() ? " | 📄 Có PDF" : "")
                .append("\n");
    }

    /**
     * Format thông tin chi tiết sách thành block nhiều dòng,
     * dùng trong context payload gửi cho AI.
     *
     * @param sb StringBuilder để append
     * @param b  sách cần format
     */
    private void appendBookDetail(StringBuilder sb, Book b) {
        sb.append("ID: ").append(b.getId()).append("\n")
                .append("Tên: \"").append(b.getTitle()).append("\"\n")
                .append("Tác giả: ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ").append("\n")
                .append("Thể loại: ").append(b.getCategory() != null ? b.getCategory() : "—").append("\n")
                .append("Giá: ").append(formatPrice(b.getPrice())).append("\n")
                .append("Kho: ").append(b.getQuantity()).append(" cuốn\n");
        if (b.getDescription() != null && !b.getDescription().isBlank())
            sb.append("Mô tả: ").append(b.getDescription().trim()).append("\n");
        if (b.getPdfPath() != null && !b.getPdfPath().isBlank())
            sb.append("📄 Có file PDF đọc thử.\n");
    }

    // UTILITIES
    /**
     * Lưu một message vào DB, tự động handle null values an toàn.
     *
     * @param username  tên người dùng (null → "anonymous")
     * @param role      vai trò (user/model) (null → "system")
     * @param message   nội dung tin nhắn (null → "")
     * @param sessionId ID phiên chat (null → UUID mới)
     */
    private void saveMessage(String username, String role, String message, String sessionId) {
        ChatMessage msg = new ChatMessage();
        msg.setUsername(username != null ? username : "anonymous");
        msg.setRole(role != null ? role : "system");
        msg.setMessage(message != null ? message : "");
        msg.setSessionId(sessionId != null ? sessionId : UUID.randomUUID().toString());
        msg.setCreateDate(LocalDateTime.now());
        chatRepo.save(msg);
    }

    /**
     * Trả về sessionId hiện tại hoặc tạo mới nếu null/blank.
     *
     * @param sessionId sessionId từ request
     * @return sessionId hợp lệ
     */
    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;
    }

    /**
     * Chuẩn hóa chuỗi tiếng Việt: loại dấu (NFD decompose + remove marks),
     * chuyển thành chữ thường, gộp khoảng trắng thừa.
     *
     * @param text chuỗi cần chuẩn hóa
     * @return chuỗi đã chuẩn hóa (lowercase, không dấu)
     */
    private String normalizeSearch(String text) {
        if (text == null)
            return "";
        String n = Normalizer.normalize(text, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ").trim();
    }

    /**
     * Format số tiền theo chuẩn Việt Nam: 150.000đ.
     * Dùng NumberFormat locale vi_VN để thêm dấu chấm ngàn tự động.
     *
     * @param price giá tiền (BigDecimal)
     * @return chuỗi giá đã format, "Chưa có giá" nếu null
     */
    private String formatPrice(BigDecimal price) {
        if (price == null)
            return "Chưa có giá";
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(price) + "đ";
    }

    /**
     * Cắt chuỗi an toàn với dấu "..." nếu vượt maxLen.
     *
     * @param text   chuỗi đầu vào
     * @param maxLen độ dài tối đa
     * @return chuỗi đã cắt (hoặc nguyên vẹn nếu đủ ngắn)
     */
    private String safeSubstring(String text, int maxLen) {
        if (text == null)
            return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /**
     * Rút gọn system instruction nếu vượt quá ngưỡng maxSystemChars,
     * giữ lại phần đầu và thêm thông báo rút gọn.
     *
     * @param instruction system instruction đầy đủ
     * @return instruction đã rút gọn (hoặc nguyên vẹn nếu đủ ngắn)
     */
    private String trimSystemInstruction(String instruction) {
        if (instruction == null)
            return "";
        if (instruction.length() <= maxSystemChars)
            return instruction;
        return instruction.substring(0, maxSystemChars - 60) + "\n\n[Context rút gọn do giới hạn kỹ thuật.]";
    }

    /**
     * Chuyển đổi exception thành thông báo lỗi thân thiện với người dùng.
     *
     * @param e exception cần chuyển đổi
     * @return chuỗi thông báo lỗi tiếng Việt
     */
    private String toUserFriendlyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota"))
            return "⏳ Hệ thống AI tạm thời đạt ngưỡng giới hạn truy cập. Bạn có thể thử lại sau ít phút hoặc tra cứu thủ công nhé!";
        if (msg.contains("API key") || msg.contains("401") || msg.contains("403"))
            return "🔑 Sự cố xác thực: Khóa API Gemini không chính xác hoặc hết hạn.";
        return "⚠️ Bộ máy xử lý gặp xung đột kỹ thuật nhỏ. Vui lòng thử lại sau giây lát!";
    }

    /**
     * Xây dựng thông báo lỗi stream với sessionId để frontend xử lý đúng.
     * 
     * @param e exception từ stream
     * @return chuỗi thông báo lỗi thân thiện
     */
    private String buildStreamErrorMessage(Throwable e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED"))
            return "⏳ AI đang quá tải, vui lòng thử lại sau vài giây.";
        if (msg.contains("404") || msg.contains("NOT_FOUND"))
            return "⚠️ Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại.";
        return "⚠️ Không thể kết nối AI lúc này. Vui lòng thử lại.";
    }

    // HTML ESCAPE UTILITIES

    /**
     * Escape các ký tự đặc biệt HTML dùng trong attribute value.
     * Xử lý: &amp; &lt; &gt; &quot;
     * 
     * @param text chuỗi cần escape
     * @return chuỗi đã escape
     */
    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * Escape các ký tự đặc biệt HTML dùng trong text content (không cần &quot;).
     *
     * @param text chuỗi cần escape
     * @return chuỗi đã escape
     */
    private String escapeHtmlContent(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Escape ký tự đặc biệt trong JSON string (cho error messages).
     *
     * @param text chuỗi cần escape
     * @return chuỗi đã escape JSON-safe
     */
    private String escapeJson(String text) {
        if (text == null)
            return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}