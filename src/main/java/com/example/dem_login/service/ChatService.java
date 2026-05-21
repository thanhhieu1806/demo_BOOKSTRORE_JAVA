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

    // Repositories ─
    private final ChatMessageRepository chatRepo;
    private final BookRepository bookRepo;
    private final OrderRepository orderRepo;
    private final WebClient webClient;
    private final PdfReaderService pdfReaderService;
    private final ObjectMapper mapper = new ObjectMapper();

    // Config từ application.properties ──
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

    // Hằng số
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Stop words tiếng Việt — CHỈ bỏ những từ thuần chức năng, KHÔNG bỏ từ mang
     * nghĩa
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "cho", "toi", "minh", "hoi", "ve", "thong", "tin",
            "la", "gi", "co", "khong", "nhu", "the", "nao", "muon", "xin", "hay",
            "mot", "cac", "nhung", "duoc", "trong", "cua", "va", "de", "khi", "day",
            "nhe", "nha", "oi", "a", "ah", "u", "roi", "that", "qua", "vay",
            "di", "voi", "ke");

    // System Prompts

    /**
     * Prompt chính: nhân cách BookBot + quy tắc trả lời TẬP TRUNG.
     * Nguyên tắc: CHỈ trả lời đúng câu hỏi, không liệt kê thêm thứ không được hỏi.
     */
    private static final String BASE_INSTRUCTION = """
            Bạn là BookBot — nhân viên tư vấn sách thông minh, nhiệt tình và am hiểu.

            PHONG CÁCH GIAO TIẾP:
            - Xưng "mình", gọi khách là "bạn" — gần gũi, ấm áp.
            - Trả lời thân thiện, rõ ràng, dễ hiểu. Có thể thêm cảm xúc tích cực nhẹ nhàng.
            - Dùng emoji phù hợp (📚 💡 ✅) để câu trả lời sinh động hơn.

            QUY TẮC QUAN TRỌNG (PHẢI TUÂN THỦ TUYỆT ĐỐI):
            1. CHỈ trả lời đúng những gì được hỏi. KHÔNG tự ý thêm thông tin không liên quan.
            2. Nếu user hỏi GIÁ → chỉ nói về giá, không liệt kê toàn bộ metadata.
            3. Nếu user hỏi TÁC GIẢ → chỉ nói tác giả, không cần mô tả thêm thể loại/giá.
            4. Nếu user hỏi NỘI DUNG → phân tích từ PDF/database, không bịa thông tin.
            5. Nếu trong đoạn trích PDF có chứa thông tin bản quyền (copyright), nhà xuất bản, địa chỉ trụ sở,
               số điện thoại, fax, email, website của nhà xuất bản, tuyệt đối KHÔNG được đưa vào câu trả lời
               trừ khi user hỏi trực tiếp về chúng.
            6. Nếu user hỏi DANH SÁCH → liệt kê ngắn gọn, đủ thông tin cơ bản.
            7. Nếu user hỏi GIÁ CAO/THẤP NHẤT → PHẢI sắp xếp đúng thứ tự và nêu rõ cuốn đứng đầu.
            8. KHÔNG bịa thông tin về sách, tác giả, giá cả, tồn kho, nội dung.
            9. Nếu không có thông tin → nói thẳng và gợi ý cách hỏi khác.

            TRẠNG THÁI ĐƠN HÀNG:
            - PENDING = Đang chờ xử lý
            - CONFIRMED = Đã xác nhận
            - SHIPPING = Đang giao hàng
            - DELIVERED = Đã giao thành công

            BỔ SUNG NGỮ CẢNH: Có thể thêm 1 câu ngữ cảnh nhẹ (thời gian, lời khuyên nhỏ)
            nếu phù hợp, nhưng KHÔNG được lấn át nội dung chính.

            ═══════════════════════════════════════════════
            TÍNH NĂNG ACTION TRIGGERS — ĐỌC KỸ VÀ TUÂN THỦ
            ═══════════════════════════════════════════════

            Sau mỗi câu trả lời, bạn PHẢI tự động đề xuất hành động phù hợp bằng các thẻ ActionTrigger.
            Mục tiêu: giúp người dùng điều hướng tự nhiên như Gemini — không cần gõ thêm.

            ── LOẠI 1: XEM CHI TIẾT SÁCH (khi trả lời về 1 cuốn cụ thể) ──
            Khi context có thông tin 1 cuốn sách cụ thể với ID, LUÔN thêm:

            <ActionTrigger type="view-detail" target="book-detail" id="[BOOK_ID]">📖 Xem chi tiết sách</ActionTrigger>

            ── LOẠI 2: ĐẶT MUA NGAY (khi đã biết ID sách và còn hàng) ──
            Nếu sách còn tồn kho > 0, thêm thêm:

            <ActionTrigger type="order" target="cart" id="[BOOK_ID]">🛒 Đặt mua ngay</ActionTrigger>

            ── LOẠI 3: XEM DANH SÁCH SÁCH (khi trả lời câu hỏi tổng quát / gợi ý nhiều sách) ──
            Khi liệt kê từ 2 sách trở lên, thêm:

            <ActionTrigger type="navigate" target="books">📚 Xem toàn bộ sách</ActionTrigger>

            ── LOẠI 4: XEM ĐƠN HÀNG (khi trả lời về đơn hàng của user) ──
            Sau khi trả lời về đơn hàng, thêm:

            <ActionTrigger type="navigate" target="orders">📦 Xem đơn hàng của tôi</ActionTrigger>

            ── LOẠI 5: ĐẶT HÀNG MỚI (khi user hỏi cách đặt hàng) ──
            <ActionTrigger type="navigate" target="books">🛍️ Bắt đầu mua sắm</ActionTrigger>

            ── LOẠI 6: TÌM KIẾM (khi không tìm thấy sách user cần) ──
            <ActionTrigger type="search" target="books" query="[TỪ KHÓA TÌM KIẾM]">🔍 Tìm kiếm "[TỪ KHÓA]"</ActionTrigger>

            QUY TẮC BẮT BUỘC CHO ACTION TRIGGERS:
            - Tất cả thẻ ActionTrigger phải nằm ở CUỐI câu trả lời, sau phần text.
            - Mỗi thẻ nằm trên 1 DÒNG RIÊNG BIỆT.
            - KHÔNG đặt trong code block, KHÔNG đặt giữa đoạn văn.
            - BOOK_ID lấy từ "ID: X" trong context — KHÔNG tự bịa.
            - Nếu không có ID thì KHÔNG thêm trigger loại 1 và 2.
            - Có thể kết hợp nhiều trigger (tối đa 3) — ưu tiên cái phù hợp nhất lên đầu.
            - Luôn chọn trigger có nghĩa nhất với ngữ cảnh, đừng thêm thừa.

            VÍ DỤ KẾT HỢP ĐÚNG:
            [Nội dung trả lời về sách Amazon với ID: 5, còn 20 cuốn]
            ...
            <ActionTrigger type="view-detail" target="book-detail" id="5">📖 Xem chi tiết sách</ActionTrigger>
            <ActionTrigger type="order" target="cart" id="5">🛒 Đặt mua ngay</ActionTrigger>

            VÍ DỤ KẾT HỢP ĐÚNG (danh sách nhiều sách):
            [Liệt kê 3 cuốn sách về lập trình]
            ...
            <ActionTrigger type="navigate" target="books">📚 Xem toàn bộ sách</ActionTrigger>
            <ActionTrigger type="search" target="books" query="lập trình">🔍 Tìm kiếm "lập trình"</ActionTrigger>
            """;

    /** Prompt cho chế độ phân tích PDF — tập trung hoàn toàn vào nội dung file */
    private static final String PDF_ANALYSIS_INSTRUCTION = """
            Bạn là BookBot — chuyên gia phân tích sách, trả lời CÓ CẤU TRÚC, RÕ RÀNG, CHÍNH XÁC.
            Xưng "mình", gọi khách là "bạn".

            ĐỊNH DẠNG TRẢ LỜI BẮT BUỘC (tuân thủ nghiêm túc):
            1. Mở đầu bằng tiêu đề in đậm: **[Câu hỏi được diễn đạt lại ngắn gọn]**
            2. Trả lời trực tiếp từ nội dung sách — dùng bullet (•) hoặc số thứ tự khi liệt kê nhiều điểm.
            3. Cuối bài thêm mục **"Tóm tắt theo sách:"** với 1–2 câu trích dẫn ý chính trong nháy kép "...".
            4. Nếu đoạn trích PDF KHÔNG chứa thông tin liên quan đến câu hỏi → thêm mục:
               **"Nhận xét về đoạn trích:"** và giải thích rõ: đoạn này nói về gì, tại sao không liên quan, có thể trích nhầm trang không.

            QUY TẮC BẮT BUỘC:
            - TUYỆT ĐỐI không dùng thông tin bản quyền, nhà xuất bản, địa chỉ, điện thoại, fax, email, ISBN, Amazon Fulfillment, MOQ từ PDF dù chúng xuất hiện trong đoạn trích — hãy BỎ QUA hoàn toàn.
            - CHỈ dùng nội dung sách thực sự (lý thuyết, khái niệm, hướng dẫn, ví dụ, case study).
            - KHÔNG bịa thông tin ngoài nội dung PDF đã cung cấp.
            - CHỈ trả lời đúng câu hỏi, không lan man, không kể thêm thứ không được hỏi.
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

    /**
     * Gửi tin nhắn chat thông thường.
     * Flow: lưu user msg → build context → gọi Gemini → lưu AI msg → trả về.
     */
    public Dto.ChatResponse sendMessage(Dto.ChatRequest req) {
        try {

            // Kiểm tra sessionId người dùng gửi lên
            // Nếu null hoặc rỗng thì tạo session mới bằng UUID
            String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                    ? UUID.randomUUID().toString()
                    : req.getSessionId();

            // Lưu tin nhắn của user vào database
            // username : người gửi
            // "user" : vai trò người gửi là user
            // message : nội dung tin nhắn
            // sessionId : id phiên chat
            saveMessage(
                    req.getUsername(),
                    "user",
                    req.getMessage(),
                    sessionId);

            // Lấy toàn bộ lịch sử chat theo sessionId
            // Sắp xếp tăng dần theo thời gian tạo
            List<ChatMessage> history = chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId);

            // Kiểm tra API KEY Gemini có tồn tại không
            if (geminiApiKey == null || geminiApiKey.isBlank())

                // Nếu chưa cấu hình thì báo lỗi
                throw new IllegalStateException("Chưa cấu hình gemini.api.key");

            // Load tất cả sách ACTIVE từ database
            // Chỉ load 1 lần để dùng cho toàn bộ pipeline
            List<Book> allBooks = bookRepo.findByStatus(Book.BookStatus.ACTIVE);

            // Tạo system instruction cho AI
            // Bao gồm:
            // - username
            // - câu hỏi hiện tại
            // - lịch sử chat
            // - danh sách sách
            String systemInstruction = buildSystemInstruction(
                    req.getUsername(),
                    req.getMessage(),
                    history,
                    allBooks);

            // Tạo payload JSON gửi lên Gemini API
            String payload = buildGeminiPayload(
                    history,
                    req.getMessage(),
                    systemInstruction);

            // Biến chứa câu trả lời AI
            String aiText;

            try {

                // Gọi Gemini API
                // Có fallback model nếu model chính lỗi
                String responseBody = callGeminiWithFallback(payload);

                // Parse JSON response lấy text AI trả lời
                aiText = parseGeminiResponse(responseBody);

            } catch (Exception apiEx) {

                // Nếu Gemini lỗi

                // Thử tạo câu trả lời local thông minh
                String local = buildSmartLocalAnswer(
                        req.getMessage(),
                        allBooks);

                // Nếu local answer tồn tại -> dùng local
                // Nếu không -> trả lỗi thân thiện cho user
                aiText = (local != null)
                        ? local
                        : toUserFriendlyError(apiEx);
            }

            // Lưu câu trả lời AI vào database
            saveMessage(
                    req.getUsername(),
                    "model",
                    aiText,
                    sessionId);

            // Trả response thành công về frontend
            return new Dto.ChatResponse(
                    true, // success
                    aiText, // message AI
                    sessionId // session hiện tại
            );

        } catch (Exception e) {

            // Log lỗi ra console server
            System.err.println(
                    "[ChatService] Lỗi sendMessage: "
                            + e.getMessage());

            // Trả response thất bại cho frontend
            return new Dto.ChatResponse(
                    false,
                    toUserFriendlyError(e),
                    req.getSessionId());
        }
    }

    /**
     * Hỏi về nội dung một file PDF cụ thể (từ trang chi tiết sách).
     */
    public Dto.ChatResponse askAboutPdf(String username, String question, String pdfPath) {
        try {
            if (!pdfReaderService.isReadable(pdfPath))// kiểm tra file pdf có tồn tại không
                return new Dto.ChatResponse(false,
                        "Không tìm thấy file PDF trên server. Kiểm tra pdf_path: " + pdfPath, null);

            String pdfContent = pdfReaderService.extractText(pdfPath);// đọc nội dung file pdf
            if (pdfContent == null || pdfContent.startsWith("Không đọc được"))
                return new Dto.ChatResponse(false,
                        " File PDF tồn tại nhưng không đọc được nội dung. "
                                + "Có thể file bị scan ảnh hoặc bị mã hóa.",
                        null);

            String cleanedPdf = filterBoilerplateFromPdf(pdfContent);// loại bỏ thông tin không cần thiết ra khỏi file
                                                                     // pdf
            String pdfExcerpt = safeSubstring(cleanedPdf.isBlank() ? pdfContent : cleanedPdf, 20000);// lấy 20000 ký tự
                                                                                                     // đầu tiên của
                                                                                                     // file pdf
            String systemPrompt = PDF_ANALYSIS_INSTRUCTION // hướng dẫn cho AI để phân tích nội dung PDF
                    + "\n=== NỘI DUNG PDF (ĐÃ LỌC THÔNG TIN KHÔNG CẦN THIẾT) ===\n" + pdfExcerpt;
            String payload = buildSimplePayload(systemPrompt, question);// tạo payload JSON gửi lên Gemini API

            String aiText;
            try {
                aiText = parseGeminiResponse(callGeminiWithFallback(payload));// gọi Gemini API và parse response
            } catch (Exception apiEx) {
                Book fromPath = findBookByPdfPath(pdfPath);// tìm sách theo pdfPath
                aiText = (fromPath != null)
                        ? formatDetailedBookAnswer(fromPath, question, cleanedPdf, false)// trả lời chi tiết về sách
                        : "**Trích từ PDF:**\n\n"
                                + extractRelevantSnippet(cleanedPdf, question, 15000)// lấy 15000 ký tự đầu tiên của
                                                                                     // file
                                                                                     // pdf
                                + "\n\n_(AI tạm hết lượt — hiển thị nội dung trực tiếp từ PDF.)_";
            }
            String sessionId = "pdf_" + UUID.randomUUID();// tạo session id
            saveMessage(username, "user", "[PDF] " + question, sessionId);// lưu câu hỏi vào database
            saveMessage(username, "model", aiText, sessionId);// lưu câu trả lời vào database
            return new Dto.ChatResponse(true, aiText, sessionId);// trả response thành công về frontend
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi askAboutPdf: " + e.getMessage());
            return new Dto.ChatResponse(false, toUserFriendlyError(e), null);
        }
    }

    /** Lấy lịch sử chat theo sessionId */
    public List<Dto.ChatHistoryItem> getHistory(String sessionId) {

        // Tìm tất cả tin nhắn theo sessionId
        // và sắp xếp theo thời gian tăng dần
        return chatRepo
                .findBySessionIdOrderByCreateDateAsc(sessionId)
                // Chuyển List thành Stream để xử lý dữ liệu
                .stream()
                // map():
                // Duyệt từng phần tử ChatMessage
                // và chuyển thành ChatHistoryItem
                .map(m -> new Dto.ChatHistoryItem(
                        m.getRole(),
                        m.getMessage(),
                        m.getCreateDate() != null
                                // Nếu có ngày giờ
                                // format lại theo FMT
                                ? m.getCreateDate().format(FMT)
                                // Nếu null -> trả chuỗi rỗng
                                : ""))
                // Collect:
                // Chuyển Stream trở lại thành List
                .collect(Collectors.toList());
    }

    /** Xóa lịch sử chat theo sessionId */
    public Map<String, String> clearHistory(String sessionId) {
        chatRepo.deleteBySessionId(sessionId);
        return Map.of("success", "true", "message", "Đã xóa lịch sử chat");
    }

    /** Kiểm tra kết nối PDF theo đường dẫn */
    public Map<String, Object> checkPdfConnection(String pdfPath) {
        return buildPdfConnectionResult(pdfPath, null);
    }

    /** Kiểm tra kết nối PDF theo bookId */
    public Map<String, Object> checkPdfConnectionByBookId(Long bookId) {
        Book book = bookRepo.findById(bookId).orElse(null);
        if (book == null)
            return Map.of("connected", false, "message", "Không tìm thấy sách id=" + bookId);
        return buildPdfConnectionResult(book.getPdfPath(), book);
    }

    // INTENT DETECTION

    /**
     * Các loại ý định câu hỏi.
     * Thứ tự ưu tiên detect: ORDER → PRICE_SORT → SPECIFIC_BOOK → CONTENT_QUESTION
     * → PRICE_INFO → CATALOG_SEARCH → GENERAL
     */
    enum ChatIntent {
        ORDER, // Hỏi về đơn hàng
        PRICE_SORT, // Hỏi sách giá cao/thấp nhất, sắp xếp theo giá
        SPECIFIC_BOOK, // Hỏi thông tin một cuốn sách cụ thể (giá, tác giả, v.v.)
        CONTENT_QUESTION, // Hỏi về nội dung/khái niệm trong sách (cần đọc PDF)
        PRICE_INFO, // Hỏi giá một cuốn cụ thể
        CATALOG_SEARCH, // Tìm/gợi ý sách
        GENERAL // Câu hỏi chung (thời gian, chào hỏi, v.v.)
    }

    /**
     * Phát hiện intent từ câu hỏi. Nhận allBooks từ ngoài (tránh query DB thêm).
     * /**
     * Hàm detectIntent dùng để xác định
     * ý định (intent) của người dùng khi chat
     */
    private ChatIntent detectIntent(
            String msg,
            List<Book> allBooks) {

        // Nếu message null hoặc rỗng
        if (msg == null || msg.isBlank())

            // Trả về intent mặc định GENERAL
            return ChatIntent.GENERAL;

        // Normalize câu hỏi:
        // - chuyển lowercase
        // - bỏ dấu tiếng Việt
        // - chuẩn hóa text
        String n = normalizeSearch(msg);

        // 1. ĐƠN HÀNG
        // matches():
        // kiểm tra regex có khớp hay không
        if (n.matches(
                ".*(don hang|tra hang|huy don|"
                        + "van chuyen|giao hang|"
                        + "trang thai don|ma don).*"))

            // Nếu có từ khóa liên quan đơn hàng
            return ChatIntent.ORDER;

        // 2. SẮP XẾP / SO SÁNH GIÁ

        // PHẢI detect trước SPECIFIC_BOOK
        // vì user có thể hỏi:
        // "cuốn java nào đắt nhất"
        if (n.matches(
                ".*(gia cao nhat|dat nhat|"
                        + "gia thap nhat|re nhat|gia re nhat|"

                        + "sap xep.*gia|"
                        + "gia.*sap xep|"

                        + "giastienf|"
                        + "gia tien cao|"
                        + "gia tien thap|"

                        + "cuon nao dat|"
                        + "cuon nao re|"
                        + "sach nao dat|"
                        + "sach nao re|"

                        + "dat hon|"
                        + "re hon|"

                        + "gia bao nhieu nhat|"
                        + "so sanh gia).*"))

            // Trả về intent PRICE_SORT
            return ChatIntent.PRICE_SORT;

        // 3. SÁCH CỤ THỂ

        // Kiểm tra người dùng có hỏi
        // đúng tên sách trong database không
        if (isSpecificBookInfoQuestion(msg, allBooks))

            // Ví dụ:
            // "sách Java Core"
            // "thông tin sách Spring Boot"
            return ChatIntent.SPECIFIC_BOOK;

        // 4. HỎI NỘI DUNG SÁCH
        // Kiểm tra có phải câu hỏi nội dung PDF không
        if (isContentQuestion(n))

            // Ví dụ:
            // "nội dung chương 1"
            // "sách nói gì về OOP"
            return ChatIntent.CONTENT_QUESTION;

        // 5. HỎI GIÁ
        // Hỏi giá chung
        // nhưng KHÔNG có tên sách cụ thể
        if (n.matches(
                ".*(gia|bao nhieu tien|"
                        + "bao nhieu d|"
                        + "bao nhieu dong).*"))

            // Ví dụ:
            // "sách bao nhiêu tiền"
            return ChatIntent.PRICE_INFO;

        // 6. TÌM KIẾM CATALOG
        // Tìm sách / gợi ý / giới thiệu
        if (n.matches(
                ".*(co sach|"
                        + "tim sach|"
                        + "goi y sach|"
                        + "sach nao|"
                        + "co gi|"
                        + "ban gi|"

                        + "sach hay|"
                        + "danh sach|"
                        + "xem sach|"
                        + "mua sach|"
                        + "dat mua|"
                        + "sach ve|"

                        + "gioi thieu.*sach|"
                        + "sach.*gioi thieu|"

                        + "goi y|"
                        + "gioi thieu).*"))

            // Ví dụ:
            // "gợi ý sách Java"
            // "có sách backend không"
            return ChatIntent.CATALOG_SEARCH;

        // KHÔNG KHỚP GÌ

        // Trả intent mặc định
        return ChatIntent.GENERAL;
    }

    // BUILD SYSTEM INSTRUCTION

    /**
     * Xây dựng system instruction đầy đủ cho AI.
     * Đây là nơi "bơm" dữ liệu thực từ database vào context của AI.
     * Nguyên tắc: chỉ đưa vào những gì AI THỰC SỰ CẦN để trả lời câu hỏi đó.
     */
    private String buildSystemInstruction(String username, String userMessage,
            List<ChatMessage> history, List<Book> allBooks) {
        // Stringbuilder để nối các chuỗi lại với nhau
        StringBuilder sb = new StringBuilder(BASE_INSTRUCTION);
        // nối thời gian hiện tại vào sb
        sb.append("\nThời gian hiện tại: ").append(LocalDateTime.now().format(FMT)).append("\n");

        // nối intent vào sb
        ChatIntent intent = detectIntent(userMessage, allBooks);// intent là ý định của người dùng
        sb.append("\n[INTENT: ").append(intent).append("]\n");

        try {
            switch (intent) {
                case ORDER -> appendUserOrders(sb, username);// ORDER: xử lý câu hỏi về đơn hàng
                case PRICE_SORT -> handlePriceSort(sb, allBooks, userMessage);// PRICE_SORT: xử lý câu hỏi về sắp xếp
                                                                              // giá
                case SPECIFIC_BOOK -> handleSpecificBook(sb, allBooks, userMessage);// SPECIFIC_BOOK: xử lý câu hỏi về
                                                                                    // sách cụ thể
                case CONTENT_QUESTION -> handleContentQuestion(sb, allBooks, userMessage);// CONTENT_QUESTION: xử lý câu
                                                                                          // hỏi về nội dung sách
                case PRICE_INFO -> handlePriceInfo(sb, allBooks, userMessage);// PRICE_INFO: xử lý câu hỏi về giá
                case CATALOG_SEARCH -> handleCatalogSearch(sb, allBooks, userMessage);// CATALOG_SEARCH: xử lý câu hỏi
                                                                                      // về tìm kiếm
                default -> handleGeneralQuery(sb, allBooks, userMessage);
            }

            // Thêm đơn hàng nếu câu hỏi liên quan đến đơn (ngoài ORDER intent)
            if (intent != ChatIntent.ORDER && username != null && !username.isBlank()
                    && normalizeSearch(userMessage).contains("don")) {
                appendUserOrders(sb, username);// nối đơn hàng vào sb nếu câu hỏi liên quan đến đơn
            }

        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi buildSystemInstruction: " + e.getMessage());
        }

        return sb.toString();
    }

    // INTENT HANDLERS — mỗi handler CHỈ đưa vào thông tin CẦN THIẾT

    /**
     * PRICE_SORT: Sắp xếp sách theo giá, trả lời tập trung vào thứ hạng giá.
     */
    private void handlePriceSort(StringBuilder sb, List<Book> allBooks, String userMessage) {

        // Chuẩn hóa câu hỏi user:
        // - chuyển chữ thường
        // - bỏ dấu tiếng Việt
        // - bỏ ký tự dư
        String n = normalizeSearch(userMessage);

        // Xác định kiểu sắp xếp giá
        // true = giá cao -> thấp
        // false = giá thấp -> cao

        // Mặc định sẽ là giảm dần
        // Trừ khi user hỏi:
        // "rẻ nhất", "giá thấp nhất", ...
        boolean descending = !n.matches(
                ".*(re nhat|gia thap nhat|gia re nhat|thap nhat|re hon).*");

        // Stream danh sách sách để:
        // - lọc sách có giá
        // - sắp xếp theo giá
        // - giới hạn số lượng
        List<Book> sorted = allBooks.stream()
                // Chỉ lấy sách có giá
                .filter(b -> b.getPrice() != null)
                // Sắp xếp theo giá
                .sorted(
                        // Nếu descending=true
                        // -> sort giảm dần (đắt -> rẻ)
                        descending ? Comparator
                                .comparing(Book::getPrice)
                                .reversed()
                                // Nếu descending=false
                                // -> sort tăng dần (rẻ -> đắt)
                                : Comparator
                                        .comparing(Book::getPrice))
                // Giới hạn số lượng sách
                // tránh prompt quá dài
                .limit(maxBooksInPrompt)
                // Chuyển stream -> List
                .collect(Collectors.toList());
        // Nếu không có sách nào
        if (sorted.isEmpty()) {
            sb.append(
                    "\n[Không có sách nào trong hệ thống để so sánh giá.]\n");
            return;
        }

        // Thêm tiêu đề vào prompt AI
        sb.append("\n\n=== DANH SÁCH SÁCH SẮP XẾP THEO GIÁ (")
                // Hiển thị kiểu sort
                .append(
                        descending ? "ĐẮT → RẺ" : "RẺ → ĐẮT")
                .append(") ===\n");
        // Biến đánh số thứ hạng
        int rank = 1;

        // Duyệt từng sách
        for (Book b : sorted) {
            sb.append(rank++)
                    .append(". \"")
                    // Tên sách
                    .append(b.getTitle())
                    .append("\"")
                    // Giá sách
                    .append(" — Giá: ")
                    // Format tiền đẹp hơn
                    .append(formatPrice(b.getPrice()))
                    // Số lượng tồn kho
                    .append(" | Còn: ")
                    .append(b.getQuantity())
                    .append(" cuốn\n");
        }

        // Thêm hướng dẫn cho AI
        // để AI tập trung trả lời về giá
        sb.append("\n[HƯỚNG DẪN TRẢ LỜI: ")
                .append("Trả lời TẬP TRUNG vào thứ hạng giá. ")
                .append("Nêu rõ cuốn ")
                // Nếu sort giảm dần
                // thì sách đầu tiên là đắt nhất
                .append(
                        descending ? "ĐẮT NHẤT" : "RẺ NHẤT")
                .append(" ở vị trí số 1. ")
                .append("Không cần mô tả nội dung sách trừ khi được hỏi.]\n");
    }

    /**
     * SPECIFIC_BOOK: Tìm đúng một cuốn sách và cung cấp thông tin phù hợp với câu
     * hỏi.
     * Nguyên tắc: nếu hỏi giá → chỉ nói giá; hỏi tác giả → chỉ nói tác giả; v.v.
     */
    private void handleSpecificBook(StringBuilder sb, List<Book> allBooks, String userMessage) {
        Book book = findSpecificBookInfoMatch(allBooks, userMessage);// tìm sách cụ thể
        if (book == null) {
            // Không tìm thấy sách cụ thể → fallback sang catalog
            handleCatalogSearch(sb, allBooks, userMessage);
            return;
        }
        String n = normalizeSearch(userMessage);
        // Xác định user đang hỏi về khía cạnh nào
        boolean askPrice = n.matches(".*(gia|bao nhieu|bao tien).*");
        boolean askAuthor = n.matches(".*(tac gia|nguoi viet|ai viet|ai lam).*");
        boolean askStock = n.matches(".*(con hang|ton kho|con may|bao nhieu cuon).*");
        boolean askContent = isContentQuestion(n);
        sb.append("\n=== THÔNG TIN SÁCH ===\n");
        sb.append("Tên: \"").append(book.getTitle()).append("\"\n");
        if (askPrice || !askAuthor && !askStock && !askContent) {
            // Nếu hỏi về giá, hoặc hỏi chung chung thì hiện giá
            sb.append("Giá: ").append(formatPrice(book.getPrice())).append("\n");
        }
        if (askAuthor || !askPrice && !askStock && !askContent) {
            // Nếu hỏi về tác giả, hoặc hỏi chung chung thì hiện tác giả
            sb.append("Tác giả: ").append(book.getAuthor() != null ? book.getAuthor() : "Chưa rõ").append("\n");
        }
        if (askStock) {
            sb.append("Tồn kho: ").append(book.getQuantity()).append(" cuốn\n");
        }
        if (!askPrice && !askAuthor && !askStock) {
            // Câu hỏi chung về sách → đưa đủ metadata
            appendBookDetail(sb, book);
        }

        // Nếu hỏi về nội dung → đọc PDF
        // Nếu user đang hỏi về nội dung sách
        if (askContent) {
            // Kiểm tra sách có đường dẫn PDF hay không
            if (
            // pdfPath khác null
            // nghĩa là có dữ liệu đường dẫn
            book.getPdfPath() != null
                    // Kiểm tra chuỗi không rỗng
                    // ví dụ:sẽ bị loại
                    && !book.getPdfPath().isBlank()
                    // Kiểm tra file PDF có tồn tại
                    // và đọc được không
                    && pdfReaderService.isReadable(
                            book.getPdfPath())) {
                // Nếu PDF hợp lệ
                // Trích nội dung liên quan từ PDF
                // rồi append vào prompt AI
                appendPdfExcerpts(
                        // StringBuilder chứa context AI
                        sb,
                        // Tạo List chứa 1 book
                        List.of(book),
                        // Câu hỏi user
                        // dùng để tìm đoạn liên quan
                        userMessage);

            } else {
                // Nếu không có PDF hoặc PDF lỗi / không đọc được
                // Thêm ghi chú cho AI biết
                sb.append(
                        // AI sẽ hiểu: không có PDF để đọc
                        "[Lưu ý: Sách này chưa có file PDF. "
                                // AI sẽ trả lời dựa trên
                                // mô tả sách có sẵn trong DB
                                + "Trả lời dựa trên mô tả có sẵn.]\n");
            }
        }
        // Thêm hướng dẫn cho AI
        sb.append(
                // Yêu cầu AI tập trung đúng trọng tâm
                "\n[HƯỚNG DẪN TRẢ LỜI: "
                        // Không lan man
                        + "Trả lời TẬP TRUNG vào đúng điều user hỏi. ")
                // Không tự liệt kê thêm thông tin
                .append(
                        "Không liệt kê thêm thông tin không được hỏi.]\n");
    }

    /**
     * CONTENT_QUESTION: Tìm sách có nội dung phù hợp (ưu tiên sách có PDF).
     */
    private void handleContentQuestion(
            StringBuilder sb,
            List<Book> allBooks,
            String userMessage) {
        // Tìm các sách liên quan đến câu hỏi user
        // Ví dụ:
        // user hỏi "OOP là gì"
        // -> tìm các sách liên quan OOP
        List<Book> candidates = findBooksForContentQuestion(
                allBooks,
                userMessage);
        // Nếu tìm thấy sách liên quan
        if (!candidates.isEmpty()) {
            // Đọc PDF của các sách đó
            // và append nội dung liên quan vào AI prompt
            boolean hasPdf = appendPdfExcerpts(
                    sb,
                    candidates,
                    userMessage);
            // Nếu không đọc được PDF nào
            if (!hasPdf) {
                // Thêm tiêu đề
                sb.append(
                        "\n\n=== THÔNG TIN SÁCH LIÊN QUAN ===\n");
                // Duyệt danh sách sách
                candidates.stream()
                        // Chỉ lấy tối đa 3 sách
                        .limit(3)
                        // Với mỗi sách
                        .forEach(b ->
                        // Append thông tin sách vào prompt
                        appendBookLine(sb, b));
                // Thêm ghi chú cho AI
                sb.append(
                        "[Lưu ý: Chưa đọc được PDF. "
                                + "Trả lời dựa trên mô tả sách.]\n");
            }
        } else {
            // Nếu không tìm thấy sách nào khớp metadata
            // Thử tìm full-text trong toàn bộ PDF
            boolean foundInPdf = appendPdfExcerptsFromAllBooks(
                    // StringBuilder prompt AI
                    sb,
                    // Toàn bộ sách trong hệ thống
                    allBooks,
                    // Câu hỏi user
                    userMessage);

            // Nếu vẫn không tìm thấy
            if (!foundInPdf) {
                // Thêm hướng dẫn cho AI
                sb.append("\n[Không tìm thấy thông tin liên quan "
                        + "trong hệ thống. ")
                        // Yêu cầu AI nói thật
                        .append("Hãy nói thật với user ")
                        // Gợi ý user hỏi cụ thể hơn
                        .append("và gợi ý hỏi theo tên sách cụ thể.]\n");
            }
        }
    }

    /**
     * PRICE_INFO: Hỏi giá chung (không có tên sách cụ thể).
     * Hiển thị danh sách sách với giá — không cần metadata thừa.
     */
    private void handlePriceInfo(StringBuilder sb, List<Book> allBooks, String userMessage) {
        List<Book> relevant = findRelevantBooks(allBooks, userMessage, false);
        // Tìm kiếm sách liên quan
        List<Book> toShow = relevant.stream()
                .filter(b -> b.getPrice() != null)// Lọc sách có giá
                .limit(10)// Chỉ lấy tối đa 10 sách
                .collect(Collectors.toList());// Collection lại danh sách sách

        if (toShow.isEmpty()) {// kiểm tra xem danh sách sách có rỗng không
            sb.append("\n[Không có thông tin giá trong hệ thống.]\n");
            return;
        }
        sb.append("\n\n=== BẢNG GIÁ SÁCH ===\n");
        for (Book b : toShow) {// lặp qua danh sách sách
            sb.append("- \"").append(b.getTitle()).append("\"")// in tên sách
                    .append(" | ").append(formatPrice(b.getPrice()))// in giá sách
                    .append(" | Còn: ").append(b.getQuantity()).append(" cuốn\n");// in số lượng sách
        }
        sb.append("\n[HƯỚNG DẪN TRẢ LỜI: Chỉ nói về giá, không thêm thông tin không liên quan.]\n");
    }

    /**
     * CATALOG_SEARCH: Tìm/gợi ý sách theo từ khóa.
     */
    private void handleCatalogSearch(StringBuilder sb, List<Book> allBooks, String userMessage) {
        List<Book> relevant = findRelevantBooks(allBooks, userMessage, false);

        sb.append("\n\n=== DANH SÁCH SÁCH (")
                .append(relevant.size())
                .append(relevant.size() < allBooks.size()
                        ? " kết quả / " + allBooks.size() + " tổng"
                        : " cuốn")
                .append(") ===\n");
        relevant.forEach(b -> appendBookLine(sb, b));
        sb.append("\n[HƯỚNG DẪN TRẢ LỜI: Liệt kê sách ngắn gọn. ")
                .append("Nếu user hỏi về thể loại cụ thể thì chỉ hiển thị sách thuộc thể loại đó.]\n");
    }

    /**
     * GENERAL: Câu hỏi chung, chỉ gợi ý vài cuốn nổi bật nếu liên quan đến sách.
     */
    private void handleGeneralQuery(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String n = normalizeSearch(userMessage);
        // Chỉ gợi ý sách nếu câu hỏi có liên quan đến sách
        if (n.contains("sach") || n.contains("doc") || n.contains("goi y")) {
            List<Book> relevant = findRelevantBooks(allBooks, userMessage, false);
            if (!relevant.isEmpty()) {
                sb.append("\n=== MỘT SỐ SÁCH NỔI BẬT ===\n");
                relevant.stream().limit(5).forEach(b -> appendBookLine(sb, b));
            }
        }
        // Câu chào hỏi, hỏi thời gian, etc. → không cần thêm gì, AI tự trả lời
    }

    /**
     * ORDER: Đơn hàng của user.
     */
    private void appendUserOrders(StringBuilder sb, String username) {
        try {
            if (username == null || username.isBlank())// kiểm tra xem username có rỗng hay không
                return;
            List<Order> orders = orderRepo.findByUsername(username);// lấy danh sách đơn hàng của user
            if (orders.isEmpty()) {// kiểm tra xem danh sách đơn hàng có rỗng hay không
                sb.append("\n[User chưa có đơn hàng nào.]\n");
                return;
            }
            sb.append("\n=== ĐƠN HÀNG CỦA \"").append(username) // thêm tên user vào prompt
                    .append("\" (").append(orders.size()).append(" đơn) ===\n");
            for (Order o : orders) {// duyệt danh sách đơn hàng
                sb.append("- Đơn #").append(o.getId())
                        .append(" | Trạng thái: ").append(o.getStatus())
                        .append(" | Tổng: ").append(formatPrice(o.getTotalAmount()))
                        .append(" | Người nhận: ").append(o.getCustomerName())
                        .append(" | Ngày: ")
                        .append(o.getCreateDate() != null ? o.getCreateDate().format(FMT) : "Chưa rõ")
                        .append("\n");
            }
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi load orders: " + e.getMessage());
        }
    }

    // CLASSIFIERS (phân loại câu hỏi)
    /**
     * Nhận biết câu hỏi về NỘI DUNG sách (cần đọc PDF).
     * Loại trừ câu hỏi về giá/sort để tránh bắt nhầm.
     */
    private boolean isContentQuestion(String normalizedMsg) {
        if (normalizedMsg == null)
            return false;

        // Loại trừ câu hỏi về giá — KHÔNG phải content question
        // normalizedMsg.matches : kiểm tra xem chuỗi có khớp với biểu thức chính quy
        // không
        if (normalizedMsg.matches(".*(gia cao nhat|dat nhat|re nhat|gia thap nhat|"
                + "gia bao nhieu|bao nhieu tien|sap xep gia|so sanh gia).*"))
            return false;

        // Từ khóa chỉ rõ câu hỏi nội dung
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
        // normalize câu hỏi
        for (String kw : contentKeywords) {
            if (normalizedMsg.contains(kw))// kiểm tra xem có chứa từ khóa nào không
                return true;
        }

        // Câu hỏi dạng "X là ai/là gì"
        if (normalizedMsg.matches(".*(la ai|la gi|la cai gi|nghia la gi).*"))
            return true;

        // Có dấu ? nhưng KHÔNG phải hỏi giá/tồn kho/sort
        if (normalizedMsg.contains("?")) {// kiểm tra xem có chứa dấu ? không
            boolean isMetadataQuestion = normalizedMsg.matches(
                    ".*(gia|bao nhieu|con hang|con may|ton kho|tac gia|nguoi viet|xuat ban).*");
            return !isMetadataQuestion;// trả về true nếu không phải câu hỏi metadata
        }
        return false;
    }

    /**
     * Nhận biết câu hỏi tìm/gợi ý sách — cần liệt kê danh sách.
     */
    private boolean isCatalogQuestion(String normalizedMsg) {
        if (normalizedMsg == null)
            return false;
        return normalizedMsg.matches(".*(co sach|tim sach|goi y sach|sach nao|co gi|ban gi|"
                + "sach hay|danh sach|xem sach|mua sach|dat mua|sach ve).*");
    }

    /**
     * Nhận biết câu hỏi thông tin một cuốn sách cụ thể.
     */
    private boolean isSpecificBookInfoQuestion(String userMessage, List<Book> allBooks) {
        if (userMessage == null) // kiểm tra xem userMessage có rỗng không
            return false;
        String n = normalizeSearch(userMessage); // normalize câu hỏi

        // Thử trích tên sách từ câu hỏi
        if (extractBookTitleFromQuestion(n, allBooks) != null)
            return true;

        // Fallback: hỏi thông tin + đề cập sách
        boolean asksForInfo = n.matches(".*\\b(thong tin|chi tiet|mo ta|gioi thieu|gia|bao nhieu|"
                + "con hang|ton kho|tac gia|nguoi viet|xuat ban)\\b.*");
        boolean mentionsBook = n.matches(".*\\b(sach|cuon|book)\\b.*");
        return asksForInfo && mentionsBook;
    }

    // TÌM SÁCH
    /**
     * Trích xuất tên sách từ câu hỏi bằng cách loại bỏ stop words và từ hỏi,
     * sau đó khớp với tên sách trong DB.
     */
    private String extractBookTitleFromQuestion(String normalizedQuery, List<Book> allBooks) {
        if (normalizedQuery == null || normalizedQuery.isBlank())
            return null;
        String cleaned = normalizedQuery
                .replaceAll("\\b(cho toi biet|cho minh biet|cho toi|cho minh|xem|ve|cua|"
                        + "thong tin|chi tiet|mo ta|gioi thieu|gia|bao nhieu|tac gia|"
                        + "nguoi viet|con hang|ton kho|sach|cuon|book|tom tat|doc|pdf|"
                        + "noi dung|chuong|tiet|phan|hoi|muon|can|hay|giup|nhe|nha|ad)\\b", " ")
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
     * Tìm đúng một cuốn sách phù hợp nhất với câu hỏi (cho SPECIFIC_BOOK intent).
     */
    private Book findSpecificBookInfoMatch(List<Book> allBooks, String userMessage) {
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
                    .filter(b -> b.getTitle().toLowerCase(Locale.ROOT)
                            .contains(extracted.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
        }
        List<Book> matched = findRelevantBooks(allBooks, userMessage, true);
        return matched.size() == 1 ? matched.get(0) : null;
    }

    /**
     * Tìm sách phù hợp cho câu hỏi nội dung.
     * Ưu tiên sách có PDF readable lên đầu danh sách.
     */
    private List<Book> findBooksForContentQuestion(List<Book> allBooks, String userMessage) {
        List<Book> byMetadata = allBooks.stream()
                .map(b -> Map.entry(b, scoreBookMatch(b, userMessage)))
                // tìm các sách có điểm số >= 20
                .filter(e -> e.getValue() >= 20)
                // sắp xếp sách theo điểm số giảm dần
                .sorted(Comparator.comparingInt((Map.Entry<Book, Integer> e) -> e.getValue()).reversed())
                // lấy sách
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Book> withPdf = byMetadata.stream()
                .filter(b -> b.getPdfPath() != null && !b.getPdfPath().isBlank()
                        && pdfReaderService.isReadable(b.getPdfPath()))
                .collect(Collectors.toList());
        List<Book> withoutPdf = byMetadata.stream()
                .filter(b -> b.getPdfPath() == null || b.getPdfPath().isBlank()
                        || !pdfReaderService.isReadable(b.getPdfPath()))
                .collect(Collectors.toList());

        List<Book> result = new ArrayList<>(withPdf);
        result.addAll(withoutPdf);
        return result;
    }

    /**
     * Tìm sách liên quan dựa trên điểm số (title, author, category, description).
     * 
     * @param strict nếu true, chỉ trả về sách score > 10; false thì fallback tất cả
     */
    private List<Book> findRelevantBooks(List<Book> books, String userMessage, boolean strict) {
        if (books == null || books.isEmpty())
            return List.of();

        String q = userMessage != null ? userMessage.trim() : "";
        if (q.length() < 2 || isGenericBookQuery(q))
            return books.stream().limit(maxBooksInPrompt).collect(Collectors.toList());

        List<Book> matched = books.stream()
                .map(b -> Map.entry(b, scoreBookMatch(b, q)))
                .filter(e -> e.getValue() >= 10)
                .sorted(Comparator.comparingInt((Map.Entry<Book, Integer> e) -> e.getValue()).reversed())
                .map(Map.Entry::getKey)
                .limit(maxBooksInPrompt)
                .collect(Collectors.toList());

        if (!matched.isEmpty())
            return matched;
        return strict ? List.of() : books.stream().limit(maxBooksInPrompt).collect(Collectors.toList());
    }

    /**
     * Kiểm tra câu hỏi generic (hỏi danh sách chung không có chủ đề cụ thể).
     * Ví dụ: "có sách gì", "danh sách sách" — không có chủ đề rõ ràng.
     */
    private boolean isGenericBookQuery(String q) {
        String n = normalizeSearch(q);
        // Chỉ có từ chung về sách, không có chủ đề cụ thể
        return n.matches(".*\\b(sach|danh sach|co gi|ban gi|goi y|xem sach)\\b.*")
                && !n.matches(".*\\b(java|python|lap trinh|lich su|van hoc|toan|van|anh|sinh|"
                        + "ky nang|kinh doanh|tam ly|triet hoc|khoa hoc|the thao|"
                        + "ban hang|marketing|kinh te|giao duc|y hoc|cong nghe)\\b.*");
    }

    /**
     * Tính điểm liên quan giữa sách và câu hỏi.
     * Điểm cao hơn = liên quan hơn.
     */
    private int scoreBookMatch(Book b, String q) {
        String nq = normalizeSearch(q);
        String title = normalizeSearch(b.getTitle());
        String author = normalizeSearch(b.getAuthor());
        String category = normalizeSearch(b.getCategory());
        String desc = normalizeSearch(b.getDescription());

        int score = 0;

        // Khớp toàn cụm
        if (title.contains(nq) || nq.contains(title)) // nếu title chứa nq hoặc nq chứa title
            score += 100; // tăng score lên 100
        if (category.contains(nq) || nq.contains(category)) // nếu category chứa nq hoặc nq chứa category
            score += 60; // tăng score lên 60
        if (author.contains(nq)) // nếu author chứa nq
            score += 40;
        if (desc.contains(nq))
            score += 25;

        // Khớp từng token
        for (String token : nq.split("\\s+")) {
            if (token.length() < 3 || STOP_WORDS.contains(token))
                continue;
            if (title.contains(token))
                score += 30;
            if (category.contains(token))
                score += 20;
            if (author.contains(token))
                score += 15;
            if (desc.contains(token))
                score += 10;
        }
        return score;
    }

    // ĐỌC PDF VÀO PROMPT

    /**
     * Đọc nội dung PDF và đưa đoạn liên quan vào system prompt.
     * Giới hạn tổng 12.000 ký tự và tối đa 3 sách để tránh vượt token limit.
     * 
     * @return true nếu đọc được ít nhất 1 PDF
     */
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
                System.err.println("[ChatService] Lỗi extractText bookId=" + b.getId() + ": " + e.getMessage());
                continue;
            }

            if (pdfText == null || pdfText.isBlank() || pdfText.startsWith("Không đọc được"))
                continue;

            int allowed = Math.min(MAX_PER, MAX_TOTAL - totalCharsAdded);
            String snippet = extractRelevantSnippet(pdfText, userMessage, allowed);
            if (snippet.isBlank())
                continue;

            String label = (b.getPdfName() != null && !b.getPdfName().isBlank()) ? b.getPdfName() : b.getTitle();
            sb.append("\n\n=== TRÍCH NỘI DUNG PDF (\"").append(b.getTitle())
                    .append("\" | file: ").append(label).append(") ===\n")
                    .append(snippet).append("\n");

            totalCharsAdded += snippet.length();
            added++;
        }
        return added > 0;
    }

    /**
     * Tìm kiếm nội dung câu hỏi trong TẤT CẢ file PDF (fallback khi không khớp
     * metadata).
     */
    private boolean appendPdfExcerptsFromAllBooks(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String nq = normalizeSearch(userMessage);
        String[] queryTokens = Arrays.stream(nq.split("\\s+"))
                .filter(t -> t.length() >= 4 && !STOP_WORDS.contains(t))
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
                System.err.println("[ChatService] Lỗi đọc PDF bookId=" + b.getId() + ": " + e.getMessage());
            }

            if (matchingBooks.size() >= 2)
                break;
        }

        if (matchingBooks.isEmpty())
            return false;
        return appendPdfExcerpts(sb, matchingBooks, userMessage);
    }

    /**
     * Phát hiện câu boilerplate (thông tin nhà xuất bản, bản quyền, liên hệ...).
     * Những câu này sẽ bị bỏ qua HOÀN TOÀN, không tính điểm.
     */
    private boolean isBoilerplateSentence(String normalizedSentence) {
        return normalizedSentence.matches(
                ".*(nha xuat ban|nxb|ban quyen|all rights reserved|copyright|isbn|" +
                        "tru so chinh|chi nhanh|so dien thoai|tel|fax|email|website|http|www\\.|" +
                        "in lan|in lan thu|tai ban|tai ban lan|xuat ban lan|" +
                        "fulfillment|amazon|moq|minimum order|muc dat hang toi thieu|" +
                        "lien he|hop dong|giay phep|so giay phep|gkxb|" +
                        "gia tien viet nam|in tai|quan 1|quan 3|ha noi|ho chi minh|" +
                        "marketing@|info@|support@|sales@|publisher).*");
    }

    /**
     * Lọc bỏ từng DÒNG boilerplate khỏi nội dung PDF thô trước khi gửi lên Gemini.
     * Hoạt động ở cấp độ dòng (finer grain) — bổ sung cho isBoilerplateSentence
     * (cấp câu).
     */
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

    /**
     * Trích xuất đoạn văn LIÊN QUAN NHẤT từ PDF dựa trên câu hỏi.
     * Tách câu → lọc boilerplate → tính điểm → ghép những câu có điểm cao nhất.
     */
    private String extractRelevantSnippet(String pdfText, String question, int maxLen) {
        if (pdfText == null || pdfText.isBlank())
            return "";

        String[] sentences = pdfText.split("(?<=[.!?\\n])\\s+");
        String nq = normalizeSearch(question);
        String[] queryTokens = Arrays.stream(nq.split("\\s+"))
                .filter(t -> t.length() >= 3 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);

        // Tính điểm cho từng câu
        record ScoredSentence(String text, int score) {
        }
        List<ScoredSentence> scored = new ArrayList<>();

        for (String s : sentences) {
            String trimmed = s.trim();
            if (trimmed.length() < 15)
                continue;

            String ns = normalizeSearch(trimmed);

            // Bỏ qua câu boilerplate (thông tin nhà xuất bản, địa chỉ, bản quyền...)
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

        // Sắp xếp theo điểm giảm dần, lấy những câu tốt nhất
        scored.sort(Comparator.comparingInt(ScoredSentence::score).reversed());

        StringBuilder result = new StringBuilder();
        for (ScoredSentence ss : scored) {
            if (result.length() + ss.text().length() > maxLen)
                break;
            result.append(ss.text()).append(" ");
        }

        return safeSubstring(result.toString().trim(), maxLen);
    }

    // PDF CONNECTION CHECK

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
            result.put("message", "Chưa có pdf_path trong database");
            return result;
        }

        var resolved = pdfReaderService.resolvePdfPath(pdfPath);
        if (resolved == null) {
            result.put("connected", false);
            result.put("message", "File PDF không tồn tại trên server. Kiểm tra app.upload.dir");
            return result;
        }

        result.put("connected", true);
        result.put("resolvedPath", resolved.toString());

        String preview = pdfReaderService.extractText(pdfPath);
        boolean ok = preview != null && !preview.startsWith("Không đọc được");
        result.put("readable", ok);
        result.put("previewLength", ok ? preview.length() : 0);
        result.put("message", ok ? "✅ Đã kết nối và đọc được PDF" : preview);

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

    // FALLBACK LOCAL ANSWER (khi Gemini hết quota / lỗi mạng)
    /**
     * Xây dựng câu trả lời local dựa hoàn toàn vào dữ liệu DB (không gọi AI).
     */
    private String buildSmartLocalAnswer(String userMessage, List<Book> books) {
        if (userMessage == null || userMessage.isBlank())
            return null;
        try {
            ChatIntent intent = detectIntent(userMessage, books);

            // PRICE_SORT — trả lời ngay không cần Gemini
            if (intent == ChatIntent.PRICE_SORT) {
                String n = normalizeSearch(userMessage);
                boolean desc = !n.matches(".*(re nhat|gia thap nhat|gia re nhat|thap nhat).*");
                List<Book> sorted = books.stream()
                        .filter(b -> b.getPrice() != null)
                        .sorted(desc ? Comparator.comparing(Book::getPrice).reversed()
                                : Comparator.comparing(Book::getPrice))
                        .limit(10).collect(Collectors.toList());
                if (sorted.isEmpty())
                    return "Hiện chưa có sách nào trong hệ thống.";
                StringBuilder sb = new StringBuilder();
                sb.append("Mình tìm thấy các cuốn sách sắp xếp theo giá ")
                        .append(desc ? "(đắt → rẻ)" : "(rẻ → đắt)").append(":\n\n");
                int i = 1;
                for (Book b : sorted) {
                    sb.append(i++).append(". **").append(b.getTitle()).append("**")
                            .append(" — ").append(formatPrice(b.getPrice()))
                            .append(" | Còn ").append(b.getQuantity()).append(" cuốn\n");
                }
                return sb.toString().trim();
            }

            // SPECIFIC_BOOK
            Book specific = findSpecificBookInfoMatch(books, userMessage);
            if (specific != null) {
                String pdfText = null;
                if (isContentQuestion(normalizeSearch(userMessage))
                        && specific.getPdfPath() != null
                        && pdfReaderService.isReadable(specific.getPdfPath())) {
                    pdfText = pdfReaderService.extractText(specific.getPdfPath());
                    if (pdfText != null && pdfText.startsWith("Không đọc được"))
                        pdfText = null;
                }
                return formatDetailedBookAnswer(specific, userMessage, pdfText, false);
            }

            // CONTENT_QUESTION
            if (intent == ChatIntent.CONTENT_QUESTION) {
                List<Book> candidates = findBooksForContentQuestion(books, userMessage);
                if (!candidates.isEmpty()) {
                    Book primary = candidates.get(0);
                    if (primary.getPdfPath() != null && pdfReaderService.isReadable(primary.getPdfPath())) {
                        String pdfText = pdfReaderService.extractText(primary.getPdfPath());
                        if (pdfText != null && !pdfText.startsWith("Không đọc được"))
                            return formatDetailedBookAnswer(primary, userMessage, pdfText, candidates.size() > 1);
                    }
                }
                return "Mình chưa tìm thấy thông tin về chủ đề này. "
                        + "Bạn thử hỏi theo tên cuốn sách cụ thể nhé! 📚";
            }

            // CATALOG_SEARCH / default
            List<Book> matched = findRelevantBooks(books, userMessage, true);
            if (matched.isEmpty())
                return "Rất tiếc, mình chưa tìm thấy sách phù hợp.\n\n"
                        + "Bạn thử gõ rõ tên sách hoặc thể loại (ví dụ: *lập trình*, *lịch sử*) nhé.";
            if (matched.size() == 1)
                return formatDetailedBookAnswer(matched.get(0), userMessage, null, false);
            return formatBookListAnswer(userMessage, matched);

        } catch (Exception e) {
            System.err.println("[ChatService] buildSmartLocalAnswer lỗi: " + e.getMessage());
            return null;
        }
    }

    // FORMAT OUTPUT

    /** Một dòng sách ngắn gọn dùng trong danh sách */
    private void appendBookLine(StringBuilder sb, Book b) {
        sb.append("- \"").append(b.getTitle()).append("\"")
                .append(" | ID: ").append(b.getId())
                .append(" | Tác giả: ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ")
                .append(" | Thể loại: ").append(b.getCategory() != null ? b.getCategory() : "—")
                .append(" | Giá: ").append(formatPrice(b.getPrice()))
                .append(" | Còn: ").append(b.getQuantity()).append(" cuốn")
                .append(b.getPdfPath() != null && !b.getPdfPath().isBlank() ? " | 📄 PDF" : "")
                .append("\n");
    }

    /** Metadata đầy đủ một cuốn sách */
    private void appendBookDetail(StringBuilder sb, Book b) {
        sb.append("ID: ").append(b.getId()).append("\n")
                .append("Tên: \"").append(b.getTitle()).append("\"\n")
                .append("Tác giả: ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ").append("\n")
                .append("Thể loại: ").append(b.getCategory() != null ? b.getCategory() : "—").append("\n")
                .append("Giá: ").append(formatPrice(b.getPrice())).append("\n")
                .append("Tồn kho: ").append(b.getQuantity()).append(" cuốn\n");
        if (b.getDescription() != null && !b.getDescription().isBlank())
            sb.append("Mô tả: ").append(b.getDescription().trim()).append("\n");
        if (b.getPdfPath() != null && !b.getPdfPath().isBlank())
            sb.append("📄 Có file PDF (hỏi mình về nội dung nhé!)\n");
    }

    /** Format câu trả lời chi tiết cho một cuốn sách (dùng trong fallback) */
    private String formatDetailedBookAnswer(Book b, String userMessage, String pdfText, boolean hasMore) {
        StringBuilder sb = new StringBuilder();
        sb.append("📖 **").append(b.getTitle().toUpperCase(Locale.ROOT)).append("**\n");

        sb.append(" ✍️ **Tác giả:** ").append(b.getAuthor() != null ? b.getAuthor() : "Đang cập nhật").append("\n");
        sb.append(" 📁 **Thể loại:** ").append(b.getCategory() != null ? b.getCategory() : "—").append("\n");
        sb.append(" 💵 **Giá bán:** `").append(formatPrice(b.getPrice())).append("`\n");
        sb.append(" 📦 **Tình trạng:** ")
                .append(b.getQuantity() > 0 ? "Còn " + b.getQuantity() + " cuốn" : "Tạm hết hàng").append("\n");

        if (b.getDescription() != null && !b.getDescription().isBlank()) {
            sb.append("\n💡 **Giới thiệu sách:**\n");
            sb.append("> ").append(b.getDescription().trim().replace("\n", "\n> ")).append("\n");
        }

        if (pdfText != null && !pdfText.isBlank()) {
            String snippet = extractRelevantSnippet(pdfText, userMessage, 1500);
            if (!snippet.isBlank()) {
                sb.append("\n📄 **Nội dung liên quan trích từ sách:**\n");
                sb.append("```text\n").append(snippet).append("\n```\n");
            }
        }

        if (hasMore)
            sb.append("\n💡 *Còn sách liên quan khác — hãy gõ tên cuốn sách cụ thể để mình tra cứu tiếp nhé!*");

        return sb.toString().trim();
    }

    /** Format danh sách nhiều sách (dùng trong fallback catalog) */
    private String formatBookListAnswer(String userMessage, List<Book> matched) {
        StringBuilder sb = new StringBuilder();
        String topic = extractTopicFromQuestion(userMessage);

        sb.append("🔍 Mình tìm thấy **").append(matched.size()).append("** cuốn sách");
        if (!topic.isBlank()) {
            sb.append(" phù hợp với từ khóa **\"").append(topic).append("\"**");
        }
        sb.append(":\n\n");

        int i = 1;
        for (Book b : matched.stream().limit(5).collect(Collectors.toList())) {
            sb.append(i++).append(". 📖 **").append(b.getTitle()).append("**\n");
            sb.append("   • **Tác giả:** ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ").append("\n");
            sb.append("   • **Thể loại:** ").append(b.getCategory() != null ? b.getCategory() : "—");
            sb.append("  **Giá:** `").append(formatPrice(b.getPrice())).append("`\n");
            if (b.getDescription() != null && !b.getDescription().isBlank()) {
                String desc = b.getDescription().trim();
                if (desc.length() > 120) {
                    desc = desc.substring(0, 120) + "...";
                }
                sb.append("   • *Mô tả:* ").append(desc).append("\n");
            }
            sb.append("\n");
        }

        if (!matched.isEmpty()) {
            sb.append("💡 **Gợi ý:** Bạn có thể gõ tên một cuốn cụ thể (ví dụ: *")
                    .append(matched.get(0).getTitle())
                    .append("*) để mình tra cứu nội dung chi tiết hoặc đọc thử nhé! ✨");
        }
        return sb.toString().trim();
    }

    private String extractTopicFromQuestion(String userMessage) {
        String n = normalizeSearch(userMessage);
        return Arrays.stream(n.split("\\s+"))
                .filter(token -> token.length() >= 2 && !STOP_WORDS.contains(token))
                .collect(Collectors.joining(" "))
                .trim();
    }

    // GEMINI API
    /**
     * Gọi Gemini API với fallback: thử lần lượt primary model → fallback models.
     */
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
                System.err.println("[ChatService] Model " + model + " thất bại: " + ex.getMessage());
            }
        }
        throw lastError != null ? lastError : new RuntimeException("Không gọi được Gemini API");
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
                                .map(body -> new RuntimeException("Gemini API " + resp.statusCode() + ": " + body)))
                .bodyToMono(String.class)
                .block();
    }

    private boolean isRetryableGeminiError(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        return msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED")
                || msg.contains("503") || msg.contains("404");
    }

    /**
     * Xây dựng payload JSON cho Gemini (có lịch sử chat).
     * Xử lý trường hợp trùng role bằng cách gộp nội dung.
     */
    private String buildGeminiPayload(List<ChatMessage> history, String newMessage,
            String systemInstruction) throws Exception {
        List<Map<String, Object>> contents = new ArrayList<>();

        int historyStart = Math.max(0, history.size() - maxHistoryMessages);
        while (historyStart < history.size()
                && !"user".equals(history.get(historyStart).getRole())) {
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

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        payload.put("contents", contents);
        return mapper.writeValueAsString(payload);
    }

    /** Payload đơn giản (không lịch sử) — dùng cho PDF analysis */
    private String buildSimplePayload(String system, String question) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", system))));
        payload.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", question)))));
        return mapper.writeValueAsString(payload);
    }

    /** Parse JSON response Gemini để lấy text */
    @SuppressWarnings("unchecked")
    private String parseGeminiResponse(String responseBody) throws Exception {
        Map<String, Object> response = mapper.readValue(responseBody, Map.class);
        if (response.containsKey("error"))
            throw new RuntimeException("Gemini error: " + response.get("error"));

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty())
            throw new RuntimeException("Gemini không trả về nội dung");

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null)
            throw new RuntimeException("Gemini không trả về content");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty() || parts.get(0).get("text") == null)
            throw new RuntimeException("Gemini không trả về text");

        return (String) parts.get(0).get("text");
    }

    // UTILITY
    private void saveMessage(String username, String role, String message, String sessionId) {
        ChatMessage msg = new ChatMessage();
        msg.setUsername(username);
        msg.setRole(role);
        msg.setMessage(message);
        msg.setSessionId(sessionId);
        msg.setCreateDate(LocalDateTime.now());
        chatRepo.save(msg);
    }

    /**
     * Chuẩn hóa text: bỏ dấu tiếng Việt, lowercase.
     * Không replace 'y' → 'i' (gây sai khi tìm "Python", "Ruby", v.v.)
     */
    private String normalizeSearch(String text) {
        if (text == null)
            return "";
        String n = Normalizer.normalize(text, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    /** Format giá tiền Việt Nam */
    private String formatPrice(java.math.BigDecimal price) {
        if (price == null)
            return "Chưa có giá";
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(price) + "đ";
    }

    /** Cắt chuỗi an toàn */
    private String safeSubstring(String text, int maxLen) {
        if (text == null)
            return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    /** Chuyển lỗi kỹ thuật thành thông báo thân thiện */
    private String toUserFriendlyError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota"))
            return " Hệ thống AI đã hết lượt hôm nay (Gemini free tier).\n\n"
                    + "Bạn có thể:\n"
                    + "• Thử lại sau 1–24 giờ\n"
                    + "• Tạo API key mới tại https://aistudio.google.com/apikey\n\n"
                    + "Trong lúc chờ, xem danh sách sách trực tiếp trên trang Sách nhé!";
        if (msg.contains("API key") || msg.contains("401") || msg.contains("403"))
            return "🔑 API key Gemini không hợp lệ. Admin cần cập nhật gemini.api.key.";
        return "⚠️ Xin lỗi! Mình gặp sự cố kỹ thuật nhỏ. Bạn thử lại sau ít phút nhé!";
    }
}