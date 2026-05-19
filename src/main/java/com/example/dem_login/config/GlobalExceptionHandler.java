package com.example.dem_login.config;

import com.example.dem_login.dto.Dto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", "false",
                "message", "File quá lớn! Giới hạn tối đa 50MB."));
    }

    /** Chat API luôn trả 200 + JSON để FE hiển thị lỗi, tránh HTTP 500 trống */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex, HttpServletRequest request) {
        String uri = request.getRequestURI() != null ? request.getRequestURI() : "";
        if (uri.contains("/api/chat/")) {
            System.err.println("[GlobalExceptionHandler] " + uri + ": " + ex.getMessage());
            ex.printStackTrace();
            return ResponseEntity.ok(new Dto.ChatResponse(false,
                    "Xin lỗi! Hệ thống gặp sự cố khi xử lý tin nhắn. Vui lòng thử lại sau ít phút.",
                    null));
        }
        return ResponseEntity.internalServerError().body(Map.of(
                "success", "false",
                "message", ex.getMessage() != null ? ex.getMessage() : "Lỗi server"));
    }
}
