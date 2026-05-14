package com.example.dem_login.config;

import com.example.dem_login.model.User;
import com.example.dem_login.repository.UserJpaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner init(UserJpaRepository userRepo) {
        return args -> {
            // Chỉ seed nếu chưa có user nào
            if (userRepo.count() == 0) {
                seedUser(userRepo, "admin@gmail.com", "admin", "123456", User.UserRole.ADMIN);
                seedUser(userRepo, "user@gmail.com", "user", "123456", User.UserRole.USER);
                seedUser(userRepo, "report@gmail.com", "report", "123456", User.UserRole.REPORT);
                System.out.println("✅ Đã tạo 3 tài khoản mặc định trong SQL Server");
            }
        };
    }

    private void seedUser(UserJpaRepository repo, String email, String username,
            String password, User.UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(hashPassword(username + password));
        user.setRole(role);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setCounterLogin(0);
        user.setCreateDate(LocalDateTime.now());
        user.setUpdateDate(LocalDateTime.now());
        repo.save(user);
    }

    private String hashPassword(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}