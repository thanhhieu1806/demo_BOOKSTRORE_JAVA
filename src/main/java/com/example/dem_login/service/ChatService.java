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

    private final ChatMessageRepository chatRepo;
    private final BookRepository bookRepo;
    private final OrderRepository orderRepo;
    private final WebClient webClient;
    private final PdfReaderService pdfReaderService; // constructor injection — tránh NullPointer
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${gemini.fallback-models:gemini-2.0-flash-lite,gemini-1.5-flash-8b}")
    private String fallbackModels;

    @Value("${gemini.max-history-messages:8}")
    private int maxHistoryMessages;

    @Value("${gemini.max-books-in-prompt:25}")
    private int maxBooksInPrompt;

    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // STOP WORDS
    private static final Set<String> STOP_WORDS = Set.of(
            "cho", "toi", "minh", "ban", "hoi", "ve", "thong", "tin", "sach", "cuon",
            "la", "gi", "co", "khong", "nhu", "the", "nao", "muon", "xin", "hay",
            "mot", "cac", "nhung", "duoc", "trong", "cua", "va", "de", "khi", "day");

    // PROMPT: nhân cách BookBot
    private static final String BASE_INSTRUCTION = "Bạn là BookBot — trợ lý ảo của Nhà sách Online. "
            + "Vai trò: nhân viên tư vấn sách giàu kinh nghiệm, nhiệt tình, đáng tin cậy.\n\n"
            + "PHONG CÁCH TRẢ LỜI:\n"
            + "- Mở đầu ngắn gọn, thân thiện.\n"
            + "- Xưng \"mình\", gọi khách là \"bạn\". Giọng lịch sự, ấm áp.\n"
            + "- Dùng emoji nhẹ (📚 ✨ 💡) tối đa 2–3 cái mỗi tin.\n"
            + "- Trả lời tiếng Việt; nếu khách hỏi tiếng Anh thì trả lời tiếng Anh.\n"
            + "- Ngắn gọn nhưng đủ ý.\n\n"
            + "NĂNG LỰC:\n"
            + "1) Tư vấn sách: tên, tác giả, thể loại, giá, tồn kho.\n"
            + "2) Đọc và trả lời câu hỏi về NỘI DUNG sách từ file PDF.\n"
            + "3) Tra cứu đơn hàng của khách.\n"
            + "4) Hướng dẫn đặt hàng, thanh toán.\n\n"
            + "QUY TẮC QUAN TRỌNG — ĐỌC KỸ:\n"
            + "1. Nếu có phần [TRÍCH NỘI DUNG PDF] bên dưới:\n"
            + "   - BẮT BUỘC trả lời DỰA TRÊN nội dung PDF đó.\n"
            + "   - KHÔNG liệt kê danh sách sách khi câu hỏi hỏi về nội dung, ý nghĩa, nhân vật, khái niệm.\n"
            + "   - Trả lời như một chuyên gia phân tích nội dung sách.\n"
            + "2. Chỉ liệt kê danh sách sách khi user hỏi: 'có sách gì', 'gợi ý sách', 'tìm sách về...'.\n"
            + "3. Nếu câu hỏi ngoài phạm vi sách/đơn hàng và KHÔNG có PDF liên quan,\n"
            + "   hãy lịch sự nói: \"Mình chỉ hỗ trợ tư vấn sách và đơn hàng. Bạn có muốn tìm sách về chủ đề này không?\"\n"
            + "4. Không bịa giá, tồn kho, tác giả, nội dung sách.\n"
            + "5. Giá format: 100.000đ. Đơn hàng: PENDING=Đang chờ, CONFIRMED=Đã xác nhận, CANCELLED=Đã hủy.\n"
            + "6. Từ chối lịch sự nội dung chính trị, bạo lực, người lớn.\n";

    // PROMPT: phân tích PDF độc lập
    private static final String PDF_ANALYSIS_INSTRUCTION = "Bạn là BookBot, chuyên gia tư vấn và phân tích nội dung sách. "
            + "Phong cách: chuyên nghiệp, thân thiện, rõ ràng. Xưng \"mình\", gọi \"bạn\".\n"
            + "Dưới đây là nội dung trích từ PDF sách. Hãy trả lời câu hỏi DỰA TRÊN nội dung đó.\n"
            + "Nếu PDF không chứa câu trả lời, nói thật và gợi ý khách hỏi theo cách khác.\n"
            + "Không bịa thông tin ngoài PDF.\n\n";

    // CONSTRUCTOR (inject tất cả qua constructor — an toàn hơn @Autowired
    // field)
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

    /** Gửi tin nhắn thông thường qua chatbox */
    public Dto.ChatResponse sendMessage(Dto.ChatRequest req) {
        try {
            String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                    ? UUID.randomUUID().toString()
                    : req.getSessionId();

            saveMessage(req.getUsername(), "user", req.getMessage(), sessionId);

            List<ChatMessage> history = chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId);

            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                throw new IllegalStateException("Chưa cấu hình gemini.api.key");
            }

            String systemInstruction = buildSystemInstruction(req.getUsername(), req.getMessage());
            String payload = buildGeminiPayload(history, req.getMessage(), systemInstruction);

            String aiText;
            try {
                String responseBody = callGeminiWithFallback(payload);
                aiText = parseGeminiResponse(responseBody);
            } catch (Exception apiEx) {
                // Fallback nội bộ khi Gemini hết quota
                String local = buildSmartLocalAnswer(req.getMessage());
                aiText = (local != null) ? local : toUserFriendlyError(apiEx);
            }

            saveMessage(req.getUsername(), "model", aiText, sessionId);
            return new Dto.ChatResponse(true, aiText, sessionId);

        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi sendMessage: " + e.getMessage());
            return new Dto.ChatResponse(false, toUserFriendlyError(e), req.getSessionId());
        }
    }

    /** Hỏi về nội dung một file PDF cụ thể (từ trang chi tiết sách) */
    public Dto.ChatResponse askAboutPdf(String username, String question, String pdfPath) {
        try {
            if (!pdfReaderService.isReadable(pdfPath)) {
                return new Dto.ChatResponse(false,
                        "Không tìm thấy file PDF trên server. Kiểm tra pdf_path: " + pdfPath, null);
            }

            String pdfContent = pdfReaderService.extractText(pdfPath);
            if (pdfContent == null || pdfContent.startsWith("Không đọc được")) {
                return new Dto.ChatResponse(false,
                        "File PDF tồn tại nhưng không đọc được nội dung. "
                                + "Có thể file bị scan ảnh hoặc bị mã hóa.",
                        null);
            }

            // Giới hạn nội dung PDF để tránh vượt token limit Gemini
            String pdfExcerpt = safeSubstring(pdfContent, 20000);

            String systemPrompt = PDF_ANALYSIS_INSTRUCTION
                    + "=== NỘI DUNG PDF ===\n" + pdfExcerpt;

            String payload = buildSimplePayload(systemPrompt, question);

            String aiText;
            try {
                String responseBody = callGeminiWithFallback(payload);
                aiText = parseGeminiResponse(responseBody);
            } catch (Exception apiEx) {
                // Fallback: trả nội dung trích thủ công
                Book fromPath = findBookByPdfPath(pdfPath);
                if (fromPath != null) {
                    aiText = formatDetailedBookAnswer(fromPath, question, pdfContent, false);
                } else {
                    aiText = "📄 **Trích từ PDF:**\n\n"
                            + extractRelevantSnippet(pdfContent, question, 1500)
                            + "\n\n_(AI tạm hết lượt — hiển thị nội dung trực tiếp từ PDF.)_";
                }
            }

            String sessionId = "pdf_" + UUID.randomUUID();
            saveMessage(username, "user", "[PDF] " + question, sessionId);
            saveMessage(username, "model", aiText, sessionId);
            return new Dto.ChatResponse(true, aiText, sessionId);

        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi askAboutPdf: " + e.getMessage());
            return new Dto.ChatResponse(false, toUserFriendlyError(e), null);
        }
    }

    /** Lấy lịch sử chat theo sessionId */
    public List<Dto.ChatHistoryItem> getHistory(String sessionId) {
        return chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId)
                .stream()
                .map(m -> new Dto.ChatHistoryItem(
                        m.getRole(),
                        m.getMessage(),
                        m.getCreateDate() != null ? m.getCreateDate().format(FMT) : ""))
                .collect(Collectors.toList());
    }

    /** Xóa lịch sử chat theo sessionId */
    public Map<String, String> clearHistory(String sessionId) {
        chatRepo.deleteBySessionId(sessionId);
        return Map.of("success", "true", "message", "Đã xóa lịch sử chat");
    }

    /** Kiểm tra kết nối PDF theo path */
    public Map<String, Object> checkPdfConnection(String pdfPath) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pdfPathInDb", pdfPath);
        if (pdfPath == null || pdfPath.isBlank()) {
            result.put("connected", false);
            result.put("message", "Chưa có pdf_path trong database");
            return result;
        }
        var resolved = pdfReaderService.resolvePdfPath(pdfPath);
        if (resolved == null) {
            result.put("connected", false);
            result.put("message", "File PDF không tồn tại trên server. Kiểm tra thư mục app.upload.dir");
            return result;
        }
        result.put("connected", true);
        result.put("resolvedPath", resolved.toString());
        String preview = pdfReaderService.extractText(pdfPath);
        boolean ok = preview != null && !preview.startsWith("Không đọc được");
        result.put("readable", ok);
        result.put("previewLength", ok ? preview.length() : 0);
        result.put("message", ok ? "Đã kết nối và đọc được PDF" : preview);
        Book book = findBookByPdfPath(pdfPath);
        if (book != null) {
            result.put("bookId", book.getId());
            result.put("bookTitle", book.getTitle());
        }
        return result;
    }

    /** Kiểm tra kết nối PDF theo bookId */
    public Map<String, Object> checkPdfConnectionByBookId(Long bookId) {
        Book book = bookRepo.findById(bookId).orElse(null);
        if (book == null) {
            return Map.of("connected", false, "message", "Không tìm thấy sách id=" + bookId);
        }
        Map<String, Object> r = new LinkedHashMap<>(checkPdfConnection(book.getPdfPath()));
        r.put("bookId", book.getId());
        r.put("bookTitle", book.getTitle());
        r.put("pdfName", book.getPdfName());
        return r;
    }

    // BUILD SYSTEM INSTRUCTION — TRÁI TIM CỦA CHATBOT

    /**
     * Xây dựng system prompt động theo từng câu hỏi.
     *
     * Logic mới:
     * 1. Phân loại câu hỏi: hỏi nội dung (isContentQuestion) hay hỏi tìm sách
     * (isCatalogQuestion)
     * 2. Nếu hỏi nội dung → tìm sách có PDF → đọc PDF → đưa vào prompt (KHÔNG liệt
     * kê danh sách)
     * 3. Nếu hỏi tìm sách → filter sách theo metadata → liệt kê + đọc PDF nếu có
     * 4. Đơn hàng: luôn đưa vào nếu user đã đăng nhập
     */
    private String buildSystemInstruction(String username, String userMessage) {
        StringBuilder sb = new StringBuilder(BASE_INSTRUCTION);

        try {
            List<Book> allBooks = bookRepo.findByStatus(Book.BookStatus.ACTIVE);

            if (isContentQuestion(userMessage)) {
                // Câu hỏi về NỘI DUNG sách
                // Bước 1: tìm sách liên quan (ưu tiên sách có PDF)
                List<Book> candidates = findBooksForContentQuestion(allBooks, userMessage);

                if (!candidates.isEmpty()) {
                    // Bước 2: đọc PDF và đưa vào prompt
                    boolean hasPdf = appendPdfExcerpts(sb, candidates, userMessage);

                    if (!hasPdf) {
                        // Có sách nhưng không đọc được PDF → thông báo cho AI biết
                        sb.append("\n\n=== THÔNG TIN SÁCH LIÊN QUAN ===\n");
                        for (Book b : candidates.stream().limit(3).collect(Collectors.toList())) {
                            appendBookLine(sb, b);
                        }
                        sb.append("\n[Lưu ý: Sách liên quan tìm thấy nhưng không đọc được nội dung PDF. "
                                + "Hãy trả lời dựa trên thông tin metadata và nói với user rằng "
                                + "mình chưa đọc được file PDF của sách này.]\n");
                    }
                } else {
                    // Không tìm thấy sách liên quan → thử tìm trong tất cả PDF
                    boolean foundInPdf = appendPdfExcerptsFromAllBooks(sb, allBooks, userMessage);
                    if (!foundInPdf) {
                        sb.append("\n[Không tìm thấy sách hay PDF nào liên quan đến câu hỏi này. "
                                + "Hãy nói với user rằng mình không có thông tin về chủ đề này "
                                + "và gợi ý họ tìm theo tên sách cụ thể.]\n");
                    }
                }

            } else {
                // Câu hỏi TÌM/GỢI Ý sách hoặc câu hỏi chung ─
                List<Book> relevant = filterRelevantBooks(allBooks, userMessage);

                if (!relevant.isEmpty()) {
                    sb.append("\n\n=== DANH SÁCH SÁCH ĐANG BÁN (")
                            .append(relevant.size())
                            .append(relevant.size() < allBooks.size()
                                    ? " cuốn liên quan / " + allBooks.size() + " tổng"
                                    : " cuốn")
                            .append(") ===\n");

                    for (Book b : relevant) {
                        appendBookLine(sb, b);
                    }

                    // Đọc PDF của sách liên quan (nếu có), giới hạn 2 cuốn
                    appendPdfExcerpts(sb, relevant, userMessage);
                }
            }

        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi load books: " + e.getMessage());
        }

        // Đơn hàng của user
        try {
            if (username != null && !username.isBlank()) {
                List<Order> orders = orderRepo.findByUsername(username);
                if (!orders.isEmpty()) {
                    sb.append("\n=== ĐƠN HÀNG CỦA \"").append(username)
                            .append("\" (").append(orders.size()).append(" đơn) ===\n");
                    for (Order o : orders) {
                        sb.append("- Đơn #").append(o.getId())
                                .append(" | Trạng thái: ").append(o.getStatus())
                                .append(" | Tổng: ").append(o.getTotalAmount()).append("đ")
                                .append(" | Người nhận: ").append(o.getCustomerName())
                                .append(" | Ngày: ")
                                .append(o.getCreateDate() != null ? o.getCreateDate().format(FMT) : "Chưa rõ")
                                .append("\n");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi load orders: " + e.getMessage());
        }

        return sb.toString();
    }

    // PHÂN LOẠI CÂU HỎI

    /**
     * Nhận biết câu hỏi về NỘI DUNG sách (khái niệm, nhân vật, ý nghĩa, tóm
     * tắt...).
     * Các câu này cần đọc PDF để trả lời, KHÔNG liệt kê danh sách sách.
     */
    private boolean isContentQuestion(String userMessage) {
        if (userMessage == null)
            return false;
        String n = normalizeSearch(userMessage);

        // Từ khóa câu hỏi nội dung
        String[] contentKeywords = {
                "y nghia", "giai thich", "la gi", "nhu the nao", "noi dung",
                "chuong", "phan", "khai niem", "dinh nghia", "tu luyen",
                "nhan vat", "cot truyen", "tom tat", "phan tich", "dac diem",
                "nguyen ly", "phuong phap", "ky thuat", "buoc", "cach thuc",
                "tai sao", "vi sao", "the nao", "quan he", "moi lien he",
                "so sanh", "khac nhau", "giong nhau", "anh huong", "tac dong",
                "qua trinh", "co che", "nguyen nhan", "ket qua", "thu phap",
                "bai hoc", "thong diep", "tinh than", "triet ly", "dao ly"
        };

        for (String kw : contentKeywords) {
            if (n.contains(kw))
                return true;
        }

        // Câu hỏi dạng "X là ai/là gì" — hỏi về nhân vật/khái niệm cụ thể
        if (n.matches(".*(la ai|la gi|la cai gi|nghia la gi).*"))
            return true;

        // Câu có dấu hỏi và không phải hỏi về giá/tồn kho
        if (n.contains("?") || userMessage.contains("?")) {
            boolean isPriceOrStock = n.matches(".*(gia|bao nhieu|con hang|con may|ton kho).*");
            if (!isPriceOrStock)
                return true;
        }

        return false;
    }

    /**
     * Nhận biết câu hỏi tìm/gợi ý sách — cần liệt kê danh sách.
     */
    private boolean isCatalogQuestion(String userMessage) {
        if (userMessage == null)
            return false;
        String n = normalizeSearch(userMessage);
        return n.matches(".*(co sach|tim sach|goi y sach|sach nao|co gi|ban gi|"
                + "sach hay|danh sach|xem sach|mua sach|dat mua).*");
    }

    // TÌM SÁCH CHO CÂU HỎI NỘI DUNG

    /**
     * Tìm sách phù hợp cho câu hỏi nội dung.
     * Ưu tiên: sách có PDF lên đầu, sau đó sắp theo score metadata.
     */
    private List<Book> findBooksForContentQuestion(List<Book> allBooks, String userMessage) {
        // Bước 1: filter theo metadata (title, author, category, description)
        List<Book> byMetadata = allBooks.stream()
                .map(b -> Map.entry(b, scoreBookMatch(b, userMessage)))
                .filter(e -> e.getValue() >= 20) // ngưỡng cao hơn để tránh false positive
                .sorted(Comparator.comparingInt((Map.Entry<Book, Integer> e) -> e.getValue()).reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Bước 2: sắp xếp — sách có PDF lên trước
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
     * Tìm kiếm nội dung câu hỏi trong TẤT CẢ các file PDF (khi không khớp
     * metadata).
     * Dùng khi user hỏi về khái niệm/nhân vật không có trong tên sách.
     */
    private boolean appendPdfExcerptsFromAllBooks(StringBuilder sb, List<Book> allBooks, String userMessage) {
        String nq = normalizeSearch(userMessage);
        String[] queryTokens = Arrays.stream(nq.split("\\s+"))
                .filter(t -> t.length() >= 4 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);

        if (queryTokens.length == 0)
            return false;

        List<Book> booksWithMatchingPdf = new ArrayList<>();

        for (Book b : allBooks) {
            String path = b.getPdfPath();
            if (path == null || path.isBlank() || !pdfReaderService.isReadable(path))
                continue;

            try {
                String pdfText = pdfReaderService.extractText(path);
                if (pdfText == null || pdfText.startsWith("Không đọc được"))
                    continue;

                String normalizedPdf = normalizeSearch(pdfText);
                int matchCount = 0;
                for (String token : queryTokens) {
                    if (normalizedPdf.contains(token))
                        matchCount++;
                }

                // Ít nhất 2 token hoặc 50% token khớp thì đưa vào
                if (matchCount >= 2 || (queryTokens.length > 0 && matchCount * 2 >= queryTokens.length)) {
                    booksWithMatchingPdf.add(b);
                }
            } catch (Exception e) {
                System.err.println("[ChatService] Lỗi đọc PDF bookId=" + b.getId() + ": " + e.getMessage());
            }

            if (booksWithMatchingPdf.size() >= 2)
                break; // giới hạn 2 sách
        }

        if (booksWithMatchingPdf.isEmpty())
            return false;

        return appendPdfExcerpts(sb, booksWithMatchingPdf, userMessage);
    }

    // ĐỌC PDF VÀO PROMPT

    /**
     * Đọc nội dung PDF của các sách và đưa vào system prompt.
     * Ưu tiên sách có PDF, giới hạn tổng 12.000 ký tự để tránh vượt token Gemini.
     *
     * @return true nếu đọc được ít nhất 1 PDF
     */
    private boolean appendPdfExcerpts(StringBuilder sb, List<Book> books, String userMessage) {
        // Sắp xếp: sách có PDF readable lên đầu
        List<Book> sorted = books.stream()
                .sorted(Comparator.comparingInt(b -> (b.getPdfPath() != null && !b.getPdfPath().isBlank()
                        && pdfReaderService.isReadable(b.getPdfPath())) ? 0 : 1))
                .collect(Collectors.toList());

        int totalCharsAdded = 0;
        final int MAX_TOTAL_CHARS = 12000; // giới hạn tổng để tránh vượt token limit
        final int MAX_PER_BOOK = 6000; // giới hạn mỗi cuốn
        int added = 0;

        for (Book b : sorted) {
            if (totalCharsAdded >= MAX_TOTAL_CHARS || added >= 3)
                break;

            String path = b.getPdfPath();
            if (path == null || path.isBlank())
                continue;
            if (!pdfReaderService.isReadable(path))
                continue;

            String pdfText;
            try {
                pdfText = pdfReaderService.extractText(path);
            } catch (Exception e) {
                System.err.println("[ChatService] Lỗi extractText bookId=" + b.getId() + ": " + e.getMessage());
                continue;
            }

            if (pdfText == null || pdfText.isBlank() || pdfText.startsWith("Không đọc được")) {
                System.err.println("[ChatService] PDF không đọc được | bookId=" + b.getId()
                        + " | path=" + path);
                continue;
            }

            System.out.println("[ChatService] Đọc PDF thành công | bookId=" + b.getId()
                    + " | title=" + b.getTitle() + " | chars=" + pdfText.length());

            int allowedChars = Math.min(MAX_PER_BOOK, MAX_TOTAL_CHARS - totalCharsAdded);
            String snippet = extractRelevantSnippet(pdfText, userMessage, allowedChars);

            if (snippet.isBlank())
                continue;

            String label = (b.getPdfName() != null && !b.getPdfName().isBlank())
                    ? b.getPdfName()
                    : b.getTitle();

            sb.append("\n\n=== TRÍCH NỘI DUNG PDF (sách \"")
                    .append(b.getTitle())
                    .append("\", file: ").append(label).append(") ===\n");
            sb.append(snippet).append("\n");

            totalCharsAdded += snippet.length();
            added++;
        }

        return added > 0;
    }

    // HELPER: FILTER SÁCH, SCORING

    private List<Book> filterRelevantBooks(List<Book> books, String userMessage) {
        return findRelevantBooks(books, userMessage, false);
    }

    private List<Book> findRelevantBooks(List<Book> books, String userMessage, boolean strict) {
        if (books == null || books.isEmpty())
            return List.of();

        String q = userMessage != null ? userMessage.trim() : "";
        if (q.length() < 2 || isGenericBookQuery(q)) {
            return books.stream().limit(maxBooksInPrompt).collect(Collectors.toList());
        }

        List<Book> matched = books.stream()
                .map(b -> Map.entry(b, scoreBookMatch(b, q)))
                .filter(e -> e.getValue() >= 10) // ngưỡng tối thiểu để tránh false positive
                .sorted(Comparator.comparingInt((Map.Entry<Book, Integer> e) -> e.getValue()).reversed())
                .map(Map.Entry::getKey)
                .limit(maxBooksInPrompt)
                .collect(Collectors.toList());

        if (!matched.isEmpty())
            return matched;
        return strict ? List.of() : books.stream().limit(maxBooksInPrompt).collect(Collectors.toList());
    }

    private boolean isGenericBookQuery(String q) {
        String n = normalizeSearch(q);
        return n.matches(".*\\b(sach|danh sach|co gi|ban gi|goi y|xem sach)\\b.*")
                && !n.matches(".*\\b(dia ly|java|van hoc|lich su|toan|van|anh|sinh|lap trinh)\\b.*");
    }

    private int scoreBookMatch(Book b, String q) {
        String nq = normalizeSearch(q);
        String title = normalizeSearch(b.getTitle());
        String author = normalizeSearch(b.getAuthor());
        String category = normalizeSearch(b.getCategory());
        String desc = normalizeSearch(b.getDescription());

        int score = 0;

        // Khớp toàn bộ cụm từ
        if (title.contains(nq) || nq.contains(title))
            score += 100;
        if (category.contains(nq) || nq.contains(category))
            score += 60;
        if (author.contains(nq))
            score += 40;
        if (desc.contains(nq))
            score += 25;

        // Khớp từng token (chỉ token >= 3 ký tự và không phải stop word)
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

    // HELPER: TRÍCH ĐOẠN PDF THÔNG MINH

    /**
     * Trích đoạn PDF liên quan nhất đến câu hỏi.
     * Thuật toán: xếp hạng câu theo số token khớp → ghép các câu điểm cao nhất.
     */
    private String extractRelevantSnippet(String pdfText, String question, int maxLen) {
        if (pdfText == null || pdfText.isBlank())
            return "";

        String[] sentences = pdfText.split("(?<=[.!?\\n])\\s+");
        String nq = normalizeSearch(question);
        String[] queryTokens = Arrays.stream(nq.split("\\s+"))
                .filter(t -> t.length() >= 3 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);

        // Xếp hạng câu
        List<String> ranked = new ArrayList<>();
        for (String s : sentences) {
            if (s.length() < 15)
                continue;
            String ns = normalizeSearch(s);
            int score = 0;

            // Khớp cụm từ nguyên văn (điểm cao nhất)
            if (nq.length() > 4 && ns.contains(nq))
                score += 20;

            // Khớp token
            for (String token : queryTokens) {
                if (ns.contains(token)) {
                    score += token.length() >= 5 ? 5 : 2;
                }
            }

            if (score > 0)
                ranked.add(s.trim());
        }

        StringBuilder result = new StringBuilder();

        // Ghép các câu có điểm cao
        for (String s : ranked) {
            if (result.length() + s.length() > maxLen)
                break;
            result.append(s).append(" ");
        }

        // Nếu chưa đủ 200 ký tự thì bổ sung từ đầu văn bản
        if (result.length() < 200) {
            for (String s : sentences) {
                if (s.length() < 15)
                    continue;
                if (result.length() + s.length() > maxLen)
                    break;
                if (!result.toString().contains(s.trim())) {
                    result.append(s.trim()).append(" ");
                }
            }
        }

        String res = result.toString().trim();
        if (res.isEmpty()) {
            res = safeSubstring(pdfText, maxLen);
        }
        return res.length() > maxLen ? res.substring(0, maxLen) + "..." : res;
    }

    // FALLBACK LOCAL ANSWER (khi Gemini hết quota)

    private String buildSmartLocalAnswer(String userMessage) {
        if (userMessage == null || userMessage.isBlank())
            return null;
        try {
            List<Book> books = bookRepo.findByStatus(Book.BookStatus.ACTIVE);

            if (isContentQuestion(userMessage)) {
                // Tìm sách có PDF khớp
                List<Book> candidates = findBooksForContentQuestion(books, userMessage);
                if (!candidates.isEmpty()) {
                    Book primary = candidates.get(0);
                    if (primary.getPdfPath() != null && pdfReaderService.isReadable(primary.getPdfPath())) {
                        String pdfText = pdfReaderService.extractText(primary.getPdfPath());
                        if (pdfText != null && !pdfText.startsWith("Không đọc được")) {
                            return formatDetailedBookAnswer(primary, userMessage, pdfText, candidates.size() > 1);
                        }
                    }
                }
                // Không có PDF → thử tìm trong tất cả
                for (Book b : books) {
                    String path = b.getPdfPath();
                    if (path == null || !pdfReaderService.isReadable(path))
                        continue;
                    String pdfText = pdfReaderService.extractText(path);
                    if (pdfText == null || pdfText.startsWith("Không đọc được"))
                        continue;
                    String nq = normalizeSearch(userMessage);
                    if (normalizeSearch(pdfText).contains(nq.substring(0, Math.min(10, nq.length())))) {
                        return formatDetailedBookAnswer(b, userMessage, pdfText, false);
                    }
                }
                return "Mình chưa tìm thấy thông tin về chủ đề này trong hệ thống sách hiện có. "
                        + "Bạn thử hỏi theo tên cuốn sách cụ thể nhé!";
            }

            // Câu hỏi tìm sách
            List<Book> matched = findRelevantBooks(books, userMessage, true);
            if (matched.isEmpty()) {
                return "Chào bạn! Rất tiếc, mình chưa tìm thấy sách phù hợp.\n\n"
                        + "Bạn thử gõ rõ tên sách hoặc thể loại (ví dụ: *lập trình*, *lịch sử*) nhé.";
            }
            if (matched.size() == 1) {
                Book b = matched.get(0);
                String pdfText = null;
                if (b.getPdfPath() != null && pdfReaderService.isReadable(b.getPdfPath())) {
                    pdfText = pdfReaderService.extractText(b.getPdfPath());
                    if (pdfText != null && pdfText.startsWith("Không đọc được"))
                        pdfText = null;
                }
                return formatDetailedBookAnswer(b, userMessage, pdfText, false);
            }
            return formatBookListAnswer(userMessage, matched);

        } catch (Exception e) {
            System.err.println("[ChatService] buildSmartLocalAnswer lỗi: " + e.getMessage());
            return null;
        }
    }

    // FORMAT OUTPUT

    private void appendBookLine(StringBuilder sb, Book b) {
        sb.append("- \"").append(b.getTitle()).append("\"")
                .append(" | Tác giả: ").append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ")
                .append(" | Thể loại: ").append(b.getCategory() != null ? b.getCategory() : "Chưa phân loại")
                .append(" | Giá: ").append(b.getPrice()).append("đ")
                .append(" | Còn: ").append(b.getQuantity()).append(" cuốn")
                .append(b.getPdfPath() != null && !b.getPdfPath().isBlank() ? " | Có PDF" : "")
                .append("\n");
    }

    private String formatDetailedBookAnswer(Book b, String userMessage, String pdfText, boolean hasMore) {
        StringBuilder sb = new StringBuilder();
        sb.append("Chào bạn! Rất vui được hỗ trợ bạn 📚\n\n");
        sb.append("**").append(b.getTitle()).append("**\n");
        sb.append("• Tác giả: ").append(b.getAuthor() != null ? b.getAuthor() : "Đang cập nhật").append("\n");
        sb.append("• Thể loại: ").append(b.getCategory() != null ? b.getCategory() : "Chưa phân loại").append("\n");
        sb.append("• Giá bán: ").append(formatPrice(b.getPrice()))
                .append(" — Còn **").append(b.getQuantity()).append("** cuốn\n");

        if (b.getDescription() != null && !b.getDescription().isBlank()) {
            sb.append("• Giới thiệu: ").append(b.getDescription().trim()).append("\n");
        }

        if (pdfText != null && !pdfText.isBlank()) {
            String snippet = extractRelevantSnippet(pdfText, userMessage, 1500);
            sb.append("\n📄 **Nội dung từ sách:**\n").append(snippet).append("\n");
        } else if (b.getPdfPath() != null && !b.getPdfPath().isBlank()) {
            if (pdfReaderService.isReadable(b.getPdfPath())) {
                sb.append("\n📄 Sách có bản PDF — hỏi mình về nội dung cụ thể nhé!\n");
            } else {
                sb.append("\n⚠️ Sách có PDF nhưng file chưa đọc được. Vui lòng liên hệ quản trị viên.\n");
            }
        }

        sb.append("\n✨ Bạn có thể thêm vào giỏ hàng trên trang Sách.");
        if (hasMore) {
            sb.append("\n\n💡 Còn sách liên quan khác — hỏi tên cụ thể để mình tra tiếp nhé!");
        }
        return sb.toString().trim();
    }

    private String formatBookListAnswer(String userMessage, List<Book> matched) {
        StringBuilder sb = new StringBuilder();
        String topic = extractTopicFromQuestion(userMessage);
        sb.append("Mình tìm thấy **").append(matched.size()).append("** cuốn");
        if (!topic.isBlank())
            sb.append(" về *").append(topic).append("*");
        sb.append(":\n\n");

        int i = 1;
        for (Book b : matched.stream().limit(5).collect(Collectors.toList())) {
            sb.append(i++).append(". **").append(b.getTitle()).append("**");
            if (b.getAuthor() != null)
                sb.append(" — ").append(b.getAuthor());
            sb.append("\n   Thể loại: ").append(b.getCategory() != null ? b.getCategory() : "—");
            sb.append(" | Giá: ").append(formatPrice(b.getPrice()));
            sb.append(" | Còn: ").append(b.getQuantity()).append(" cuốn");
            if (b.getPdfPath() != null && !b.getPdfPath().isBlank())
                sb.append(" | 📄 Có PDF");
            sb.append("\n");
            if (b.getDescription() != null && !b.getDescription().isBlank()) {
                String desc = b.getDescription().trim();
                if (desc.length() > 120)
                    desc = desc.substring(0, 120) + "...";
                sb.append("   ").append(desc).append("\n");
            }
            sb.append("\n");
        }
        if (!matched.isEmpty()) {
            sb.append("💡 Hỏi tên một cuốn cụ thể (ví dụ: *")
                    .append(matched.get(0).getTitle())
                    .append("*) để mình tra chi tiết và nội dung PDF nhé!");
        }
        return sb.toString().trim();
    }

    private String extractTopicFromQuestion(String userMessage) {
        String n = normalizeSearch(userMessage);
        for (String token : n.split("\\s+")) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token))
                return token;
        }
        return "";
    }

    // GEMINI API

    private String buildGeminiPayload(List<ChatMessage> history, String newMessage,
            String systemInstruction) throws Exception {
        List<Map<String, Object>> contents = new ArrayList<>();

        // Chỉ lấy N tin nhắn gần nhất, và đảm bảo luân phiên user/model
        int historyEnd = history.size() - 1;
        int historyStart = Math.max(0, historyEnd - maxHistoryMessages);

        String lastRole = null;
        for (int i = historyStart; i < historyEnd; i++) {
            ChatMessage m = history.get(i);
            String role = m.getRole();
            // Bỏ qua nếu trùng role liên tiếp (tránh lỗi Gemini)
            if (role.equals(lastRole))
                continue;
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("role", role);
            content.put("parts", List.of(Map.of("text", m.getMessage())));
            contents.add(content);
            lastRole = role;
        }

        // Đảm bảo tin nhắn cuối cùng là user
        if (!"user".equals(lastRole)) {
            contents.add(Map.of("role", "user",
                    "parts", List.of(Map.of("text", newMessage))));
        } else {
            // Nếu cuối history đã là user, thêm message mới vào
            contents.add(Map.of("role", "user",
                    "parts", List.of(Map.of("text", newMessage))));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction",
                Map.of("parts", List.of(Map.of("text", systemInstruction))));
        payload.put("contents", contents);
        return mapper.writeValueAsString(payload);
    }

    private String buildSimplePayload(String system, String question) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system_instruction",
                Map.of("parts", List.of(Map.of("text", system))));
        payload.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", question)))));
        return mapper.writeValueAsString(payload);
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
                                .map(body -> new RuntimeException(
                                        "Gemini API " + resp.statusCode() + ": " + body)))
                .bodyToMono(String.class)
                .block();
    }

    private boolean isRetryableGeminiError(RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        return msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED")
                || msg.contains("503") || msg.contains("404");
    }

    @SuppressWarnings("unchecked")
    private String parseGeminiResponse(String responseBody) throws Exception {
        Map<String, Object> response = mapper.readValue(responseBody, Map.class);
        if (response.containsKey("error")) {
            throw new RuntimeException("Gemini error: " + response.get("error"));
        }
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("Gemini không trả về nội dung");
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null)
            throw new RuntimeException("Gemini không trả về content");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty() || parts.get(0).get("text") == null) {
            throw new RuntimeException("Gemini không trả về text");
        }
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

    private String normalizeSearch(String text) {
        if (text == null)
            return "";
        String n = Normalizer.normalize(text, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
        return n.replaceAll("\\s+", " ");
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
        if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota")) {
            return "📵 Hệ thống AI đã hết lượt miễn phí hôm nay (Gemini free tier).\n\n"
                    + "Bạn có thể:\n"
                    + "• Thử lại sau 1–24 giờ\n"
                    + "• Tạo API key mới tại https://aistudio.google.com/apikey\n\n"
                    + "Trong lúc chờ, hãy xem danh sách sách trực tiếp trên trang Sách.";
        }
        if (msg.contains("API key") || msg.contains("401") || msg.contains("403")) {
            return "🔑 API key Gemini không hợp lệ. Admin cần cập nhật gemini.api.key.";
        }
        return "Xin lỗi! Tôi gặp sự cố kỹ thuật. Hãy thử lại sau ít phút.";
    }
}