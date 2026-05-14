package com.example.dem_login.repository;

import com.example.dem_login.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    // Lấy lịch sử chat theo sessionId, sắp xếp theo thời gian
    List<ChatMessage> findBySessionIdOrderByCreateDateAsc(String sessionId);

    // Lấy lịch sử chat theo username
    List<ChatMessage> findByUsernameOrderByCreateDateDesc(String username);

    // Xóa toàn bộ 1 session
    void deleteBySessionId(String sessionId);
}