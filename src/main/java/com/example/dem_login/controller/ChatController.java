package com.example.dem_login.controller;

import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/chat")
@CrossOrigin
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
    // @RequestBody Dto.ChatRequets req: nhan du lieu tu body cua request
    // @RequestBody: ep du lieu tu body cua request vao bien req
    public ResponseEntity<Dto.ChatResponse> send(@RequestBody Dto.ChatRequets req) {
        Dto.ChatResponse res = chatService.sendMessage(req);
        return ResponseEntity.status(res.isSuccess() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR)
                .body(res);
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
}
