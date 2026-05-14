package com.example.dem_login.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

// cache lưu OTP
@Component
public class OtpCache {
    // Lưu otp theo email: Otp info gồm mã và thời gian hết hạn

    // lớp nội bộ
    private static class OtpData {
        String code;// otpCode: ma xac nhan
        long expireTime;// expireTime: thoi gian het han
        String username;
        String role;

        // lưu cache: dùng map email -> OtpData
        OtpData(String code, long expireTime, String username, String role) {
            // this. thuoc tinh
            this.code = code;
            this.expireTime = expireTime;
            this.username = username;
            this.role = role;
        }
    }

    // otpStore: luu otp theo email
    // concurrentHashMap: luu data an toàn cho đa luồng
    private final Map<String, OtpData> otpStore = new ConcurrentHashMap<>();

    // OTP_EXPRIE_MINUTES: thoi gian het han cua OTP
    private static final long OTP_EXPRIE_MINUTES = 1;

    // luu otp cho tài khoản mail
    public void saveOtp(String email, String otpCode, String username, String role) {
        // System.currentTimeMillis(): tra thoi gian hien tai tinh theo mili giay
        // 60 * 1000: chuyen sang mili giay
        // expireTime: thoi gian het han cua OTP
        long expireTime = System.currentTimeMillis() + (OTP_EXPRIE_MINUTES * 60 * 1000);
        // otpStore: luu otp theo email
        // concurrentHashMap: luu data an toàn cho đa luồng
        otpStore.put(email, new OtpData(otpCode, expireTime, username, role));
    }

    // lay OTP data theo email
    public OtpData getOtp(String email) {
        // otpStore.get(email): lay data theo email
        OtpData data = otpStore.get(email);
        if (data == null)
            return null;

        // kiem tra thời gian hết hạn
        if (System.currentTimeMillis() > data.expireTime) {
            otpStore.remove(email);
            return null;
        }
        return data;
    }

    // xoa otp sau khi sac thuc thanh cong
    public void removeOtp(String email, String otpCode) {
        otpStore.remove(email);
    }

    // kiem ttra otp co hople khong
    public boolean verifyOtp(String email, String otpCode) {
        OtpData data = getOtp(email);// lay OTP data theo email
        if (data == null)// kiem tra thoi gian het han
            return false;
        // so sánh otpCode
        // equals: so sánh chuỗi
        // data.code: mã OTP lưu trong cache
        // otpCode: mã OTP người dùng nhập
        return data.code.equals(otpCode);
    }

    // lay thong tin user da luu
    public String getSaveUsername(String email) {
        OtpData data = getOtp(email);// lay OTP data theo email
        // data != null ? data.username : null: neu data khac null thi tra ve username,
        // nguoc lai tra ve null
        // data.username: username da luu trong cache
        // cache: la gi: luu tam thoi trong bo nho
        return data != null ? data.username : null;
    }

    // lay thong tin role da luu
    public String getSaveRole(String email) {
        OtpData data = getOtp(email);// lay OTP data theo email
        return data != null ? data.role : null;// data.role: role da luu trong cache
    }
}
