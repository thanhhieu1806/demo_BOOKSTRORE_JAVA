package com.example.dem_login.service;

import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import java.time.LocalDateTime;
import com.example.dem_login.dto.Dto;
import com.example.dem_login.model.Book;
import com.example.dem_login.model.ChatMessage;
import com.example.dem_login.model.Order;
import com.example.dem_login.repository.BookRepository;
import com.example.dem_login.repository.ChatMessageRepository;
import com.example.dem_login.repository.OrderRepository;
import java.text.NumberFormat;
import java.text.Normalizer;
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
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    // Repositories ─
    private final ChatMessageRepository chatRepo;
    private final BookRepository bookRepo;
    private final OrderRepository orderRepo;
    private final WebClient webClient;
    private final PdfReaderService pdfReaderService;
    private final ObjectMapper mapper = new ObjectMapper();

    // Config từ application.properties
    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    @Value("${gemini.fallback-models:gemini-1.5-flash,gemini-1.5-flash-8b}")
    private String fallbackModels;

    @Value("${gemini.max-history-messages:10}")
    private int maxHistoryMessages;

    @Value("${gemini.max-books-in-prompt:20}")
    private int maxBooksInPrompt;

    @Value("${gemini.max-system-chars:20000}")
    private int maxSystemChars;

    // Hằng số API
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Hằng số RAG Chunking
    /** Kích thước mỗi chunk (ký tự). read.md khuyến nghị 500-1000. */
    private static final int CHUNK_SIZE = 800;
    /** Độ gối chồng giữa các chunk (~18%). read.md khuyến nghị 10-20%. */
    private static final int CHUNK_OVERLAP = 150;
    /** Số chunk trả về sau retrieval (Top-K). */
    private static final int TOP_K_CHUNKS = 5;
    /** Nhiệt độ Gemini — thấp để tránh hallucination. read.md: 0.1-0.2. */
    private static final double TEMPERATURE = 0.1;

    // Stop words tiếng Việt
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

    // System Prompts

    /**
     * BASE_INSTRUCTION — Vai trò và quy tắc định dạng cho BookBot.
     * FIX: Đã xóa dòng blockquote bị lặp đôi ở phiên bản cũ.
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

            // Ví dụ format: xem few-shot trong contents đầu tiên.

            ═══ QUY TẮC NỘI DUNG ═══
            1. CHỈ dùng dữ liệu THỰC TẾ từ <internal_context>. KHÔNG bịa đặt hay suy diễn ngoài ngữ cảnh.
            2. Tiền tệ chuẩn Việt Nam: 150.000đ (không dùng "150000 VND" hay "VNĐ").
            3. Nếu không tìm thấy sách → gợi ý từ khóa khác + hiện nút điều hướng.
            4. Tuyệt đối KHÔNG trả lời dưới dạng đoạn văn thuần túy khi có dữ liệu cấu trúc.
            """;

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
            → Nếu <internal_context> **CHỈ** chứa các thông tin trên, hãy phản hồi thẳng:
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
              * [Chi tiết con tiếp theo]
            * **[Điểm quan trọng 3]:** [Giải thích]

            ---

            [Lặp lại cho các phần tiếp theo]

            ## Bản tóm tắt thuật ngữ cốt lõi *(nếu tài liệu có)*
            * **[Thuật ngữ]:** [Định nghĩa ngắn]
            * **[Thuật ngữ]:** [Định nghĩa ngắn]

            ---
            > ℹ️ *Tóm tắt dựa trên nội dung tài liệu đính kèm. Đối chiếu văn bản gốc cho các quyết định quan trọng.*

            # QUY TẮC ĐỊNH DẠNG & TRÌNH BÀY
            [F1] Dùng **in đậm** cho: tên thuật ngữ, con số quan trọng, tên công cụ, từ khóa cốt lõi.
            [F2] Dùng dấu `*` cho bullet điểm chính, thụt lề `  *` cho điểm con. LUÔN LUÔN viết hoa chữ cái đầu tiên của mỗi gạch đầu dòng.
            [F3] Dùng `---` để phân cách giữa các PHẦN.
            [F4] KHÔNG viết "wall of text" — mỗi ý phải trên 1 dòng riêng biệt. Đầu mỗi dòng phải viết hoa.
            [F5] Số liệu, giá tiền, công thức phải in đậm và rõ ràng (ví dụ: **39.99$/tháng**, **ACoS ≤ 30%**).
            [F6] Tên sản phẩm, tên sách, tên công cụ dùng *in nghiêng*.
            [F7] KIỂM TRA CHÍNH TẢ BẮT BUỘC: Vì văn bản PDF cắt tự động có thể bị mất chữ ở đầu hoặc cuối đoạn (ví dụ bắt đầu bằng dấu phẩy, cụm từ lửng lơ), BẠN PHẢI hiệu đính văn bản lại cho hoàn chỉnh, xóa ký tự thừa và sửa lỗi từ vựng trước khi trả về kết quả.
            """;


    // Constructor ─
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

    public Flux<String> streamSendMessage(Dto.ChatRequest req) {
        try {
            String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                    ? UUID.randomUUID().toString()
                    : req.getSessionId();

            saveMessage(req.getUsername(), "user", req.getMessage(), sessionId);

            List<ChatMessage> history = chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId);
            List<Book> allBooks = bookRepo.findByStatus(Book.BookStatus.ACTIVE);

            String effectiveMessage = resolveEffectiveMessage(req.getMessage(), history);

            // FIX: Tách system prompt và context payload theo chuẩn RAG
            String systemPrompt = buildBaseSystemPrompt();
            String contextPayload = buildContextPayload(req.getUsername(), req.getMessage(),
                    effectiveMessage, history, allBooks);
            String payload = buildGeminiPayload(history, contextPayload,
                    req.getMessage(), systemPrompt);

            StringBuilder fullResponse = new StringBuilder();
            final String finalEffectiveMessage = effectiveMessage;
            final List<Book> finalAllBooks = allBooks;
            final String finalUsername = req.getUsername();
            final String finalSessionId = sessionId;

            return callGeminiStreamWithFallback(payload)
                    .onErrorResume(e -> {
                        String local = buildSmartLocalAnswer(finalEffectiveMessage, finalAllBooks);
                        if (local != null && !local.isBlank()) {
                            return Flux.just(local);
                        }
                        String emsg = e.getMessage() != null ? e.getMessage() : "Lỗi hệ thống";
                        String friendlyMsg;
                        if (emsg.contains("429") || emsg.contains("RESOURCE_EXHAUSTED")) {
                            friendlyMsg = "⚠️ AI đang quá tải, vui lòng thử lại sau vài giây.";
                        } else if (emsg.contains("404") || emsg.contains("NOT_FOUND")) {
                            friendlyMsg = "⚠️ Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại.";
                        } else {
                            friendlyMsg = "⚠️ Không thể kết nối AI lúc này. Vui lòng thử lại.";
                        }
                        return Flux.just(friendlyMsg);
                    })
                    .map(chunk -> {
                        fullResponse.append(chunk);
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("chunk", chunk);
                        map.put("sessionId", finalSessionId);
                        try {
                            return mapper.writeValueAsString(map);
                        } catch (Exception e) {
                            return "{\"chunk\":\"\"}";
                        }
                    })
                    .doOnComplete(() -> {
                        String enriched = enrichWithActionTriggers(fullResponse.toString(),
                                finalEffectiveMessage, finalAllBooks);
                        saveMessage(finalUsername, "model", enriched, finalSessionId);
                    });
        } catch (Exception e) {
            return Flux.just("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public Dto.ChatResponse sendMessage(Dto.ChatRequest req) {
        try {
            String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                    ? UUID.randomUUID().toString()
                    : req.getSessionId();

            saveMessage(req.getUsername(), "user", req.getMessage(), sessionId);

            List<ChatMessage> history = chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId);

            if (geminiApiKey == null || geminiApiKey.isBlank())
                throw new IllegalStateException("Chưa cấu hình gemini.api.key");

            List<Book> allBooks = bookRepo.findByStatus(Book.BookStatus.ACTIVE);
            String effectiveMessage = resolveEffectiveMessage(req.getMessage(), history);

            String systemPrompt = buildBaseSystemPrompt();
            String contextPayload = buildContextPayload(req.getUsername(), req.getMessage(),
                    effectiveMessage, history, allBooks);
            String payload = buildGeminiPayload(history, contextPayload,
                    req.getMessage(), systemPrompt);
            String aiText;
            try {
                String responseBody = callGeminiWithFallback(payload);
                aiText = parseGeminiResponse(responseBody);
            } catch (Exception apiEx) {
                String local = buildSmartLocalAnswer(effectiveMessage, allBooks);
                aiText = (local != null) ? local : toUserFriendlyError(apiEx);
            }

            aiText = enrichWithActionTriggers(aiText, effectiveMessage, allBooks);
            saveMessage(req.getUsername(), "model", aiText, sessionId);
            return new Dto.ChatResponse(true, aiText, sessionId);
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi sendMessage: " + e.getMessage());
            return new Dto.ChatResponse(false, toUserFriendlyError(e), req.getSessionId());
        }
    }

    public Dto.ChatResponse askAboutPdf(String username, String question, String pdfPath) {
        try {
            if (!pdfReaderService.isReadable(pdfPath))
                return new Dto.ChatResponse(false,
                        "Không tìm thấy file PDF trên server. Kiểm tra pdf_path: " + pdfPath, null);

            String pdfContent = pdfReaderService.extractText(pdfPath);
            if (pdfContent == null || pdfContent.startsWith("Không đọc được"))
                return new Dto.ChatResponse(false,
                        "File PDF tồn tại nhưng không đọc được nội dung (quét ảnh hoặc mã hóa).", null);

            // RAG: chunk PDF và lấy top-K chunks liên quan thay vì cắt thô
            String cleanedPdf = filterBoilerplateFromPdf(pdfContent);
            String sourceText = cleanedPdf.isBlank() ? pdfContent : cleanedPdf;
            String retrievedContext = retrieveTopChunks(sourceText, question, TOP_K_CHUNKS);

            // Đóng gói theo chuẩn XML
            String userPayload = buildXmlUserPayload(retrievedContext, question);
            String systemPrompt = trimSystemInstruction(PDF_ANALYSIS_INSTRUCTION);
            String payload = buildSimplePayload(systemPrompt, userPayload);

            String aiText;
            try {
                aiText = parseGeminiResponse(callGeminiWithFallback(payload));
            } catch (Exception apiEx) {
                System.err.println("[ChatService] askAboutPdf Gemini Lỗi: " + apiEx.getMessage());
                aiText = buildPdfLocalAnswer(question, sourceText, pdfPath);
            }
            aiText = enrichWithActionTriggers(aiText, question, bookRepo.findByStatus(Book.BookStatus.ACTIVE));

            String sessionId = "pdf_" + UUID.randomUUID();
            saveMessage(username, "user", "[PDF] " + question, sessionId);
            saveMessage(username, "model", aiText, sessionId);
            return new Dto.ChatResponse(true, aiText, sessionId);
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi askAboutPdf: " + e.getMessage());
            return new Dto.ChatResponse(false, toUserFriendlyError(e), null);
        }
    }

    public List<Dto.ChatHistoryItem> getHistory(String sessionId) {
        return chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId)
                .stream()
                .map(m -> new Dto.ChatHistoryItem(
                        m.getRole(),
                        m.getMessage(),
                        m.getCreateDate() != null ? m.getCreateDate().format(FMT) : ""))
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, String> clearHistory(String sessionId) {
        chatRepo.deleteBySessionId(sessionId);
        return Map.of("success", "true", "message", "Đã xóa lịch sử chat");
    }

    public Map<String, Object> checkPdfConnection(String pdfPath) {
        return buildPdfConnectionResult(pdfPath, null);
    }

    public Map<String, Object> checkPdfConnectionByBookId(Long bookId) {
        Book book = bookRepo.findById(bookId).orElse(null);
        if (book == null)
            return Map.of("connected", false, "message", "Không tìm thấy sách id=" + bookId);
        return buildPdfConnectionResult(book.getPdfPath(), book);
    }

    // RAG PIPELINE — Chunking & Retrieval

    /**
     * Phân đoạn văn bản thành các chunk có kích thước chuẩn với overlap.
     * Theo read.md: chunk size 500-1000 ký tự, overlap 10-20%.
     *
     * TODO (Vector DB): Sau khi chunk, gọi Gemini Embedding API
     * (text-embedding-004)
     * để tạo vector 768 chiều cho mỗi chunk, lưu vào bảng book_chunks
     * (book_id, chunk_index, chunk_text, embedding vector(768)).
     */
    private List<String> chunkText(String text) {
        if (text == null || text.isBlank())
            return List.of();

        // Tách thành câu trước, sau đó gom thành chunks
        String[] sentences = text.split("(?<=[.!?\\n])\\s+");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isBlank())
                continue;

            if (current.length() + trimmed.length() > CHUNK_SIZE && current.length() > 0) {
                chunks.add(current.toString().trim());
                // Overlap: giữ lại phần cuối của chunk trước
                String currentStr = current.toString();
                int overlapStart = Math.max(0, currentStr.length() - CHUNK_OVERLAP);
                current = new StringBuilder(currentStr.substring(overlapStart));
                current.append(" ");
            }
            current.append(trimmed).append(" ");
        }
        if (!current.isEmpty())
            chunks.add(current.toString().trim());
        return chunks;
    }

    /**
     * Lấy Top-K chunk liên quan nhất theo BM25-style scoring,
     * GIỮ NGUYÊN thứ tự xuất hiện trong tài liệu gốc (không xáo trộn).
     *
     * TODO (Vector DB): Thay toàn bộ hàm này bằng:
     * 1. Gọi Embedding API cho userQuery → queryVector
     * 2. SELECT chunk_text FROM book_chunks
     * ORDER BY embedding <=> queryVector (pgvector Cosine)
     * LIMIT topK;
     */
    private String retrieveTopChunks(String pdfText, String userQuery, int topK) {
        List<String> chunks = chunkText(pdfText);
        if (chunks.isEmpty())
            return "";

        // ── Chunk quality gate: loại bỏ chunk boilerplate trước khi scoring ──
        List<String> qualityChunks = chunks.stream()
                .filter(chunk -> {
                    // Loại chunk quá ngắn
                    if (chunk.length() < 80) return false;
                    // Loại chunk có tỉ lệ chữ quá thấp
                    long letters = chunk.chars().filter(Character::isLetter).count();
                    if ((double) letters / chunk.length() < 0.45) return false;
                    // Loại block thông tin nhà xuất bản
                    if (isPublisherInfoBlock(chunk)) return false;
                    return true;
                })
                .collect(Collectors.toList());

        // Nếu sau khi lọc không còn chunk nào có chất lượng → báo trống
        if (qualityChunks.isEmpty())
            return "";

        String[] queryTokens = Arrays.stream(normalizeSearch(userQuery).split("\\s+"))
                .filter(t -> t.length() >= 3 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);

        if (queryTokens.length == 0) {
            // Không có token có nghĩa → trả về các chunk chất lượng đầu tiên
            return qualityChunks.stream().limit(topK).collect(Collectors.joining("\n\n"));
        }

        // Tính IDF đơn giản
        int totalChunks = qualityChunks.size();
        Map<String, Double> idf = new HashMap<>();
        for (String token : queryTokens) {
            long df = qualityChunks.stream()
                    .filter(c -> normalizeSearch(c).contains(token))
                    .count();
            idf.put(token, Math.log((double) (totalChunks + 1) / (df + 1)) + 1.0);
        }

        // Đánh score từng chunk (BM25-style TF × IDF)
        record IndexedChunk(int index, String text, double score) {
        }
        List<IndexedChunk> scored = new ArrayList<>();
        for (int i = 0; i < qualityChunks.size(); i++) {
            String nc = normalizeSearch(qualityChunks.get(i));
            double score = 0;
            for (String token : queryTokens) {
                int tf = countOccurrences(nc, token);
                if (tf > 0) {
                    // BM25: TF saturation k1=1.5, b=0.75
                    double tfSat = (tf * 2.5) / (tf + 1.5);
                    score += tfSat * idf.getOrDefault(token, 1.0);
                    // Boost cho token dài (từ chuyên ngành quan trọng hơn)
                    if (token.length() >= 6)
                        score += idf.getOrDefault(token, 1.0) * 0.5;
                }
            }
            if (score > 0)
                scored.add(new IndexedChunk(i, qualityChunks.get(i), score));
        }

        if (scored.isEmpty()) {
            // Không có chunk nào khớp query → trả top-K chunk chất lượng đầu tiên
            return qualityChunks.stream().limit(topK).collect(Collectors.joining("\n\n"));
        }

        // Sắp xếp theo score, lấy top-K, rồi sắp xếp lại theo thứ tự gốc
        return scored.stream()
                .sorted(Comparator.comparingDouble(IndexedChunk::score).reversed())
                .limit(topK)
                .sorted(Comparator.comparingInt(IndexedChunk::index)) // QUAN TRỌNG: giữ thứ tự tài liệu
                .map(IndexedChunk::text)
                .collect(Collectors.joining("\n\n"));
    }

    /** Đếm số lần xuất hiện của token trong text (không phân biệt hoa/thường). */
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
     * Đóng gói context và câu hỏi theo chuẩn XML của read.md.
     * read.md: dùng thẻ <internal_context> và <user_query> để AI phân biệt rõ ràng.
     */
    private String buildXmlUserPayload(String context, String userQuery) {
        return "<internal_context>\n" + context + "\n</internal_context>\n\n"
                + "<user_query>\n" + userQuery + "\n</user_query>";
    }

    // SYSTEM PROMPT BUILDING

    /**
     * System prompt cơ bản — chỉ chứa role + formatting rules, không chứa dữ liệu
     * nghiệp vụ.
     */
    private String buildBaseSystemPrompt() {
        return BASE_INSTRUCTION
                + "\nThời gian hệ thống hiện tại: " + LocalDateTime.now().format(FMT) + "\n";
    }

    /**
     * Xây dựng context payload theo chuẩn RAG — dữ liệu nghiệp vụ đưa vào user
     * message,
     * KHÔNG đưa vào system instruction.
     * Context được bọc trong thẻ XML <internal_context> theo read.md.
     */
    private String buildContextPayload(String username, String userMessage,
            String effectiveMessage,
            List<ChatMessage> history, List<Book> allBooks) {
        StringBuilder contextSb = new StringBuilder();
        ChatIntent intent = detectIntent(effectiveMessage, allBooks);
        contextSb.append("[INTENT: ").append(intent).append("]\n");

        try {
            switch (intent) {
                case ORDER -> appendUserOrders(contextSb, username);
                case PRICE_SORT -> handlePriceSort(contextSb, allBooks, effectiveMessage);
                case SPECIFIC_BOOK -> handleSpecificBook(contextSb, allBooks, effectiveMessage);
                case CONTENT_QUESTION -> handleContentQuestion(contextSb, allBooks, effectiveMessage);
                case PRICE_INFO -> handlePriceInfo(contextSb, allBooks, effectiveMessage);
                case CATALOG_SEARCH -> handleCatalogSearch(contextSb, allBooks, effectiveMessage);
                default -> handleGeneralQuery(contextSb, allBooks, effectiveMessage);
            }
            // Bổ sung đơn hàng nếu cần
            if (intent != ChatIntent.ORDER && username != null && !username.isBlank()
                    && normalizeSearch(userMessage).contains("don")) {
                appendUserOrders(contextSb, username);
            }
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi dựng context payload: " + e.getMessage());
        }

        String context = contextSb.toString().trim();
        if (context.isBlank())
            context = "Không có dữ liệu nghiệp vụ liên quan.";

        // Bổ sung context lịch sử nếu là câu hỏi tiếp nối
        if (!effectiveMessage.equals(userMessage)) {
            context = "[BỔ SUNG NGỮ CẢNH LỊCH SỬ]\n" + context;
        }

        return context;
    }

    // Intent handlers (giữ nguyên logic, chỉ thay StringBuilder tham số)

    enum ChatIntent {
        ORDER, PRICE_SORT, SPECIFIC_BOOK, CONTENT_QUESTION, PRICE_INFO, CATALOG_SEARCH, GENERAL
    }

    private ChatIntent detectIntent(String msg, List<Book> allBooks) {
        if (msg == null || msg.isBlank())
            return ChatIntent.GENERAL;
        String n = normalizeSearch(msg);

        if (n.matches(".*(don hang|tra hang|huy don|van chuyen|giao hang|trang thai don|ma don).*"))
            return ChatIntent.ORDER;

        if (n.matches(
                ".*(gia cao nhat|dat nhat|mac nhat|gia thap nhat|re nhat|gia re nhat|sap xep.*gia|gia.*sap xep|gia tien cao|gia tien thap|cuon nao dat|cuon nao mac|cuon nao re|sach nao dat|sach nao mac|sach nao re|dat hon|mac hon|re hon|so sanh gia).*"))
            return ChatIntent.PRICE_SORT;

        Book bookHint = findBestBookFromQuery(allBooks, msg);
        if (bookHint != null && (isSpecificBookInfoQuestion(msg, allBooks)
                || n.matches(".*(gia|bao nhieu|ton kho|tac gia|thong tin|chi tiet|mo ta).*")))
            return ChatIntent.SPECIFIC_BOOK;

        if (isCatalogQuestion(n))
            return ChatIntent.CATALOG_SEARCH;

        if (n.matches(".*(tac gia|nguoi viet|ai viet|sach cua).*") && !extractAuthorNameFromQuery(msg).isBlank())
            return ChatIntent.CATALOG_SEARCH;

        if (isContentQuestion(n))
            return ChatIntent.CONTENT_QUESTION;

        if (n.matches(".*(gia|bao nhieu tien|bao nhieu d|bao nhieu dong).*"))
            return ChatIntent.PRICE_INFO;

        return ChatIntent.GENERAL;
    }

    private void handlePriceSort(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String n = normalizeSearch(userMessage);
        boolean descending = !n.matches(".*(re nhat|gia thap nhat|gia re nhat|thap nhat|re hon).*");

        List<Book> sorted = allBooks.stream()
                .filter(b -> b.getPrice() != null)
                .sorted(descending ? Comparator.comparing(Book::getPrice).reversed()
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
                    .append(formatPrice(b.getPrice())).append(" | Kho: ").append(b.getQuantity()).append(" cuốn\n");
        }
        sb.append("[HƯỚNG DẪN AI: Dùng ## Bảng xếp hạng giá, phân tách bằng ---, làm nổi bật vị trí số 1.]\n");
    }

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
        if (askPrice || !askAuthor && !askStock && !askContent)
            sb.append("Giá: ").append(formatPrice(book.getPrice())).append("\n");
        if (askAuthor || !askPrice && !askStock && !askContent)
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
                sb.append(
                        "[HƯỚNG DẪN AI: Không phát hiện dữ liệu liên quan. Đề xuất người dùng cung cấp tên sách cụ thể.]\n");
        }
    }

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
            sb.append("* \"").append(b.getTitle()).append("\" | Giá: ").append(formatPrice(b.getPrice()))
                    .append(" | Kho: ").append(b.getQuantity()).append("\n");
        sb.append("[HƯỚNG DẪN AI: Dùng ## Bảng giá sách, --- và danh sách *.]\n");
    }

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

    // PDF CHUNK RETRIEVAL (thay thế appendPdfExcerpts cũ)

    /**
     * Lấy top-K chunk liên quan từ danh sách sách.
     * FIX: Dùng chunkText() + retrieveTopChunks() thay vì extractRelevantSnippet().
     */
    private boolean appendPdfChunks(StringBuilder sb, List<Book> books, String userMessage) {
        List<Book> sorted = books.stream()
                .sorted(Comparator.comparingInt(b -> (b.getPdfPath() != null
                        && pdfReaderService.isReadable(b.getPdfPath())) ? 0 : 1))
                .collect(Collectors.toList());

        int totalCharsAdded = 0;
        final int MAX_TOTAL = 12000;
        int added = 0;

        for (Book b : sorted) {
            if (totalCharsAdded >= MAX_TOTAL || added >= 3)
                break;
            String path = b.getPdfPath();
            if (path == null || path.isBlank() || !pdfReaderService.isReadable(path))
                continue;

            String pdfText;
            try {
                pdfText = pdfReaderService.extractText(path);
            } catch (Exception e) {
                System.err.println("[ChatService] Lỗi trích PDF bookId=" + b.getId() + ": " + e.getMessage());
                continue;
            }
            if (pdfText == null || pdfText.isBlank() || pdfText.startsWith("Không đọc được"))
                continue;

            String cleaned = filterBoilerplateFromPdf(pdfText);
            String source = cleaned.isBlank() ? pdfText : cleaned;
            int allowedChars = Math.min(6000, MAX_TOTAL - totalCharsAdded);

            // RAG: dùng chunk + retrieval thay vì cắt thô
            String chunks = retrieveTopChunks(source, userMessage, TOP_K_CHUNKS);
            if (chunks.isBlank())
                continue;

            String snippet = safeSubstring(chunks, allowedChars);
            String label = (b.getPdfName() != null && !b.getPdfName().isBlank()) ? b.getPdfName() : b.getTitle();
            sb.append("=== TRÍCH ĐOẠN PDF (\"").append(b.getTitle()).append("\" | ").append(label).append(") ===\n")
                    .append(snippet).append("\n");

            totalCharsAdded += snippet.length();
            added++;
        }
        return added > 0;
    }

    /**
     * Tìm sách có PDF khớp với query, tối đa 2 sách.
     * FIX: Dừng sớm khi đủ 2 sách thay vì quét toàn bộ.
     */
    private boolean appendPdfChunksFromAllBooks(StringBuilder sb, List<Book> allBooks, String userMessage) {
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
                break; // Dừng sớm khi đủ
            String path = b.getPdfPath();
            if (path == null || path.isBlank() || !pdfReaderService.isReadable(path))
                continue;

            try {
                String pdfText = pdfReaderService.extractText(path);
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

    // GEMINI API — Non-blocking, với generationConfig + temperature

    /**
     * Xây dựng Gemini payload.
     * FIX 1: Thêm generationConfig với temperature=0.1 (read.md yêu cầu).
     * FIX 2: Context data được inject vào user message thay vì system_instruction.
     * FIX 3: Cấu trúc user message dùng XML tags <internal_context> + <user_query>.
     */
    private String buildGeminiPayload(List<ChatMessage> history, String contextPayload,
            String newMessage, String systemInstruction) throws Exception {
        List<Map<String, Object>> contents = new ArrayList<>();
        int historyStart = Math.max(0, history.size() - maxHistoryMessages);

        // Đảm bảo lượt đầu tiên trong history là "user"
        while (historyStart < history.size()
                && !"user".equals(history.get(historyStart).getRole())) {
            historyStart++;
        }

        String lastRole = null;
        for (int i = historyStart; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            String role = m.getRole();
            String msgText = m.getMessage() != null ? m.getMessage() : "";

            if ("model".equals(role) && msgText.length() > 400) {
                msgText = msgText.substring(0, 400) + "…[rút gọn]";
            }

            if (role.equals(lastRole) && !contents.isEmpty()) {
                Map<String, Object> last = contents.get(contents.size() - 1);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parts = (List<Map<String, Object>>) last.get("parts");
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

        // Message mới của user — bọc context trong XML tags theo read.md
        String finalMessage = (newMessage != null && !newMessage.isBlank()) ? newMessage : "Xin chào";
        String userPayload = buildXmlUserPayload(
                trimSystemInstruction(contextPayload), finalMessage);

        if (contents.isEmpty()) {
            Map<String, Object> userTurn = new LinkedHashMap<>();
            userTurn.put("role", "user");
            userTurn.put("parts", List.of(Map.of("text", userPayload)));
            contents.add(userTurn);
        } else {
            // Cập nhật nội dung của tin nhắn user cuối cùng
            Map<String, Object> lastContent = contents.get(contents.size() - 1);
            if ("user".equals(lastContent.get("role"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parts = (List<Map<String, Object>>) lastContent.get("parts");
                parts.get(0).put("text", userPayload);
            } else {
                Map<String, Object> userTurn = new LinkedHashMap<>();
                userTurn.put("role", "user");
                userTurn.put("parts", List.of(Map.of("text", userPayload)));
                contents.add(userTurn);
            }
        }

        // generationConfig: temperature=0.1 theo read.md (tránh hallucination)
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("topP", 0.85);
        generationConfig.put("topK", 40);
        generationConfig.put("maxOutputTokens", 2048);


        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        payload.put("contents", contents);
        payload.put("generationConfig", generationConfig);
        return mapper.writeValueAsString(payload);
    }

    private String buildSimplePayload(String system, String userMessage) throws Exception {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", TEMPERATURE);
        generationConfig.put("topP", 0.85);
        generationConfig.put("topK", 40);
        generationConfig.put("maxOutputTokens", 2048);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", system))));
        payload.put("contents", List.of(Map.of("role", "user",
                "parts", List.of(Map.of("text", userMessage)))));
        payload.put("generationConfig", generationConfig);
        return mapper.writeValueAsString(payload);
    }

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
            }
        }
        throw lastError != null ? lastError : new RuntimeException("Tất cả Gemini model tạm thời không khả dụng.");
    }

    /**
     * FIX: Dùng subscribeOn(Schedulers.boundedElastic()) để tránh blocking trên
     * Netty I/O thread.
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
                                .map(body -> new RuntimeException("Gemini API Lỗi " + resp.statusCode() + ": " + body)))
                .bodyToMono(String.class)
                .subscribeOn(Schedulers.boundedElastic()) // FIX: tránh blocking Netty thread
                .block();
    }

    /**
     * FIX: Chỉ retry 429 (quota) và 503 (server quá tải).
     * Bỏ 404 — model không tồn tại, retry vô nghĩa và tốn thời gian.
     */
    private boolean isRetryableGeminiError(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        return msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("503");
    }

    private Flux<String> callGeminiStreamWithFallback(String payload) {
        List<String> models = buildModelList();
        Flux<String> flux = callGeminiStream(models.get(0), payload)
                .doOnSubscribe(s -> System.out.println("[ChatService] Stream dùng model: " + models.get(0)));

        for (int i = 1; i < models.size(); i++) {
            final String nextModel = models.get(i);
            flux = flux.onErrorResume(ex -> {
                String msg = ex.getMessage() != null ? ex.getMessage() : "";
                // Chỉ fallback khi quota hết (429) hoặc server quá tải (503)
                // KHÔNG fallback khi 404 (model không tồn tại) vì vô nghĩa
                if (msg.contains("404") || msg.contains("NOT_FOUND")) {
                    System.err.println("[ChatService] Model 404 - KHÔNG fallback, báo lỗi ngay: " + msg);
                    return Flux.error(ex);
                }
                System.err.println("[ChatService] Stream fallback sang: " + nextModel + " | Lý do: " + msg.substring(0, Math.min(80, msg.length())));
                return callGeminiStream(nextModel, payload)
                        .doOnSubscribe(s -> System.out.println("[ChatService] Đang dùng fallback: " + nextModel));
            });
        }
        return flux.onErrorResume(ex -> {
            System.err.println("[ChatService] Tất cả stream model thất bại: " + ex.getMessage());
            return Flux.error(ex);
        });
    }

    private Flux<String> callGeminiStream(String model, String payload) {
        String url = GEMINI_BASE + model + ":streamGenerateContent?alt=sse&key=" + geminiApiKey;
        System.out.println("[ChatService] Gọi Gemini URL: " + GEMINI_BASE + model + ":streamGenerateContent?alt=sse");
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
                        if (t.isBlank()) continue;
                        
                        String json = t;
                        if (json.startsWith("data:")) {
                            json = json.substring(5).trim();
                        }
                        if ("[DONE]".equals(json)) continue;
                        
                        try {
                            if (json.startsWith("{")) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> response = mapper.readValue(json, Map.class);
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                                if (candidates == null || candidates.isEmpty()) continue;
                                
                                @SuppressWarnings("unchecked")
                                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                                if (content == null) continue;
                                
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                                if (parts == null || parts.isEmpty()) continue;
                                
                                String text = (String) parts.get(0).get("text");
                                if (text != null) chunks.add(text);
                            }
                        } catch (Exception e) {
                            System.err.println("[ChatService] Parse SSE JSON lỗi: " + e.getMessage());
                        }
                    }
                    return Flux.fromIterable(chunks);
                });
    }

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

    // CONTEXT RESOLUTION — Follow-up question handling

    private String resolveEffectiveMessage(String message, List<ChatMessage> history) {
        if (isFollowUpQuestion(message) && !history.isEmpty()) {
            String topic = extractTopicFromHistory(history, 2);
            if (!topic.isBlank())
                return topic + " " + message;
        }
        return message;
    }

    private boolean isFollowUpQuestion(String msg) {
        if (msg == null || msg.isBlank())
            return false;
        String n = normalizeSearch(msg);
        boolean hasFollowUp = n.matches(
                ".*(con.*nao|con gi|cuon khac|sach khac|cai khac|loai khac|them cai|them cuon|"
                        + "the con|vay con|con nua|gi nua|cuon do|sach do|no thi|cai do|"
                        + "the thi|vay thi|mua di|dat hang di|bao nhieu nua|gia nua|tac gia nua).*");
        boolean veryShort = msg.trim().split("\\s+").length <= 6;
        return hasFollowUp || veryShort;
    }

    private String extractTopicFromHistory(List<ChatMessage> history, int recentN) {
        if (history == null || history.size() < 2)
            return "";
        int end = history.size() - 1;
        int start = Math.max(0, end - recentN);
        StringBuilder ctx = new StringBuilder();
        for (int i = start; i < end; i++) {
            String content = history.get(i).getMessage();
            if (content != null && !content.isBlank())
                ctx.append(content).append(" ");
        }
        return ctx.toString().trim();
    }

    // BOOK SEARCH & SCORING

    private boolean isContentQuestion(String normalizedMsg) {
        if (normalizedMsg == null)
            return false;
        if (normalizedMsg.matches(
                ".*(gia cao nhat|dat nhat|mac nhat|re nhat|gia thap nhat|gia bao nhieu|bao nhieu tien|sap xep gia|so sanh gia).*"))
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
            boolean isMeta = normalizedMsg
                    .matches(".*(gia|bao nhieu|con hang|con may|ton kho|tac gia|nguoi viet|xuat ban).*");
            return !isMeta;
        }
        return false;
    }

    private boolean isCatalogQuestion(String normalizedMsg) {
        if (normalizedMsg == null)
            return false;
        return normalizedMsg.matches(
                ".*(co sach|tim sach|goi y sach|sach nao|co gi|ban gi|sach hay|danh sach|xem sach|mua sach|dat mua|sach ve).*");
    }

    private boolean isSpecificBookInfoQuestion(String userMessage, List<Book> allBooks) {
        if (userMessage == null)
            return false;
        String n = normalizeSearch(userMessage);
        if (extractBookTitleFromQuestion(n, allBooks) != null)
            return true;
        boolean asksInfo = n.matches(
                ".*\\b(thong tin|chi tiet|mo ta|gioi thieu|gia|bao nhieu|con hang|ton kho|tac gia|nguoi viet|xuat ban)\\b.*");
        boolean mentionsBook = n.matches(".*\\b(sach|cuon|book)\\b.*");
        return asksInfo && mentionsBook;
    }

    private String extractBookTitleFromQuestion(String normalizedQuery, List<Book> allBooks) {
        if (normalizedQuery == null || normalizedQuery.isBlank())
            return null;
        String cleaned = normalizedQuery
                .replaceAll(
                        "\\b(cho toi biet|cho minh biet|cho toi|cho minh|xem|ve|cua|thong tin|chi tiet|mo ta|gioi thieu|gia|bao nhieu|tac gia|nguoi viet|con hang|ton kho|sach|cuon|book|tom tat|doc|pdf|noi dung|chuong|tiet|phan|hoi|muon|can|hay|giup|nhe|nha|ad|mac|dat|re)\\b",
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
                    .filter(b -> normalizeSearch(b.getTitle()).equals(nt))
                    .findFirst().orElse(null);
            if (exact != null)
                return exact;
            return allBooks.stream()
                    .filter(b -> b.getTitle().toLowerCase(Locale.ROOT).contains(extracted.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
        }
        List<Book> matched = findRelevantBooks(allBooks, userMessage, true);
        return matched.size() == 1 ? matched.get(0) : null;
    }

    @SuppressWarnings("unchecked")
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

    private List<String> extractSearchKeywords(String userMessage) {
        if (userMessage == null || userMessage.isBlank())
            return List.of();
        return Arrays.stream(normalizeSearch(userMessage).split("\\s+"))
                .filter(t -> t.length() >= 2 && !STOP_WORDS.contains(t))
                .distinct()
                .collect(Collectors.toList());
    }

    private Book findBestBookFromQuery(List<Book> allBooks, String userMessage) {
        if (allBooks == null || allBooks.isEmpty() || userMessage == null || userMessage.isBlank())
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

    private String extractAuthorNameFromQuery(String userMessage) {
        if (userMessage == null)
            return "";
        String n = normalizeSearch(userMessage);
        String[] patterns = { "tac gia la", "tac gia", "nguoi viet la", "nguoi viet", "ai viet la", "boi" };
        for (String pat : patterns) {
            int idx = n.indexOf(pat);
            if (idx >= 0) {
                String after = n.substring(idx + pat.length()).trim()
                        .replaceAll("^(la|ten|la ten)\\s+", "").trim();
                if (!after.isBlank())
                    return after;
            }
        }
        return "";
    }

    private boolean isGenericBookQuery(String q) {
        if (q == null || q.isBlank())
            return false;
        String n = normalizeSearch(q);
        return n.matches(".*\\b(sach|danh sach|co gi|ban gi|goi y|xem sach)\\b.*")
                && !n.matches(
                        ".*\\b(java|python|lap trinh|lich su|van hoc|toan|van|anh|sinh|ky nang|kinh doanh|tam ly|triet hoc|khoa hoc|the thao|ban hang|marketing|kinh te|giao duc|y hoc|cong nghe)\\b.*");
    }

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
                score += token.length() >= 5 ? 35 : 28;
            if (category.contains(token))
                score += 22;
            if (author.contains(token))
                score += 18;
            if (desc.contains(token))
                score += 8;
        }
        return score;
    }

    // BOILERPLATE FILTER

    private boolean isBoilerplateSentence(String normalizedSentence) {
        if (normalizedSentence == null || normalizedSentence.isBlank())
            return true;
        // Pattern mở rộng: bao gồm các biến thể thực tế từ PDF tiếng Việt
        if (normalizedSentence.matches(
                ".*(nha xuat ban|nxb|ban quyen|all rights reserved|copyright|isbn|issn|"
                        + "tru so chinh|chi nhanh|so dien thoai|dien thoai|tel|fax|email|website|http|www\\.|"
                        + "in lan|tai ban|xuat ban lan|moq|minimum order|"
                        + "lien he|hop dong|giay phep|gkxb|in tai|"
                        + "marketing@|info@|support@|sales@|publisher|"
                        + "giam doc|tong bien tap|bien tap|ve bia|trinh bay|sua ban in|chinh sua|"
                        + "chiu trach nhiem|ky duyet|nguoi ky|chu biet|chuc vu|"
                        + "dia chi|so nha|ngo |ngo$|duong |phuong |quan |thanh pho|tp\\.|tp |"
                        + "khu cong nghiep|kcn|lo |tang |toa |co so|xuong sx|xuong san xuat|"
                        + "cong ty co phan|cong ty tnhh|joint stock|co phan truyen thong|"
                        + "[0-9]{9,12}|[0-9]{2,4}[-.][0-9]{3,4}[-.][0-9]{3,4})"))
            return true;
        // Dòng chỉ toàn số, dấu câu
        if (normalizedSentence.matches("^[\\d\\s\\p{Punct}]{1,20}$"))
            return true;
        // Dòng có tỉ lệ ký tự chữ thấp (nhiều số/ký hiệu)
        int letterCount = 0;
        for (char c : normalizedSentence.toCharArray())
            if (Character.isLetter(c))
                letterCount++;
        return normalizedSentence.length() > 8 && (double) letterCount / normalizedSentence.length() < 0.40;
    }

    /**
     * Kiểm tra xem một đoạn văn bản có phải là block thông tin xuất bản không.
     * Dùng để loại bỏ các trang bìa / trang pháp lý của sách.
     */
    private boolean isPublisherInfoBlock(String block) {
        if (block == null || block.isBlank()) return false;
        String n = normalizeSearch(block);
        // Nếu chứa nhiều hơn 2 trong số các dấu hiệu xuất bản → boilerplate block
        int signals = 0;
        if (n.contains("chiu trach nhiem") || n.contains("giam doc") || n.contains("tong bien tap")) signals++;
        if (n.contains("bien tap") || n.contains("ve bia") || n.contains("trinh bay") || n.contains("sua ban in")) signals++;
        if (n.contains("nha xuat ban") || n.contains("nxb") || n.contains("isbn") || n.contains("issn")) signals++;
        if (n.contains("dia chi") || n.contains("email") || n.contains("website") || n.contains("dien thoai")) signals++;
        if (n.contains("in lan") || n.contains("xuat ban lan") || n.contains("in tai") || n.contains("kcn")) signals++;
        if (n.contains("cong ty co phan") || n.contains("cong ty tnhh") || n.contains("truyen thong")) signals++;
        return signals >= 2;
    }

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
                // Dòng trắng: flush blockBuf nếu không phải boilerplate block
                if (blockBuf.length() > 0) {
                    String block = blockBuf.toString().trim();
                    if (!isPublisherInfoBlock(block)) {
                        filtered.append(block).append("\n\n");
                    }
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
                    // Block chủ yếu là boilerplate → bỏ
                    blockBuf.setLength(0);
                } else if (blockBuf.length() > 0) {
                    // Bổ sung các dòng non-boilerplate từ block trước vào output
                    filtered.append(blockBuf).append("\n");
                    blockBuf.setLength(0);
                }
                filtered.append(line).append("\n");
                consecutiveBoilerplate = 0;
            }
        }
        // Flush buffer cuối
        if (blockBuf.length() > 0) {
            String block = blockBuf.toString().trim();
            if (!isPublisherInfoBlock(block))
                filtered.append(block);
        }
        return filtered.toString().trim();
    }

    // LOCAL FALLBACK ANSWER (khi Gemini API không khả dụng)

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

            Book specific = findSpecificBookInfoMatch(books, userMessage);
            if (specific != null) {
                String pdfText = null;
                if (isContentQuestion(normalizeSearch(userMessage)) && specific.getPdfPath() != null
                        && pdfReaderService.isReadable(specific.getPdfPath())) {
                    pdfText = pdfReaderService.extractText(specific.getPdfPath());
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
                        String pdfText = pdfReaderService.extractText(primary.getPdfPath());
                        if (pdfText != null && !pdfText.startsWith("Không đọc được"))
                            return formatDetailedBookAnswer(primary, userMessage, pdfText, candidates.size() > 1);
                    }
                    String answer = formatDetailedBookAnswer(primary, userMessage, null, candidates.size() > 1);
                    boolean hasDesc = primary.getDescription() != null && !primary.getDescription().isBlank();
                    if (!hasDesc)
                        answer += "\n\n> ⚠️ *Tác phẩm chưa được cập nhật file đính kèm lẫn mô tả.*";
                    return answer;
                }
                return "Mình chưa tìm thấy phân đoạn tri thức phù hợp. Hãy chỉ định tên cuốn sách cụ thể nhé! 📚";
            }

            Book single = findBestBookFromQuery(books, userMessage);
            if (single != null && isSpecificBookInfoQuestion(userMessage, books)) {
                String pdfText = null;
                if (single.getPdfPath() != null && pdfReaderService.isReadable(single.getPdfPath()))
                    pdfText = pdfReaderService.extractText(single.getPdfPath());
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
                        + "Hãy thử tên cụ thể hơn, ví dụ: *Lập trình Java*, *Tâm lý học*.\n\n---\n**Bước tiếp theo:** 👇\n"
                        + buildActionButton("navigate", "books", null, null, "Xem toàn bộ kệ sách", "📚") + "\n"
                        + searchTag).trim();
            }
            if (matched.size() == 1) {
                String pdfText = null;
                if (matched.get(0).getPdfPath() != null && pdfReaderService.isReadable(matched.get(0).getPdfPath()))
                    pdfText = pdfReaderService.extractText(matched.get(0).getPdfPath());
                return formatDetailedBookAnswer(matched.get(0), userMessage, pdfText, false);
            }
            return formatBookListAnswer(userMessage, matched);
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi buildSmartLocalAnswer: " + e.getMessage());
            return null;
        }
    }

    // FORMAT ANSWER (Local fallback)

    private String formatDetailedBookAnswer(Book b, String userMessage, String pdfText, boolean hasMore) {
        boolean isPdfContentQuery = pdfText != null && !pdfText.isBlank()
                && isContentQuestion(normalizeSearch(userMessage));
        StringBuilder sb = new StringBuilder();

        if (isPdfContentQuery) {
            String cleanedPdf = filterBoilerplateFromPdf(pdfText);
            String sourceText = cleanedPdf.isBlank() ? pdfText : cleanedPdf;
            // RAG: dùng retrieveTopChunks thay vì extractRelevantSnippet
            String chunks = retrieveTopChunks(sourceText, userMessage, TOP_K_CHUNKS);
            String snippet = chunks.isBlank() ? safeSubstring(sourceText, 3000) : safeSubstring(chunks, 4000);

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
            sb.append("\n---\n\n")
                    .append("> ℹ️ *Phân tích trích xuất hoàn toàn từ tài liệu đính kèm. Vui lòng đối chiếu với văn bản gốc.*\n");
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
                String snippet = chunks.isBlank() ? safeSubstring(pdfText, 2000) : safeSubstring(chunks, 2000);
                if (!snippet.isBlank()) {
                    sb.append("\n---\n\n## Tóm tắt nội dung PDF\n\n")
                            .append(formatPdfSnippetAsBullets(snippet, 5)).append("\n");
                }
            }
            if (hasMore)
                sb.append(
                        "\n---\n\n## Gợi ý thêm\n\nKho còn một số đầu sách tương tự, bạn muốn thu hẹp tìm kiếm không?\n");
            appendActionTriggersForBook(sb, b);
        }
        return sb.toString().trim();
    }

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
            sb.append("   - **Tác giả:** ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa cập nhật")
                    .append("\n");
            sb.append("   - **Chuyên mục:** ").append(b.getCategory() != null ? b.getCategory() : "—").append("\n");
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

    // PDF LOCAL ANSWER (khi API lỗi, dựa trên chunking)

    private String buildPdfLocalAnswer(String question, String pdfText, String pdfPath) {
        String nq = normalizeSearch(question != null ? question : "");
        boolean wantSum = nq.matches(".*(tom tat|tong hop|summary|noi dung chinh|outline|muc luc).*");
        Book linked = findBookByPdfPath(pdfPath);
        String docTitle = (linked != null && linked.getTitle() != null) ? linked.getTitle() : "tài liệu đính kèm";

        String cleanedPdf = filterBoilerplateFromPdf(pdfText);
        String sourceText = cleanedPdf.isBlank() ? pdfText : cleanedPdf;

        // RAG: dùng chunk retrieval thay vì cắt thô
        int topK = wantSum ? 8 : TOP_K_CHUNKS;
        String chunks = retrieveTopChunks(sourceText, question, topK);
        String snippet = chunks.isBlank() ? safeSubstring(sourceText, 3500)
                : safeSubstring(chunks, wantSum ? 6000 : 4000);
        if (snippet.length() < 200)
            snippet = extractMeaningfulSentences(sourceText, 5);

        StringBuilder sb = new StringBuilder();
        sb.append("Phản hồi này được phân tích và trích xuất dựa trên nội dung gốc của tài liệu **")
                .append(docTitle).append("**.\n\n");
        sb.append("---\n\n## Luận điểm chính\n\n").append(extractMainThesisFromSnippet(snippet, question))
                .append("\n\n");
        sb.append("---\n\n## Kiến thức kỹ thuật / Phương pháp luận\n\n")
                .append(formatPdfSnippetAsBullets(snippet, wantSum ? 8 : 6)).append("\n");
        sb.append("---\n\n## Ứng dụng thực tế\n\n").append(extractPracticalApplicationFromSnippet(snippet))
                .append("\n\n");
        sb.append("---\n\n## Tóm tắt cốt lõi\n\n");
        for (String p : extractKeyPointsFromSnippet(snippet, 3))
            sb.append("* ").append(p).append("\n");
        sb.append("\n---\n\n")
                .append("> ℹ️ *Phân tích trích xuất hoàn toàn từ tài liệu đính kèm. Vui lòng đối chiếu với văn bản gốc.*\n");
        if (linked != null)
            appendActionTriggersForBook(sb, linked);
        return sb.toString().trim();
    }

    private String extractMeaningfulSentences(String text, int maxSentences) {
        if (text == null || text.isBlank())
            return "";
        String[] sentences = text.split("(?<=[.!?])\\s+");
        List<String> good = new ArrayList<>();
        for (String s : sentences) {
            String t = s.trim();
            if (t.length() >= 40 && t.length() <= 500 && !isBoilerplateSentence(normalizeSearch(t))) {
                good.add(t);
                if (good.size() >= maxSentences)
                    break;
            }
        }
        return String.join(" ", good);
    }

    private String formatPdfSnippetAsBullets(String snippet, int maxItems) {
        if (snippet == null || snippet.isBlank())
            return "* _(Không thể trích xuất đoạn phù hợp từ tài liệu này.)_\n";

        String cleaned = snippet
                .replaceAll("(?<=[A-Za-zÀ-ỹ])\\s*[-–:—]+\\s*$", "")
                .replaceAll("(?m)^.{1,30}[-:—]+\\s*$", "")
                .replaceAll("[A-Z]{1,3}\\s+[-–:—]+\\s*", "")
                .replaceAll("\\s{2,}", " ").trim();

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

    private String extractPracticalApplicationFromSnippet(String snippet) {
        if (snippet == null || snippet.isBlank())
            return "Kiến thức trong tài liệu có thể áp dụng trực tiếp vào thực tế.";
        String[] sentences = snippet.split("(?<=[.!?])\\s+");
        for (String s : sentences) {
            String norm = normalizeSearch(s);
            if (norm.matches(
                    ".*(ap dung|thuc te|su dung|trien khai|can lam|nen lam|cach|buoc|quy trinh|ket qua|mang lai|giup|tang|giam|cai thien).*")
                    && s.trim().length() >= 40)
                return s.trim();
        }
        String last = sentences[sentences.length - 1].trim();
        return last.length() >= 30 ? last : "Tài liệu cung cấp các hướng dẫn thực tế có thể triển khai ngay.";
    }

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
            points.add(points.size() < fallback.length ? fallback[points.size()] : "Điểm cốt lõi từ tài liệu.");
        return points.toArray(new String[0]);
    }

    // ACTION TRIGGERS

    private boolean hasActionTriggers(String text) {
        if (text == null)
            return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("<actiontrigger") || lower.contains("class=\"action-trigger\"");
    }

    private String enrichWithActionTriggers(String aiText, String userMessage, List<Book> books) {
        if (aiText == null || aiText.isBlank() || hasActionTriggers(aiText))
            return aiText;
        Book specific = findBestBookFromQuery(books, userMessage);
        if (specific == null)
            specific = findSpecificBookInfoMatch(books, userMessage);
        if (specific != null) {
            StringBuilder sb = new StringBuilder(aiText);
            appendActionTriggersForBook(sb, specific);
            return sb.toString().trim();
        }
        List<Book> matched = findRelevantBooks(books, userMessage, true);
        if (!matched.isEmpty()) {
            StringBuilder sb = new StringBuilder(aiText);
            appendActionTriggersForList(sb, userMessage, matched);
            return sb.toString().trim();
        }
        return aiText;
    }

    private void appendActionTriggersForBook(StringBuilder sb, Book b) {
        if (b == null || b.getId() == null)
            return;
        sb.append("\n\n");
        sb.append(
                buildActionButton("view-detail", "book-detail", String.valueOf(b.getId()), null, "Xem chi tiết", "✅"));
        sb.append(" ");
        if (b.getQuantity() > 0) {
            sb.append(buildActionButton("order", "cart", String.valueOf(b.getId()), null, "Đặt mua ngay", "🛒"));
            sb.append(" ");
        }
        if (b.getAuthor() != null && !b.getAuthor().isBlank())
            sb.append(buildActionButton("search", "books", null, b.getAuthor(), "Sách cùng tác giả", "📖"));
        sb.append("\n");
    }

    private void appendActionTriggersForList(StringBuilder sb, String userMessage, List<Book> matched) {
        if (matched == null || matched.isEmpty())
            return;
        if (matched.size() == 1) {
            appendActionTriggersForBook(sb, matched.get(0));
            return;
        }
        sb.append("\n\n");
        sb.append(buildActionButton("navigate", "books", null, null, "Xem toàn bộ sách", "📚")).append(" ");
        String topic = String.join(" ", extractSearchKeywords(userMessage));
        if (!topic.isBlank()) {
            String safeTopic = topic.length() > 30 ? topic.substring(0, 30) : topic;
            sb.append(buildActionButton("search", "books", null, safeTopic, "Tìm \"" + safeTopic + "\"", "🔍"))
                    .append(" ");
        }
        Book first = matched.get(0);
        if (first != null && first.getId() != null) {
            String shortTitle = first.getTitle().length() > 25 ? first.getTitle().substring(0, 22) + "..."
                    : first.getTitle();
            sb.append(buildActionButton("view-detail", "book-detail", String.valueOf(first.getId()), null,
                    "Chi tiết: " + shortTitle, "✅"));
        }
        sb.append("\n");
    }

    private String buildActionButton(String type, String target, String id, String query, String label, String icon) {
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

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String escapeHtmlContent(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ORDERS

    private void appendUserOrders(StringBuilder sb, String username) {
        try {
            if (username == null || username.isBlank())
                return;
            List<Order> orders = orderRepo.findByUsername(username);
            if (orders.isEmpty()) {
                sb.append("[Dữ liệu hệ thống: Tài khoản chưa có đơn hàng.]\n");
                return;
            }
            sb.append("=== LỊCH SỬ ĐƠN HÀNG \"").append(username).append("\" (").append(orders.size())
                    .append(" bản ghi) ===\n");
            for (Order o : orders) {
                sb.append("* Đơn #").append(o.getId())
                        .append(" | Trạng thái: ").append(o.getStatus())
                        .append(" | Tổng: ").append(formatPrice(o.getTotalAmount()))
                        .append(" | Người nhận: ").append(o.getCustomerName())
                        .append(" | Ngày: ").append(o.getCreateDate() != null ? o.getCreateDate().format(FMT) : "—")
                        .append("\n");
            }
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi tải đơn hàng: " + e.getMessage());
        }
    }

    // PDF UTILITIES

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
        String preview = pdfReaderService.extractText(pdfPath);
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

    // BOOK LINE FORMATTERS

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

    private void saveMessage(String username, String role, String message, String sessionId) {
        ChatMessage msg = new ChatMessage();
        msg.setUsername(username != null ? username : "anonymous");
        msg.setRole(role != null ? role : "system");
        msg.setMessage(message != null ? message : "");
        msg.setSessionId(sessionId != null ? sessionId : UUID.randomUUID().toString());
        msg.setCreateDate(LocalDateTime.now());
        chatRepo.save(msg);
    }

    private String normalizeSearch(String text) {
        if (text == null)
            return "";
        String n = Normalizer.normalize(text, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String formatPrice(java.math.BigDecimal price) {
        if (price == null)
            return "Chưa có giá";
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(price) + "đ";
    }

    private String safeSubstring(String text, int maxLen) {
        if (text == null)
            return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private String trimSystemInstruction(String instruction) {
        if (instruction == null)
            return "";
        if (instruction.length() <= maxSystemChars)
            return instruction;
        return instruction.substring(0, maxSystemChars - 60) + "\n\n[Context rút gọn do giới hạn kỹ thuật.]";
    }

    private String extractTopicFromQuestion(String userMessage) {
        String n = normalizeSearch(userMessage);
        return Arrays.stream(n.split("\\s+"))
                .filter(token -> token.length() >= 2 && !STOP_WORDS.contains(token))
                .collect(Collectors.joining(" ")).trim();
    }

    private String toUserFriendlyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota"))
            return "⏳ Hệ thống AI tạm thời đạt ngưỡng giới hạn truy cập. Bạn có thể thử lại sau ít phút hoặc tra cứu thủ công nhé!";
        if (msg.contains("API key") || msg.contains("401") || msg.contains("403"))
            return "🔑 Sự cố xác thực: Khóa API Gemini không chính xác hoặc hết hạn.";
        return "⚠️ Bộ máy xử lý gặp xung đột kỹ thuật nhỏ. Vui lòng thử lại sau giây lát!";
    }

    // Alias Map để tránh import java.util.HashMap bị thiếu
    private static <K, V> Map<K, V> new_HashMap() {
        return new java.util.HashMap<>();
    }

    @SuppressWarnings("rawtypes")
    private static Map HashMap() {
        return new java.util.HashMap();
    }
}