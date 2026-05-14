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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class ChatService {
    // khai báo các biến
    private final ChatMessageRepository chatRepo; // repository để thao tác với bảng chat message
    private final BookRepository bookRepo;// repository để thao tác với bảng book
    private final OrderRepository orderRepo; // repository để thao tác với bảng order
    private final WebClient webClient;// webclient để gọi API
    private final ObjectMapper mapper = new ObjectMapper();// object mapper để chuyển đổi json

    // lấy api key từ application.properties
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    // định nghĩa URL để gọi API Gemini
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    // dinh dang ngay gio
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    // prompt nhân cách AI
    private static final String BASE_INSTRUCTION = ""
            // 1. Danh tính
            + "Bạn tên là BookBot, trợ lý AI của hệ thống Quản lý Nhà sách Online. "

            // 2. Giọng điệu & phong cách
            + "Giọng điệu: thân thiện, vui vẻ, chuyên nghiệp như một nhân viên bán sách giỏi. "
            + "Thỉnh thoảng dùng emoji để tạo sự gần gũi (📚, 🎉, ✅, 💡) nhưng không lạm dụng. "
            + "Luôn xưng 'mình' và gọi người dùng là 'bạn'. "

            // 3. Ngôn ngữ
            + "Trả lời bằng tiếng Việt. Nếu người dùng hỏi bằng tiếng Anh thì trả lời bằng tiếng Anh. "
            + "Viết ngắn gọn, dễ hiểu, tránh dài dòng. Mỗi câu trả lời tối đa 3-4 đoạn. "

            // 4. Khả năng
            + "Bạn có khả năng: "
            + "(1) Tra cứu danh sách sách đang bán — tên, tác giả, giá, tồn kho. "
            + "(2) Kiểm tra đơn hàng của người dùng — trạng thái, tổng tiền, ngày đặt. "
            + "(3) Tư vấn sách phù hợp dựa trên sở thích người dùng. "
            + "(4) Hướng dẫn cách đặt hàng, thanh toán. "
            + "(5) Trả lời các câu hỏi chung về sách, tác giả, thể loại. "

            // 5. Quy tắc quan trọng
            + "QUY TẮC BẮT BUỘC: "
            + "- Khi người dùng hỏi về sách hoặc đơn hàng, LUÔN dựa vào DỮ LIỆU THỰC được cung cấp bên dưới. "
            + "- KHÔNG BỊA thông tin sách hoặc đơn hàng nếu không có trong dữ liệu. "
            + "- Nếu không tìm thấy dữ liệu phù hợp, hãy nói rõ: 'Hiện tại mình chưa tìm thấy thông tin này'. "
            + "- KHÔNG trả lời các câu hỏi về chính trị, tôn giáo, bạo lực, nội dung người lớn. "
            + "- Nếu người dùng hỏi ngoài phạm vi sách/đơn hàng, vẫn trả lời vui vẻ nhưng nhắc nhở nhẹ nhàng về chức năng chính. "

            // 6. Định dạng
            + "ĐỊNH DẠNG: Khi liệt kê sách, dùng dạng danh sách rõ ràng. "
            + "Khi nói giá tiền, format theo kiểu '50.000đ'. "
            + "Khi nói trạng thái đơn hàng: PENDING = 'Đang chờ xác nhận', CONFIRMED = 'Đã xác nhận', CANCELLED = 'Đã hủy'. ";

    // Constructor (phương thức khởi tạo)
    public ChatService(ChatMessageRepository chatRepo, BookRepository bookRepo, OrderRepository orderRepo,
            WebClient.Builder webClientBuilder) {
        // chatRepo → làm việc với bảng chat (lưu lịch sử chat)
        // bookRepo → lấy dữ liệu sản phẩm (book)
        // orderRepo → lấy dữ liệu đơn hàng
        // webClientBuilder → dùng để gọi API (ví dụ Gemini AI)
        this.chatRepo = chatRepo;
        this.bookRepo = bookRepo;
        this.orderRepo = orderRepo;
        this.webClient = webClientBuilder.build();
    }

    // Tạo system instruction định hình cách AI hoạt động, kèm dữ liệu database
    private String buildSystemInstruction(String username) {
        // StringBuilder: dùng để nối chuỗi hiệu quả (tránh tạo nhiều object String)
        // Bắt đầu từ prompt gốc (nhân cách AI)
        StringBuilder sb = new StringBuilder(BASE_INSTRUCTION);
        // 1. DANH SÁCH SÁCH
        try {
            // Lấy tất cả sách đang ở trạng thái ACTIVE (đang bán)
            List<Book> books = bookRepo.findByStatus(Book.BookStatus.ACTIVE);
            // Nếu có dữ liệu thì thêm vào prompt
            if (!books.isEmpty()) {

                // Thêm tiêu đề + số lượng sách
                // append: thêm (nối) dữ liệu vào cuối một chuỗi hoặc danh sách
                sb.append("\n\n=== DANH SÁCH SÁCH ĐANG BÁN (")
                        .append(books.size()) // số lượng sách
                        .append(" cuốn) ===\n");

                // Duyệt từng cuốn sách trong danh sách
                for (Book b : books) {
                    // Append từng dòng thông tin sách vào prompt
                    sb.append("- \"")
                            .append(b.getTitle()) // tên sách
                            .append("\"")

                            .append(" | Tác giả: ")
                            .append(b.getAuthor() != null ? b.getAuthor() : "Chưa rõ") // nếu null thì ghi "Chưa rõ"

                            .append(" | Thể loại: ")
                            .append(b.getCategory() != null ? b.getCategory() : "Chưa phân loại")

                            .append(" | Giá: ")
                            .append(b.getPrice()).append("đ") // giá tiền

                            .append(" | Còn: ")
                            .append(b.getQuantity()).append(" cuốn") // số lượng còn

                            .append("\n"); // xuống dòng
                }
            }

        } catch (Exception e) {
            // Nếu lỗi khi query DB thì chỉ log ra console (không làm crash hệ thống)
            System.err.println("[ChatService] Lỗi load books: " + e.getMessage());
        }

        // 2. ĐƠN HÀNG CỦA USER
        try {
            // Chỉ xử lý nếu có username
            if (username != null) {

                // Lấy danh sách đơn hàng theo username
                List<Order> orders = orderRepo.findByUsername(username);

                // Nếu user có đơn hàng
                if (!orders.isEmpty()) {

                    // Thêm tiêu đề + số lượng đơn
                    sb.append("\n=== ĐƠN HÀNG CỦA NGƯỜI DÙNG \"")
                            .append(username) // tên user
                            .append("\" (")
                            .append(orders.size()) // số đơn
                            .append(" đơn) ===\n");

                    // Duyệt từng đơn hàng
                    for (Order o : orders) {

                        // Append thông tin từng đơn vào prompt
                        sb.append("- Đơn #")
                                .append(o.getId()) // id đơn hàng

                                .append(" | Trạng thái: ")
                                .append(o.getStatus()) // trạng thái (PENDING, CONFIRMED,...)

                                .append(" | Tổng: ")
                                .append(o.getTotalAmount()).append("đ") // tổng tiền

                                .append(" | Người nhận: ")
                                .append(o.getCustomerName()) // tên người nhận

                                .append(" | Ngày: ")
                                .append(
                                        o.getCreateDate() != null
                                                ? o.getCreateDate().format(FMT) // format ngày
                                                : "Chưa rõ" // nếu null
                                )
                                .append("\n"); // xuống dòng
                    }
                }
            }
        } catch (Exception e) {
            // Nếu lỗi khi load đơn hàng
            System.err.println("[ChatService] Lỗi load orders: " + e.getMessage());
        }

        // Trả về toàn bộ prompt đã build (bao gồm: BASE + sách + đơn hàng)
        return sb.toString();
    }

    // Gửi tin nhắn và nhận phản hồi từ Gemini
    public Dto.ChatResponse sendMessage(Dto.ChatRequets req) {
        try {
            // tao sessionid neu chua co
            String sessionId = (req.getSessionId() == null || req.getSessionId().isBlank())
                    ? UUID.randomUUID().toString() // tao sessionid mới nếu chưa có
                    : req.getSessionId(); // sử dụng sessionid cũ nếu có

            // luu tin nhan nguoi dung
            saveMessage(req.getUsername(), "user", req.getMessage(), sessionId);

            // Lay lich su chat de gui kem
            List<ChatMessage> history = chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId);

            // Build system instruction ĐỘNG kèm dữ liệu database
            String systemInstruction = buildSystemInstruction(req.getUsername());

            // build payload gui len gemini API
            String payload = buildGeminiPayload(history, req.getMessage(), systemInstruction);

            // goi gemini api
            String responseBody = webClient.post()
                    .uri(GEMINI_URL + "?key=" + geminiApiKey) // url của gemini api
                    .header("Content-Type", "application/json") // header của gemini api
                    .bodyValue(payload) // payload của gemini api
                    .retrieve() // lấy response
                    .bodyToMono(String.class) // lấy response dưới dạng string
                    .block(); // gọi gemini api và nhận response

            // parse cau tra loi
            String aiText = parseGeminiResponse(responseBody);// parse response từ gemini api

            // luu cau tra loi ai vao db
            saveMessage(req.getUsername(), "model", aiText, sessionId);// luu response chat vao db

            return new Dto.ChatResponse(true, aiText, sessionId);// trả về chat response
        } catch (Exception e) {
            System.err.println("[ChatService] Lỗi:" + e.getMessage());
            return new Dto.ChatResponse(false, "Xin lỗi! Tôi gặp sự cố. Hãy thử lại sau ít phút", req.getSessionId());
        }
    }

    // lay lich su theo sessionid
    public List<Dto.ChatHistoryItem> getHistory(String sessionId) {
        // lay lich su tu DB-> map sang DTO -> return List
        return chatRepo.findBySessionIdOrderByCreateDateAsc(sessionId)
                .stream()// Stream dử liệu
                .map(m -> new Dto.ChatHistoryItem(// map qua chat list item DTO
                        m.getRole(),
                        m.getMessage(),
                        m.getCreateDate() != null ? m.getCreateDate().format(FMT) : ""))// map qua list chat item
                .collect(Collectors.toList());// thu thập kết quả vào list
    }

    // xoa lich su session
    public Map<String, String> clearHistory(String sessionId) {
        chatRepo.deleteBySessionId(sessionId);// xóa lịch sử theo session id
        return Map.of("success", "true", "message", "Đã xóa lịch sử chat"); // trả về map
    }

    // lưu tin nhắn vào database
    private void saveMessage(String username, String role, String message, String sessionId) {
        ChatMessage msg = new ChatMessage(); // tạo đối tượng chat message
        msg.setUsername(username); // set username
        msg.setRole(role); // set role
        msg.setMessage(message); // set message
        msg.setSessionId(sessionId); // set session id
        msg.setCreateDate(LocalDateTime.now()); // set create date
        chatRepo.save(msg); // lưu vào db
    }

    // ham nay de xay dung payload cho gemini api
    private String buildGeminiPayload(List<ChatMessage> history, String newMessage, String systemInstruction)
            throws Exception {
        // Build contents array tu lich su + tin nhan moi
        List<Map<String, Object>> contents = new ArrayList<>();

        // them lich su (bo tin nhan cuoi vi do la tin moi vua luu)
        for (int i = 0; i < history.size() - 1; i++) {
            // lay tin nhan
            ChatMessage m = history.get(i);
            // tao content làm gì: de dua vao payload gui len gemini API
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("role", m.getRole());// role của tin nhan
            content.put("parts", List.of(Map.of("text", m.getMessage())));// nội dung tin nhắn
            contents.add(content);// thêm vào list contents
        }
        // them tin nhan moi cua user
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", newMessage))));// role user va noi dung tin nhan moi
        // Build full payload voi system instructions (có kèm dữ liệu DB)
        Map<String, Object> payload = new LinkedHashMap<>();// linkedhashmap de giu thu tu key value
        payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));// thong tin
                                                                                                       // system
        payload.put("contents", contents);// thong tin contents
        return mapper.writeValueAsString(payload);// chuyen sang string
    }

    @SuppressWarnings("unchecked") // bo qua canh bao unchecked
    // parse cau tra loi gemini
    private String parseGeminiResponse(String responseBody) throws Exception {
        // lam sao biet duoc structure cua gemini: su dung ham debug
        // readValue: doc input string-> convert thanh java object
        Map<String, Object> response = mapper.readValue(responseBody, Map.class);// doc response tu gemini API
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");// lay danh sach
                                                                                                      // candidate
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");// lay content
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");// lay parts

        return (String) parts.get(0).get("text");
    }
}
