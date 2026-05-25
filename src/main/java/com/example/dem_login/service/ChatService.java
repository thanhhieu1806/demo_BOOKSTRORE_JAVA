package com.example.dem_login.service;

import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class ChatService {

    // Repositories
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

    // Hằng số
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Stop words tiếng Việt */
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

    // System Prompts định hình Nhân cách & Định dạng chuẩn Gemini

    /**
     * Prompt chính: Nhân cách Gemini + quy tắc cấu trúc Scannable trực quan.
     */
    private static final String BASE_INSTRUCTION = """
            Bạn là Gemini (hỗ trợ trong vai trò trợ lý BookBot) — cộng sự AI thông minh, linh hoạt, hóm hỉnh đúng lúc.
            Mục tiêu: câu trả lời SÚCTÍCH, đi thẳng bản chất, CỰC KỲ dễ đọc lướt, định hướng hành động cao.
            Xưng "mình", gọi người dùng là "bạn".

            ═══ NGUYÊN TẮC (Chuẩn Gemini) ═══
            • Thấu hiểu & thẳng thắn: nếu dữ liệu không tồn tại, đính chính nhẹ nhàng như người bạn tin cậy — không giáo điều, không cứng nhắc.
            • Điều chỉnh tông giọng đồng điệu với phong cách của người dùng (trẻ trung / chuyên nghiệp / tò mò...).
            • Câu trả lời PHẢI có cấu trúc section rõ ràng — tuyệt đối KHÔNG viết "wall of text" dài liền mạch.

            ═══ BỘ QUY TẮC ĐỊNH DẠNG BẮT BUỘC (Scannable Gemini Layout) ═══

            [R1] CÂU MỞ ĐẦU — Đúng 1 câu, tóm tắt điều bạn sắp trình bày. Ví dụ:
                 "Dưới đây là thông tin chi tiết về **{Tên Sách}** theo dữ liệu cửa hàng:"

            [R2] SECTION HEADER — Dùng ## để phân vùng logic, ### cho tiểu mục. Các section bắt buộc với sách:
                 ## Thông tin chung
                 ## Giá bán & Tình trạng kho
                 ## Giới thiệu tổng quan       ← nếu có mô tả
                 ## Dữ liệu trích yếu (PDF)    ← nếu có nội dung PDF

            [R3] ĐƯỜNG PHÂN CÁCH --- Đặt trước MỖI section để tạo khoảng thở thị giác.

            [R4] BULLET POINT * — Liệt kê thuộc tính theo mẫu:
                 * **Nhãn:** Nội dung giá trị
                 Ví dụ:
                 * **Tên tác phẩm:** Spring Boot Thực Chiến
                 * **Tác giả:** Hoàng Văn E
                 * **Danh mục:** Lập trình
                 * **Giá ưu đãi hiện tại:** **220.000đ**
                 * **Tình trạng khả dụng:** Còn khả dụng **11** cuốn trong kho

            [R5] DANH SÁCH SỐ 1. 2. 3. — Dùng cho ranking/thứ tự ưu tiên, kèm thụt lề `-` cho chi tiết con:
                 1. **Spring Boot Thực Chiến** — Giá: 220.000đ
                    - Tác giả: Hoàng Văn E
                    - Còn: 11 cuốn

            [R6] BÔI ĐẬM **...** — Nhấn mạnh: tên sách, giá, số lượng, thuật ngữ cốt lõi. Dùng ĐỦ LIỀU, không lạm dụng.

            [R7] BLOCKQUOTE > — Dùng cho lưu ý quan trọng, tip, cảnh báo hữu ích.
                 Ví dụ: > 💡 **Mẹo:** Nếu bạn muốn đọc thử nội dung, mình có thể trích xuất PDF của cuốn này ngay!

            [R8] KẾT THÚC SECTION — Nếu câu trả lời liên quan đến sách cụ thể, LUÔN kết thúc bằng:
                 > 📌 Bạn muốn làm gì tiếp theo? Nhấn chọn bên dưới nhé 👇

            ═══ VÍ DỤ HOÀN CHỈNH (THAM KHẢO BẮT BUỘC) ═══

            Dưới đây là thông tin chi tiết về cuốn sách **Spring Boot Thực Chiến** theo hệ thống dữ liệu cửa hàng:

            ---

            ## Thông tin chung

            * **Tên tác phẩm:** Spring Boot Thực Chiến
            * **Tác giả:** Hoàng Văn E
            * **Danh mục:** Lập trình

            ---

            ## Giá bán & Tình trạng kho

            * **Giá ưu đãi hiện tại:** **220.000đ**
            * **Tình trạng khả dụng:** Còn khả dụng **11** cuốn trong kho

            ---

            ## Giới thiệu tổng quan

            Cuốn sách hướng dẫn xây dựng ứng dụng Spring Boot từ cơ bản đến nâng cao, phù hợp cho lập trình viên Java muốn làm chủ framework phổ biến nhất hiện nay.

            > 💡 **Mẹo:** Nếu bạn cần trích xuất nội dung học thuật bên trong, cứ bảo mình đọc file PDF của cuốn này nhé!

            ═══ QUY TẮC NỘI DUNG ═══
            1. CHỈ dùng dữ liệu THỰC TẾ từ Context (ID, tên, giá, đoạn PDF). KHÔNG bịa đặt hay suy diễn ngoài ngữ cảnh.
            2. Tiền tệ chuẩn Việt Nam: 150.000đ (không dùng "150000 VND" hay "VNĐ").
            3. Nếu không tìm thấy sách → gợi ý từ khóa khác + hiện nút điều hướng.
            4. Tuyệt đối KHÔNG trả lời dưới dạng đoạn văn thuần túy khi có dữ liệu cấu trúc.

            ACTION TRIGGER (Đặt cuối câu trả lời, mỗi thẻ một dòng riêng):
            Dẫn dắt: "Bạn muốn làm gì tiếp theo? Hãy nhấn chọn các lối tắt bên dưới nhé 👇"
            <ActionTrigger type="view-detail" target="book-detail" id="[ID]">✅ Có, xem trang chi tiết</ActionTrigger>
            <ActionTrigger type="order" target="cart" id="[ID]">🛒 Đặt mua ngay</ActionTrigger>
            """;

    /** Prompt cho chế độ phân tích tài liệu chuyên sâu */
    private static final String PDF_ANALYSIS_INSTRUCTION = """
            Bạn là Gemini — chuyên gia phân tích tài liệu chuyên sâu. Nhiệm vụ: bóc tách nội dung PDF và trình bày khoa học, súc tích, có tính scannable tối đa.
            Xưng "mình", gọi "bạn".

            ═══ CẤU TRÚC PHẢN HỒI BẮT BUỘC ═══

            [B1] CÂU MỞ ĐẦU (1 câu): Xác nhận câu trả lời dựa hoàn toàn trên nội dung tài liệu.

            [B2] NẾU TÀI LIỆU LÀ HỒ SƠ / CV / KẾ HOẠCH CÁ NHÂN — dùng khung sau:
            ## Thông tin chung
            * **Họ và tên:** ...
            * **Mã số / ID:** ...
            * **Đơn vị / Trường:** ...
            * **Chuyên ngành:** ...

            ---

            ## [Nội dung chính — tiêu đề theo nội dung thực tế của tài liệu]
            1. **[Mục lớn thứ nhất]**
               - Chi tiết con 1
               - Chi tiết con 2
            2. **[Mục lớn thứ hai]**
               - ...

            ---

            ## Tóm tắt cốt lõi
            * [Luận điểm quan trọng nhất 1]
            * [Luận điểm quan trọng nhất 2]
            * [Luận điểm quan trọng nhất 3]

            [B3] NẾU TÀI LIỆU LÀ SÁCH / TÀI LIỆU HỌC THUẬT — dùng khung:
            ## Luận điểm chính
            ## Kiến thức kỹ thuật / Phương pháp
            ## Ứng dụng thực tế
            ## Tóm tắt cốt lõi

            [B4] Dùng `---` phân tách các section lớn.
            [B5] Dùng `* **Nhãn:** Nội dung` cho các thuộc tính cụ thể.
            [B6] Dùng `1. **Tiêu đề**` + thụt lề `   -` cho nội dung có thứ tự/giai đoạn.

            ═══ QUY TẮC BẮT BUỘC ═══
            • LOẠI BỎ thông tin rác: bản quyền, NXB, ISBN, địa chỉ, email liên hệ, Amazon MOQ.
            • CHỈ phân tích kiến thức học thuật / nghiệp vụ chính.
            • Nếu PDF không có nội dung phù hợp → giải thích trực tiếp dựa trên đoạn trích thực tế.
            • KHÔNG suy diễn ngoài văn bản gốc.
            • Cuối câu trả lời: thêm dòng > ℹ️ *Phân tích trích xuất từ tài liệu đính kèm. Kiểm tra lại bản gốc nếu cần độ chính xác tuyệt đối.*
            """;

    // Constructor
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

            String effectiveMessage = req.getMessage();
            if (isFollowUpQuestion(req.getMessage())) {
                String histCtx = extractTopicFromHistory(history, 6);
                if (!histCtx.isBlank()) {
                    effectiveMessage = histCtx + " " + req.getMessage();
                }
            }

            String systemInstruction = buildSystemInstruction(
                    req.getUsername(),
                    req.getMessage(),
                    effectiveMessage,
                    history,
                    allBooks);

            String payload = buildGeminiPayload(history, req.getMessage(), systemInstruction);
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
                return new Dto.ChatResponse(false, "Không tìm thấy file PDF trên server. Kiểm tra pdf_path: " + pdfPath,
                        null);

            String pdfContent = pdfReaderService.extractText(pdfPath);
            if (pdfContent == null || pdfContent.startsWith("Không đọc được"))
                return new Dto.ChatResponse(false,
                        "File PDF tồn tại nhưng không đọc được nội dung (quét ảnh hoặc mã hóa).", null);

            String cleanedPdf = filterBoilerplateFromPdf(pdfContent);
            String sourceText = cleanedPdf.isBlank() ? pdfContent : cleanedPdf;
            int maxPdfChars = Math.max(4000, maxSystemChars - PDF_ANALYSIS_INSTRUCTION.length() - 800);
            String pdfExcerpt = safeSubstring(sourceText, Math.min(maxPdfChars, 12000));
            String systemPrompt = trimSystemInstruction(
                    PDF_ANALYSIS_INSTRUCTION + "\n=== NỘI DUNG PDF ===\n" + pdfExcerpt);
            String payload = buildSimplePayload(systemPrompt, question);

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

    private boolean isFollowUpQuestion(String msg) {
        if (msg == null || msg.isBlank())
            return false;
        String n = normalizeSearch(msg);
        boolean hasFollowUpKeyword = n.matches(
                ".*(con.*nao|con gi|cuon khac|sach khac|"
                        + "cai khac|loai khac|them cai|them cuon|"
                        + "the con|vay con|con nua|gi nua|"
                        + "cuon do|sach do|no thi|cai do|"
                        + "the thi|vay thi|mua di|dat hang di|"
                        + "bao nhieu nua|gia nua|tac gia nua).*");
        boolean veryShort = msg.trim().split("\\s+").length <= 6;
        return hasFollowUpKeyword || veryShort;
    }

    private String extractTopicFromHistory(List<ChatMessage> history, int recentN) {
        if (history == null || history.size() < 2)
            return "";
        int end = history.size() - 1;
        int start = Math.max(0, end - recentN);
        StringBuilder ctx = new StringBuilder();
        for (int i = start; i < end; i++) {
            String content = history.get(i).getMessage();
            if (content != null && !content.isBlank()) {
                ctx.append(content).append(" ");
            }
        }
        return ctx.toString().trim();
    }

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
                ".*(gia cao nhat|dat nhat|mac nhat|gia thap nhat|re nhat|gia re nhat|sap xep.*gia|gia.*sap xep|giastienf|gia tien cao|gia tien thap|cuon nao dat|cuon nao mac|cuon nao re|sach nao dat|sach nao mac|sach nao re|dat hon|mac hon|re hon|gia bao nhieu nhat|so sanh gia).*"))
            return ChatIntent.PRICE_SORT;

        Book bookHint = findBestBookFromQuery(allBooks, msg);
        if (bookHint != null && (isSpecificBookInfoQuestion(msg, allBooks)
                || n.matches(".*(gia|bao nhieu|ton kho|tac gia|thong tin|chi tiet|mo ta).*")))
            return ChatIntent.SPECIFIC_BOOK;

        if (isCatalogQuestion(n))
            return ChatIntent.CATALOG_SEARCH;

        if (isContentQuestion(n))
            return ChatIntent.CONTENT_QUESTION;

        if (n.matches(".*(gia|bao nhieu tien|bao nhieu d|bao nhieu dong).*"))
            return ChatIntent.PRICE_INFO;

        return ChatIntent.GENERAL;
    }

    private String buildSystemInstruction(String username, String userMessage, String effectiveMessage,
            List<ChatMessage> history, List<Book> allBooks) {
        StringBuilder sb = new StringBuilder(BASE_INSTRUCTION);
        sb.append("\nThời gian hệ thống hiện tại: ").append(LocalDateTime.now().format(FMT)).append("\n");

        if (!effectiveMessage.equals(userMessage)) {
            sb.append("\n[BỔ SUNG CONTEXT LỊCH SỬ CHAT ĐỂ ĐỌC HIỂU NGỮ CẢNH]\n");
        }

        ChatIntent intent = detectIntent(effectiveMessage, allBooks);
        sb.append("\n[HỆ THỐNG PHÂN LOẠI INTENT: ").append(intent).append("]\n");

        try {
            switch (intent) {
                case ORDER -> appendUserOrders(sb, username);
                case PRICE_SORT -> handlePriceSort(sb, allBooks, effectiveMessage);
                case SPECIFIC_BOOK -> handleSpecificBook(sb, allBooks, effectiveMessage);
                case CONTENT_QUESTION -> handleContentQuestion(sb, allBooks, effectiveMessage);
                case PRICE_INFO -> handlePriceInfo(sb, allBooks, effectiveMessage);
                case CATALOG_SEARCH -> handleCatalogSearch(sb, allBooks, effectiveMessage);
                default -> handleGeneralQuery(sb, allBooks, effectiveMessage);
            }

            if (intent != ChatIntent.ORDER && username != null && !username.isBlank()
                    && normalizeSearch(userMessage).contains("don")) {
                appendUserOrders(sb, username);
            }
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi dựng SystemInstruction: " + e.getMessage());
        }

        return trimSystemInstruction(sb.toString());
    }

    /** Cắt system prompt để tránh vượt giới hạn token Gemini */
    private String trimSystemInstruction(String instruction) {
        if (instruction == null)
            return "";
        if (instruction.length() <= maxSystemChars)
            return instruction;
        return instruction.substring(0, maxSystemChars - 60)
                + "\n\n[Context rút gọn do giới hạn kỹ thuật.]";
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
            sb.append("\n[Không tìm thấy sách phù hợp để xếp hạng giá.]\n");
            return;
        }

        sb.append("\n\n=== DANH SÁCH SÁCH ĐÃ SẮP XẾP THEO GIÁ (").append(descending ? "ĐẮT → RẺ" : "RẺ → ĐẮT")
                .append(") ===\n");
        int rank = 1;
        for (Book b : sorted) {
            sb.append(rank++).append(". \"").append(b.getTitle()).append("\" — Giá: ").append(formatPrice(b.getPrice()))
                    .append(" | Kho: ").append(b.getQuantity()).append(" cuốn\n");
        }

        sb.append(
                "\n[HƯỚNG DẪN AI TRẢ LỜI: Trình bày cấu trúc chuẩn Gemini. Cung cấp câu mở đầu, dùng phần mục ## Bảng xếp hạng giá, phân tách bằng đường kẻ `---`, danh sách chấm hoặc đánh số. Làm nổi bật cuốn vị trí số 1.]\n");
    }

    private void handleSpecificBook(StringBuilder sb, List<Book> allBooks, String userMessage) {
        Book book = findSpecificBookInfoMatch(allBooks, userMessage);
        if (book == null) {
            book = findBestBookFromQuery(allBooks, userMessage);
        }
        if (book == null) {
            handleCatalogSearch(sb, allBooks, userMessage);
            return;
        }
        String n = normalizeSearch(userMessage);
        boolean askPrice = n.matches(".*(gia|bao nhieu|bao tien).*");
        boolean askAuthor = n.matches(".*(tac gia|nguoi viet|ai viet|ai lam).*");
        boolean askStock = n.matches(".*(con hang|ton kho|con may|bao nhieu cuon).*");
        boolean askContent = isContentQuestion(n);

        sb.append("\n=== NGỮ CẢNH DỮ LIỆU SÁCH CỤ THỂ ===\n");
        sb.append("Tên: \"").append(book.getTitle()).append("\"\n");
        if (askPrice || !askAuthor && !askStock && !askContent) {
            sb.append("Giá: ").append(formatPrice(book.getPrice())).append("\n");
        }
        if (askAuthor || !askPrice && !askStock && !askContent) {
            sb.append("Tác giả: ").append(book.getAuthor() != null ? book.getAuthor() : "Chưa rõ").append("\n");
        }
        if (askStock) {
            sb.append("Tồn kho: ").append(book.getQuantity()).append(" cuốn\n");
        }
        if (!askPrice && !askAuthor && !askStock) {
            appendBookDetail(sb, book);
        }

        if (askContent) {
            if (book.getPdfPath() != null && !book.getPdfPath().isBlank()
                    && pdfReaderService.isReadable(book.getPdfPath())) {
                appendPdfExcerpts(sb, List.of(book), userMessage);
            } else {
                sb.append(
                        "[Lưu ý hệ thống: Tác phẩm này chưa có file PDF đính kèm. Trả lời thuần túy và trung thực dựa trên trường dữ liệu Mô tả].\n");
            }
        }
        sb.append(
                "\n[HƯỚNG DẪN AI TRẢ LỜI: Định dạng chuẩn Gemini. Dùng ##, mục rõ ràng, phân vùng bằng dòng kẻ `---`, bullet points dạng hoa thị `*`. Đi trực tiếp vào trọng tâm câu hỏi].\n");
    }

    private void handleContentQuestion(StringBuilder sb, List<Book> allBooks, String userMessage) {
        List<Book> candidates = findBooksForContentQuestion(allBooks, userMessage);
        if (!candidates.isEmpty()) {
            boolean hasPdf = appendPdfExcerpts(sb, candidates, userMessage);
            if (!hasPdf) {
                sb.append("\n\n=== THÔNG TIN TÓM TẮT METADATA SÁCH LIÊN QUAN ===\n");
                candidates.stream().limit(3).forEach(b -> {
                    appendBookDetail(sb, b);
                    sb.append("\n");
                });
                sb.append(
                        "[Lưu ý: Hệ thống chưa có file PDF nội dung chi tiết. Phân tích dựa trên phần MÔ TẢ ở trên. Nếu phần mô tả trống, hãy thẳng thắn thông báo cho người dùng biết].\n");
            }
        } else {
            boolean foundInPdf = appendPdfExcerptsFromAllBooks(sb, allBooks, userMessage);
            if (!foundInPdf) {
                sb.append(
                        "\n[HƯỚNG DẪN AI: Không phát hiện dữ liệu liên quan trong kho. Hãy thẳng thắn phản hồi với tinh thần của một cộng sự chân thực, đề xuất người dùng cung cấp chính xác tên sách cụ thể].\n");
            }
        }
    }

    private void handlePriceInfo(StringBuilder sb, List<Book> allBooks, String userMessage) {
        Book one = findBestBookFromQuery(allBooks, userMessage);
        if (one != null) {
            handleSpecificBook(sb, allBooks, userMessage);
            return;
        }
        List<Book> relevant = findRelevantBooks(allBooks, userMessage, true);
        List<Book> toShow = relevant.stream().filter(b -> b.getPrice() != null).limit(10).collect(Collectors.toList());

        if (toShow.isEmpty()) {
            sb.append("\n[Hệ thống chưa ghi nhận thông tin bảng giá cho danh mục này.]\n");
            return;
        }
        sb.append("\n\n=== BẢNG BÁO GIÁ THAM KHẢO ===\n");
        for (Book b : toShow) {
            sb.append("* \"").append(b.getTitle()).append("\" | Giá niêm yết: ").append(formatPrice(b.getPrice()))
                    .append(" | Kho: ").append(b.getQuantity()).append("\n");
        }
        sb.append(
                "\n[HƯỚNG DẪN AI: Định dạng Gemini — Tiêu đề mục `## Bảng giá sách`, dùng dòng kẻ `---` và danh sách dấu chấm `*`].\n");
    }

    private void handleCatalogSearch(StringBuilder sb, List<Book> allBooks, String userMessage) {
        List<Book> relevant = findRelevantBooks(allBooks, userMessage, true);
        if (relevant.isEmpty()) {
            sb.append(
                    "\n[Hệ thống không tìm thấy kết quả nào khớp từ khóa. Hãy hướng dẫn người dùng tìm bằng từ khóa khái quát hơn].\n");
            return;
        }

        sb.append("\n\n=== KẾT QUẢ TRA CỨU DANH MỤC SÁCH (").append(relevant.size()).append(" dòng) ===\n");
        relevant.forEach(b -> appendBookLine(sb, b));
        sb.append(
                "\n[HƯỚNG DẪN AI: Định dạng Gemini — Gợi ý trực quan theo dạng danh sách, cấu trúc rõ ràng với tiêu đề `## Kết quả tra cứu` và ngăn cách bằng đường kẻ `---`].\n");
    }

    private void handleGeneralQuery(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String n = normalizeSearch(userMessage);
        if (n.contains("sach") || n.contains("doc") || n.contains("goi y")) {
            List<Book> relevant = findRelevantBooks(allBooks, userMessage, false);
            if (!relevant.isEmpty()) {
                sb.append("\n=== DANH SÁCH GỢI Ý TÁC PHẨM TIÊU BIỂU ===\n");
                relevant.stream().limit(5).forEach(b -> appendBookLine(sb, b));
            }
        }
    }

    private void appendUserOrders(StringBuilder sb, String username) {
        try {
            if (username == null || username.isBlank())
                return;
            List<Order> orders = orderRepo.findByUsername(username);
            if (orders.isEmpty()) {
                sb.append("\n[Dữ liệu hệ thống: Tài khoản người dùng chưa thực hiện đơn hàng nào.]\n");
                return;
            }
            sb.append("\n=== LỊCH SỬ ĐƠN HÀNG CỦA TÀI KHOẢN \"").append(username).append("\" (").append(orders.size())
                    .append(" bản ghi) ===\n");
            for (Order o : orders) {
                sb.append("* Đơn mã #").append(o.getId())
                        .append(" | Trạng thái xử lý: ").append(o.getStatus())
                        .append(" | Tổng giá trị: ").append(formatPrice(o.getTotalAmount()))
                        .append(" | Người nhận: ").append(o.getCustomerName())
                        .append(" | Ngày lập đơn: ")
                        .append(o.getCreateDate() != null ? o.getCreateDate().format(FMT) : "Chưa cập nhật")
                        .append("\n");
            }
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi tải dữ liệu đơn hàng: " + e.getMessage());
        }
    }

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
        for (String kw : contentKeywords) {
            if (normalizedMsg.contains(kw))
                return true;
        }

        if (normalizedMsg.matches(".*(la ai|la gi|la cai gi|nghia la gi).*"))
            return true;
        if (isCatalogQuestion(normalizedMsg))
            return false;

        if (normalizedMsg.contains("?")) {
            boolean isMetadataQuestion = normalizedMsg
                    .matches(".*(gia|bao nhieu|con hang|con may|ton kho|tac gia|nguoi viet|xuat ban).*");
            return !isMetadataQuestion;
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

        boolean asksForInfo = n.matches(
                ".*\\b(thong tin|chi tiet|mo ta|gioi thieu|gia|bao nhieu|con hang|ton kho|tac gia|nguoi viet|xuat ban)\\b.*");
        boolean mentionsBook = n.matches(".*\\b(sach|cuon|book)\\b.*");
        return asksForInfo && mentionsBook;
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
            Book exact = allBooks.stream().filter(b -> normalizeSearch(b.getTitle()).equals(nt)).findFirst()
                    .orElse(null);
            if (exact != null)
                return exact;
            return allBooks.stream()
                    .filter(b -> b.getTitle().toLowerCase(Locale.ROOT).contains(extracted.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
        }
        List<Book> matched = findRelevantBooks(allBooks, userMessage, true);
        return matched.size() == 1 ? matched.get(0) : null;
    }

    private List<Book> findBooksForContentQuestion(List<Book> allBooks, String userMessage) {
        List<String> keywords = extractSearchKeywords(userMessage);
        List<Book> byMetadata = allBooks.stream()
                .map(b -> Map.entry(b, scoreBookMatch(b, keywords, userMessage)))
                .filter(e -> e.getValue() >= 20)
                .sorted(Comparator.comparingInt((Map.Entry<Book, Integer> e) -> e.getValue()).reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Book> withPdf = byMetadata.stream().filter(
                b -> b.getPdfPath() != null && !b.getPdfPath().isBlank() && pdfReaderService.isReadable(b.getPdfPath()))
                .collect(Collectors.toList());
        List<Book> withoutPdf = byMetadata.stream().filter(
                b -> b.getPdfPath() == null || b.getPdfPath().isBlank() || !pdfReaderService.isReadable(b.getPdfPath()))
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

        return books.stream()
                .map(b -> Map.entry(b, scoreBookMatch(b, keywords, q)))
                .filter(e -> e.getValue() >= 20)
                .sorted(Comparator.comparingInt((Map.Entry<Book, Integer> e) -> e.getValue()).reversed())
                .map(Map.Entry::getKey)
                .limit(maxBooksInPrompt)
                .collect(Collectors.toList());
    }

    private List<String> extractSearchKeywords(String userMessage) {
        if (userMessage == null || userMessage.isBlank())
            return List.of();
        return Arrays.stream(normalizeSearch(userMessage).split("\\s+"))
                .filter(t -> t.length() >= 2)
                .filter(t -> !STOP_WORDS.contains(t))
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

    private boolean isGenericBookQuery(String q) {
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

    private boolean appendPdfExcerpts(StringBuilder sb, List<Book> books, String userMessage) {
        List<Book> sorted = books.stream()
                .sorted(Comparator.comparingInt(b -> (b.getPdfPath() != null && !b.getPdfPath().isBlank()
                        && pdfReaderService.isReadable(b.getPdfPath())) ? 0 : 1))
                .collect(Collectors.toList());

        int totalCharsAdded = 0;
        final int MAX_TOTAL = 12000;
        final int MAX_PER = 6000;
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
                System.err.println("[ChatService] Lỗi trích văn bản PDF bookId=" + b.getId() + ": " + e.getMessage());
                continue;
            }

            if (pdfText == null || pdfText.isBlank() || pdfText.startsWith("Không đọc được"))
                continue;

            int allowed = Math.min(MAX_PER, MAX_TOTAL - totalCharsAdded);
            String snippet = extractRelevantSnippet(pdfText, userMessage, allowed);
            if (snippet.isBlank())
                continue;

            String label = (b.getPdfName() != null && !b.getPdfName().isBlank()) ? b.getPdfName() : b.getTitle();
            sb.append("\n\n=== TRÍCH ĐOẠN NỘI DUNG TÀI LIỆU PDF (\"").append(b.getTitle()).append("\" | File: ")
                    .append(label).append(") ===\n").append(snippet).append("\n");

            totalCharsAdded += snippet.length();
            added++;
        }
        return added > 0;
    }

    private boolean appendPdfExcerptsFromAllBooks(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String nq = normalizeSearch(userMessage);
        String[] queryTokens = Arrays.stream(nq.split("\\s+")).filter(t -> t.length() >= 4 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);

        if (queryTokens.length == 0)
            return false;

        int minMatch = Math.min(2, (int) Math.ceil(queryTokens.length / 2.0));
        List<Book> matchingBooks = new ArrayList<>();

        for (Book b : allBooks) {
            String path = b.getPdfPath();
            if (path == null || path.isBlank() || !pdfReaderService.isReadable(path))
                continue;

            try {
                String pdfText = pdfReaderService.extractText(path);
                if (pdfText == null || pdfText.startsWith("Không đọc được"))
                    continue;

                String cleaned = filterBoilerplateFromPdf(pdfText);
                String np = normalizeSearch(cleaned.isBlank() ? pdfText : cleaned);
                int count = 0;
                for (String token : queryTokens) {
                    if (np.contains(token))
                        count++;
                }
                if (count >= minMatch)
                    matchingBooks.add(b);
            } catch (Exception e) {
                System.err.println("[ChatService] Thất bại khi quét PDF bookId=" + b.getId() + ": " + e.getMessage());
            }

            if (matchingBooks.size() >= 2)
                break;
        }

        if (matchingBooks.isEmpty())
            return false;
        return appendPdfExcerpts(sb, matchingBooks, userMessage);
    }

    private boolean isBoilerplateSentence(String normalizedSentence) {
        return normalizedSentence.matches(
                ".*(nha xuat ban|nxb|ban quyen|all rights reserved|copyright|isbn|tru so chinh|chi nhanh|so dien thoai|tel|fax|email|website|http|www\\.|in lan|in lan thu|tai ban|tai ban lan|xuat ban lan|fulfillment|amazon|moq|minimum order|muc dat hang toi thieu|lien he|hop dong|giay phep|so giay phep|gkxb|gia tien viet nam|in tai|quan 1|quan 3|ha noi|ho chi minh|marketing@|info@|support@|sales@|publisher).*");
    }

    private String filterBoilerplateFromPdf(String rawPdfText) {
        if (rawPdfText == null || rawPdfText.isBlank())
            return "";
        String[] lines = rawPdfText.split("\\n");
        StringBuilder filtered = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() < 4)
                continue;
            String norm = normalizeSearch(trimmed);
            if (!isBoilerplateSentence(norm)) {
                filtered.append(line).append("\n");
            }
        }
        return filtered.toString().trim();
    }

    private String extractRelevantSnippet(String pdfText, String question, int maxLen) {
        if (pdfText == null || pdfText.isBlank())
            return "";

        String[] sentences = pdfText.split("(?<=[.!?\\n])\\s+");
        String nq = normalizeSearch(question);
        String[] queryTokens = Arrays.stream(nq.split("\\s+")).filter(t -> t.length() >= 3 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);

        record ScoredSentence(String text, int score) {
        }
        List<ScoredSentence> scored = new ArrayList<>();

        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.length() < 15)
                continue;

            String ns = normalizeSearch(trimmed);
            if (isBoilerplateSentence(ns))
                continue;

            int score = 0;
            if (nq.length() > 4 && ns.contains(nq))
                score += 20;
            for (String token : queryTokens) {
                if (ns.contains(token))
                    score += token.length() >= 5 ? 5 : 2;
            }
            if (score > 0)
                scored.add(new ScoredSentence(trimmed, score));
        }

        scored.sort(Comparator.comparingInt(ScoredSentence::score).reversed());

        StringBuilder result = new StringBuilder();
        for (ScoredSentence ss : scored) {
            if (result.length() + ss.text().length() > maxLen)
                break;
            result.append(ss.text()).append(" ");
        }

        return safeSubstring(result.toString().trim(), maxLen);
    }

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
            result.put("message", "Tệp PDF không tồn tại trên vùng lưu trữ vật lý của máy chủ.");
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
                sb.append("Dưới đây là danh sách sách được sắp xếp theo hệ thống giá ")
                        .append(desc ? "(Từ cao đến thấp)" : "(Từ thấp đến cao)")
                        .append(":\n\n---\n\n## Kết quả phân loại\n");
                int i = 1;
                for (Book b : sorted) {
                    sb.append(i++).append(". **").append(b.getTitle()).append("** — Gia: **")
                            .append(formatPrice(b.getPrice())).append("** | Kho còn: ").append(b.getQuantity())
                            .append(" bản\n");
                }
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
                    if (!hasDesc) {
                        answer += "\n\n> ⚠️ *Tác phẩm hiện chưa được cập nhật file đính kèm lẫn mô tả. Hãy thử tra cứu từ khóa khác nhé!*";
                    }
                    return answer;
                }
                return "Mình chưa tìm thấy phân đoạn tri thức phù hợp. Hãy chỉ định tên cuốn sách cụ thể nhé! 📚";
            }

            Book single = findBestBookFromQuery(books, userMessage);
            if (single != null && isSpecificBookInfoQuestion(userMessage, books))
                return formatDetailedBookAnswer(single, userMessage, null, false);

            List<Book> matched = findRelevantBooks(books, userMessage, true);
            if (matched.isEmpty()) {
                String topic = String.join(" ", extractSearchKeywords(userMessage));
                String searchTag = topic.isBlank() ? ""
                        : "<ActionTrigger type=\"search\" target=\"books\" query=\"" + topic.replace("\"", "'")
                                + "\">🔍 Tìm kiếm \"" + topic + "\"</ActionTrigger>\n";
                return ("Thật lòng mà nói, mình chưa tìm thấy cuốn sách nào khớp hoàn toàn với mô tả của bạn.\n\nBạn có thể thử gõ tên cụ thể hoặc chuyên ngành hẹp (ví dụ: *Lập trình Java*, *Tâm lý học*) để mình quét lại kho dữ liệu nhé.\n\n---\n**Gợi ý bước tiếp theo:** 👇\n<ActionTrigger type=\"navigate\" target=\"books\">📚 Xem toàn bộ kệ sách</ActionTrigger>\n"
                        + searchTag).trim();
            }
            if (matched.size() == 1)
                return formatDetailedBookAnswer(matched.get(0), userMessage, null, false);
            return formatBookListAnswer(userMessage, matched);

        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi xử lý buildSmartLocalAnswer fallback: " + e.getMessage());
            return null;
        }
    }

    private void appendBookLine(StringBuilder sb, Book b) {
        sb.append("* \"").append(b.getTitle()).append("\"")
                .append(" | ID: ").append(b.getId())
                .append(" | Tác giả: ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ")
                .append(" | Phân loại: ").append(b.getCategory() != null ? b.getCategory() : "—")
                .append(" | Giá niêm yết: ").append(formatPrice(b.getPrice()))
                .append(" | Kho: ").append(b.getQuantity()).append(" cuốn")
                .append(b.getPdfPath() != null && !b.getPdfPath().isBlank() ? " | 📄 Có bản đọc thử PDF" : "")
                .append("\n");
    }

    private void appendBookDetail(StringBuilder sb, Book b) {
        sb.append("Mã định danh (ID): ").append(b.getId()).append("\n")
                .append("Tên tác phẩm: \"").append(b.getTitle()).append("\"\n")
                .append("Tác giả chính: ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ").append("\n")
                .append("Thể loại: ").append(b.getCategory() != null ? b.getCategory() : "—").append("\n")
                .append("Giá niêm yết: ").append(formatPrice(b.getPrice())).append("\n")
                .append("Số lượng khả dụng: ").append(b.getQuantity()).append(" cuốn\n");
        if (b.getDescription() != null && !b.getDescription().isBlank())
            sb.append("Tóm tắt nội dung: ").append(b.getDescription().trim()).append("\n");
        if (b.getPdfPath() != null && !b.getPdfPath().isBlank())
            sb.append("📄 Hệ thống đã đồng bộ file đọc thử PDF.\n");
    }

    private String formatDetailedBookAnswer(Book b, String userMessage, String pdfText, boolean hasMore) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dưới đây là thông tin chi tiết về cuốn sách **").append(b.getTitle())
                .append("** dựa trên hệ thống dữ liệu cửa hàng:\n\n");
        sb.append("---\n\n");
        sb.append("## Thông tin chung\n\n");
        sb.append("* **Tên tác phẩm:** ").append(b.getTitle()).append("\n");
        sb.append("* **Tác giả:** ").append(b.getAuthor() != null ? b.getAuthor() : "Đang cập nhật thêm").append("\n");
        sb.append("* **Danh mục:** ").append(b.getCategory() != null ? b.getCategory() : "—").append("\n\n");
        sb.append("---\n\n");
        sb.append("## Giá bán & Tình trạng kho\n\n");
        sb.append("* **Giá ưu đãi hiện tại:** **").append(formatPrice(b.getPrice())).append("**\n");
        sb.append("* **Tình trạng khả dụng:** ").append(
                b.getQuantity() > 0 ? "Còn khả dụng **" + b.getQuantity() + "** cuốn trong kho" : "**Tạm cháy hàng**")
                .append("\n");

        if (b.getDescription() != null && !b.getDescription().isBlank()) {
            sb.append("\n---\n\n## Giới thiệu tổng quan\n\n").append(b.getDescription().trim()).append("\n");
        }

        if (pdfText != null && !pdfText.isBlank()) {
            String snippet = extractRelevantSnippet(pdfText, userMessage, 1500);
            if (!snippet.isBlank()) {
                sb.append("\n---\n\n## Dữ liệu trích yếu (Từ nội dung PDF)\n\n").append(snippet).append("\n");
            }
        }

        if (hasMore) {
            sb.append(
                    "\n---\n\n## Gợi ý thêm\n\nKho hàng còn ghi nhận một số đầu sách tương tự thuộc nhóm ngành này, bạn có muốn thu hẹp phạm vi tìm kiếm không?\n");
        }

        appendActionTriggersForBook(sb, b);
        return sb.toString().trim();
    }

    private String formatBookListAnswer(String userMessage, List<Book> matched) {
        StringBuilder sb = new StringBuilder();
        String topic = String.join(" ", extractSearchKeywords(userMessage));
        if (topic.isBlank())
            topic = extractTopicFromQuestion(userMessage);

        sb.append("Mình đã rà soát và tìm thấy **").append(matched.size()).append("** cuốn sách");
        if (!topic.isBlank()) {
            sb.append(" thuộc nhóm chủ đề **\"").append(topic).append("\"**");
        }
        sb.append(" cực kỳ phù hợp với nhu cầu của bạn:\n\n");
        sb.append("---\n\n");
        sb.append("## Danh mục đề xuất hàng đầu\n\n");

        int i = 1;
        for (Book b : matched.stream().limit(5).collect(Collectors.toList())) {
            sb.append(i++).append(". **").append(b.getTitle()).append("**\n");
            sb.append("   - **Tác giả:** ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa cập nhật")
                    .append("\n");
            sb.append("   - **Chuyên mục:** ").append(b.getCategory() != null ? b.getCategory() : "—").append("\n");
            sb.append("   - **Giá bán giá:** **").append(formatPrice(b.getPrice())).append("**\n");
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

    private boolean hasActionTriggers(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains("<actiontrigger");
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
        sb.append("\n\n---\n");
        sb.append("**Bạn muốn làm gì tiếp theo?** Nhấn nút chọn bên dưới nhé 👇\n");
        sb.append("<ActionTrigger type=\"view-detail\" target=\"book-detail\" id=\"").append(b.getId())
                .append("\">✅ Có, xem trang chi tiết</ActionTrigger>\n");
        if (b.getQuantity() > 0) {
            sb.append("<ActionTrigger type=\"order\" target=\"cart\" id=\"").append(b.getId())
                    .append("\">🛒 Đặt mua ngay</ActionTrigger>\n");
        }
    }

    private void appendActionTriggersForList(StringBuilder sb, String userMessage, List<Book> matched) {
        if (matched == null || matched.isEmpty())
            return;
        if (matched.size() == 1) {
            appendActionTriggersForBook(sb, matched.get(0));
            return;
        }
        sb.append("\n\n---\n");
        sb.append("**Bạn muốn làm gì tiếp theo?** 👇\n");
        sb.append("<ActionTrigger type=\"navigate\" target=\"books\">📚 Xem toàn bộ sách</ActionTrigger>\n");
        String topic = String.join(" ", extractSearchKeywords(userMessage));
        if (!topic.isBlank()) {
            String safe = topic.replace("\"", "'");
            sb.append("<ActionTrigger type=\"search\" target=\"books\" query=\"").append(safe)
                    .append("\">🔍 Tìm tiếp \"").append(safe).append("\"</ActionTrigger>\n");
        }
        Book first = matched.get(0);
        if (first != null && first.getId() != null) {
            sb.append("<ActionTrigger type=\"view-detail\" target=\"book-detail\" id=\"").append(first.getId())
                    .append("\">✅ Xem chi tiết cuốn \"").append(first.getTitle()).append("\"</ActionTrigger>\n");
        }
    }

    private String extractTopicFromQuestion(String userMessage) {
        String n = normalizeSearch(userMessage);
        return Arrays.stream(n.split("\\s+")).filter(token -> token.length() >= 2 && !STOP_WORDS.contains(token))
                .collect(Collectors.joining(" ")).trim();
    }

    private String buildPdfLocalAnswer(String question, String pdfText, String pdfPath) {
        String nq = normalizeSearch(question != null ? question : "");
        boolean wantSummary = nq.matches(".*(tom tat|tong hop|summary|noi dung chinh|outline|muc luc).*");

        StringBuilder sb = new StringBuilder();
        sb.append("Dưới đây là ");
        sb.append(wantSummary ? "**bản tóm lược cốt lõi**" : "**phần phân hệ kiến thức liên quan**");
        sb.append(" bóc tách từ file tài liệu hệ thống");
        Book linked = findBookByPdfPath(pdfPath);
        if (linked != null)
            sb.append(" (*Cuốn: ").append(linked.getTitle()).append("*)");
        sb.append(":\n\n---\n\n");

        String snippet = extractRelevantSnippet(pdfText, question, wantSummary ? 6000 : 4000);
        if (snippet.isBlank())
            snippet = safeSubstring(pdfText, 3500);

        if (wantSummary) {
            sb.append("## Tóm tắt nội dung chính\n\n");
            sb.append(formatPdfSnippetAsBullets(snippet, 14));
        } else {
            sb.append("## Phân tích điểm liên quan\n\n");
            sb.append(formatPdfSnippetAsBullets(snippet, 10));
        }

        sb.append("\n---\n\n## Thông tin vận hành hệ thống\n\n");
        sb.append(
                "> ⚙️ **Thông báo:** Kênh truyền API Gemini hiện tại đang tạm gián đoạn do vượt quá hạn ngạch (Quota limit). Bản hiển thị trên được trích lọc tự động thông qua lõi phân tích cục bộ (Local Filter) của server.\n");

        if (linked != null)
            appendActionTriggersForBook(sb, linked);
        return sb.toString().trim();
    }

    private String formatPdfSnippetAsBullets(String snippet, int maxItems) {
        if (snippet == null || snippet.isBlank())
            return "* _(Hệ thống cục bộ không thể bóc tách phân đoạn phù hợp trong tệp PDF này.)_\n";

        String[] chunks = snippet.split("(?<=[.!?\\n])\\s+");
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (String chunk : chunks) {
            String t = chunk.trim();
            if (t.length() < 25)
                continue;
            if (t.length() > 320)
                t = t.substring(0, 317) + "...";
            boolean looksLikeHeading = t.length() < 80 && !t.contains(".");
            if (looksLikeHeading)
                out.append("* **").append(t).append("**\n");
            else
                out.append("* ").append(t).append("\n");
            if (++n >= maxItems)
                break;
        }
        if (n == 0)
            out.append("* ").append(safeSubstring(snippet.replace("\n", " "), 500)).append("\n");
        return out.toString();
    }

    private String callGeminiWithFallback(String payload) {
        List<String> models = new ArrayList<>();
        models.add(geminiModel);
        if (fallbackModels != null && !fallbackModels.isBlank()) {
            for (String m : fallbackModels.split(",")) {
                String t = m.trim();
                if (!t.isEmpty() && !models.contains(t))
                    models.add(t);
            }
        }

        RuntimeException lastError = null;
        for (String model : models) {
            try {
                return callGemini(model, payload);
            } catch (RuntimeException ex) {
                lastError = ex;
                if (!isRetryableGeminiError(ex))
                    throw ex;
                System.err.println("[ChatService] Phân hệ Model " + model + " quá tải: " + ex.getMessage());
            }
        }
        throw lastError != null ? lastError : new RuntimeException("Tất cả cổng kết nối Gemini API tạm thời đóng.");
    }

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
                .block();
    }

    private boolean isRetryableGeminiError(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        return msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("503") || msg.contains("404");
    }

    private String buildGeminiPayload(List<ChatMessage> history, String newMessage, String systemInstruction)
            throws Exception {
        List<Map<String, Object>> contents = new ArrayList<>();
        int historyStart = Math.max(0, history.size() - maxHistoryMessages);
        while (historyStart < history.size() && !"user".equals(history.get(historyStart).getRole())) {
            historyStart++;
        }

        String lastRole = null;
        for (int i = historyStart; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            String role = m.getRole();

            if (role.equals(lastRole) && !contents.isEmpty()) {
                Map<String, Object> lastContent = contents.get(contents.size() - 1);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> parts = (List<Map<String, Object>>) lastContent.get("parts");
                Map<String, Object> textMap = parts.get(0);
                String oldText = (String) textMap.get("text");
                textMap.put("text", oldText + "\n" + m.getMessage());
            } else {
                Map<String, Object> content = new LinkedHashMap<>();
                content.put("role", role);
                List<Map<String, Object>> parts = new ArrayList<>();
                parts.add(new LinkedHashMap<>(Map.of("text", m.getMessage())));
                content.put("parts", parts);
                contents.add(content);
                lastRole = role;
            }
        }

        if (contents.isEmpty() && newMessage != null && !newMessage.isBlank()) {
            Map<String, Object> userTurn = new LinkedHashMap<>();
            userTurn.put("role", "user");
            userTurn.put("parts", List.of(Map.of("text", newMessage)));
            contents.add(userTurn);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        payload.put("contents", contents);
        return mapper.writeValueAsString(payload);
    }

    private String buildSimplePayload(String system, String question) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", system))));
        payload.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", question)))));
        return mapper.writeValueAsString(payload);
    }

    @SuppressWarnings("unchecked")
    private String parseGeminiResponse(String responseBody) throws Exception {
        Map<String, Object> response = mapper.readValue(responseBody, Map.class);
        if (response.containsKey("error"))
            throw new RuntimeException("Lỗi máy chủ Gemini: " + response.get("error"));

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty())
            throw new RuntimeException("Mô hình không thể phản hồi câu hỏi này.");

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null)
            throw new RuntimeException("Cấu trúc JSON phản hồi trống.");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty() || parts.get(0).get("text") == null)
            throw new RuntimeException("Không tìm thấy phân mảnh Text.");

        return (String) parts.get(0).get("text");
    }

    private void saveMessage(String username, String role, String message, String sessionId) {
        ChatMessage msg = new ChatMessage();
        msg.setUsername(username);
        msg.setRole(role);
        msg.setMessage(message);
        msg.setSessionId(sessionId);
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

    private String toUserFriendlyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota"))
            return " Hệ thống trí tuệ nhân tạo tạm thời đạt ngưỡng giới hạn truy cập trong ngày (Hạn ngạch của gói kết nối miễn phí).\n\nBạn có thể thử lại sau ít phút hoặc tra cứu thủ công trực tiếp trên thanh công cụ của hệ thống nhé!";
        if (msg.contains("API key") || msg.contains("401") || msg.contains("403"))
            return "🔑 Sự cố xác thực hệ thống: Khóa kết nối API (Gemini Key) không chính xác hoặc hết hạn.";
        return "⚠️ Thật xin lỗi bạn, bộ máy xử lý của mình gặp một xung đột kỹ thuật nhỏ ngoài ý muốn. Vui lòng thử lại sau giây lát!";
    }
}