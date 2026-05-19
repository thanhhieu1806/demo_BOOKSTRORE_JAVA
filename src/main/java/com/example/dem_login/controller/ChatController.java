package com.example.dem_login.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import com.example.dem_login.dto.Dto;
import com.example.dem_login.service.ChatService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {
    private final ChatService chatService;

    // chatService → service xử lý logic chat
    // @Autowired: tự động tìm và gán một instance của ChatService vào biến
    // chatService.
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // Gửi tin nhắn
    @PostMapping("/send")
    // @RequestBody Dto.ChatRequest req: nhan du lieu tu body cua request
    // @RequestBody: ep du lieu tu body cua request vao bien req
    public ResponseEntity<Dto.ChatResponse> send(@RequestBody Dto.ChatRequest req) {
        Dto.ChatResponse res = chatService.sendMessage(req);
        // Luôn trả 200 để FE đọc được message; success=false khi AI/DB lỗi
        return ResponseEntity.ok(res);
    }

    // Lấy lịch sử chat theo sessionId
    @GetMapping("/history/{sessionId}")
    // @PathVariable dùng để lấy giá trị từ URL
    // Dto.ChatHistoryItem res: tra ve du lieu cho client
    public ResponseEntity<List<Dto.ChatHistoryItem>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getHistory(sessionId));
    }

    // Xóa lịch sử
    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<Map<String, String>> clearHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.clearHistory(sessionId));
    }

  @PostMapping("/ask-pdf")
    public ResponseEntity<Dto.ChatResponse> askPdf(
            @RequestParam("question") String question,
            @RequestParam("pdfPath") String pdfPath,
            @RequestParam("username") String username) {
        return ResponseEntity.ok(chatService.askAboutPdf(username, question, pdfPath));
    }

    /** Kiểm tra pdf_path có đọc được file trên server (debug / admin) */
    @GetMapping("/pdf-status")
    public ResponseEntity<Map<String, Object>> pdfStatus(
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "bookId", required = false) Long bookId) {
        if (bookId != null) {
            return ResponseEntity.ok(chatService.checkPdfConnectionByBookId(bookId));
        }
        if (path != null && !path.isBlank()) {
            return ResponseEntity.ok(chatService.checkPdfConnection(path));
        }
        return ResponseEntity.badRequest().body(Map.of(
                "connected", false,
                "message", "Cần tham số bookId hoặc path"));
    }
}
