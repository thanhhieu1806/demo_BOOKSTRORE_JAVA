package com.example.dem_login.service;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import com.example.dem_login.config.OtpCache;

// quan ly otp
@Service
public class OtpService {

    // khai bao bien
    private final OtpCache otpCache;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpCache otpCache, EmailService emailService) {
        this.otpCache = otpCache;
        this.emailService = emailService;
    }

    // tao ma otp 6 so
    private String generateOtpCode() {
        int code = 100000 + random.nextInt(999999);
        return String.valueOf(code);
    }

    // Gửi OTP đến email, lưu vào cache
    public boolean sendOtpToEmail(String email, String username, String role) {
        String otpCode = generateOtpCode();// tạo mã OTP
        // luu vao cache
        otpCache.saveOtp(email, otpCode, username, role);
        // gui email chua otp
        if (emailService.isConfigured()) {// kiểm tra cấu hình email
            return emailService.sendOtpEmail(email, username, otpCode);// gửi email
        }
        return false;
    }

    // xac thuc otp
    public boolean verifyOtp(String email, String otpCode) {
        return otpCache.verifyOtp(email, otpCode);
    }

    // lay username da luu
    public String getSaveUsername(String email) {
        return otpCache.getSaveUsername(email);
        // otpCache: la interface de luu tru otp
        // getSaveUsername: la phuong thuc de lay username
        // email: la tham so truyen vao
    }

    // lay role da luu
    public String getSaveRole(String email) {
        return otpCache.getSaveRole(email);
    }

    // Xóa OTP khỏi cache sau khi dùng xong
    public void clearOtp(String email) {
        otpCache.removeOtp(email, null);
    }
}