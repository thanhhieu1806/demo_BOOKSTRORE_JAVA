package com.example.dem_login.service;

import org.springframework.stereotype.Component;
import com.example.dem_login.model.User;
import com.example.dem_login.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

@Component // tu dong chay khi khoi dong ung dung
public class DefaultUserSeeder {

    public void seedIfEmpty(UserRepository repository) {
        if (repository.count() > 0)
            return;
        seedDefaultUsers(repository);
    }

    public void seedDefaultUsers(UserRepository repository) {
        User admin = buildUser("admin@gmail.com", "admin", "123456", User.UserRole.ADMIN);
        User user = buildUser("user@gmail.com", "user", "123456", User.UserRole.USER);
        User report = buildUser("report@gmail.com", "report", "123456", User.UserRole.REPORT);
        repository.save(admin);
        repository.save(user);
        repository.save(report);
    }

    // ham tao user
    private User buildUser(String email, String username, String password, User.UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(hashPassword(username, password));
        user.setRole(role);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setCreateDate(LocalDateTime.now());
        user.setUpdateDate(LocalDateTime.now());
        user.setLastLoginDate(LocalDateTime.now());
        user.setCounterLogin(0);
        user.setMustChangePassword(false); // tai khoan mac dinh khong can doi mat khau
        return user;
    }

    // ham ma hoa mat khau
    private String hashPassword(String username, String data) {
        try {
            // MessageDigest: lop de ma hoa
            // getInstance("SHA-256"): tao doi tuong ma hoa SHA-256
            // dataToHash.getBytes(StandardCharsets.UTF_8): chuyen du lieu thanh byte
            // md.digest(): ma hoa du lieu
            // StringBuilder: de luu tru du lieu
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String dataToHash = username + data;
            // byte[] hash: luu tru du lieu sau khi ma hoa
            byte[] hash = md.digest(dataToHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();// tao chuoi
            // byte b: duyet qua tung byte trong mang hash
            for (byte b : hash)
                // String.format("%02x", b): dinh dang du lieu
                sb.append(String.format("%02x", b));
            // sb.toString(): chuyen du lieu thanh string
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Không thể mã hóa mật khẩu", e);
        }
    }
}
