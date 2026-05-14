package com.example.dem_login.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import jakarta.mail.MessagingException;
import org.springframework.mail.MailException;

@Service // de xac dinh class nay la service
public class EmailService {
    // JavaMailSender là 1 interface de gui email
    private final JavaMailSender mailSender; // de gui email

    // @Value de doc du lieu tu file application.properties
    @Value("${spring.mail.username:}") // lay application.properties
    private String from;

    @Value("${app.login-url:http://localhost:8000}") // link login
    private String loginUrl;

    public EmailService(JavaMailSender mailSender) { // duong dan de truy cap class java mail sender
        this.mailSender = mailSender;
    }

    // kiem tra smtp da duoc cau hinh chua
    // isConfigured: kiem tra trong application.properties
    public boolean isConfigured() {
        // isblank() kiem tra xem chuoi co trong hay khong
        return from != null && !from.isBlank();
    }

    // gui email html voi mat khau random cho user moi
    public boolean sendNewAccountPassword(String to, String userName, String rawPassword) {
        if (!isConfigured()) {
            System.out.println("[EmailService]: Configured email failt");
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Tài khoản đăng nhập");
            helper.setText(buildHtml(userName, rawPassword), true);
            mailSender.send(message);
            return true;
        } catch (MessagingException | MailException e) {
            System.err.println("[EmailService]:Loi khi gui email den " + to + ": " + e.getMessage());
            return false;
        }
    }

    // giao dien doan gui mail
    private String buildHtml(String username, String rawPassword) {
        return "<!DOCTYPE html>"
                + "<html lang='vi'><head><meta charset='UTF-8'></head>"
                + "<body style='margin:0;padding:0;background:#f4f6f9;"
                + "font-family:Segoe UI,Arial,sans-serif;'>"
                + "<table width='100%' cellpadding='0' cellspacing='0'"
                + " style='background:#f4f6f9;padding:40px 0;'>"
                + "<tr><td align='center'>"
                + "<table width='520' cellpadding='0' cellspacing='0'"
                + " style='background:#fff;border-radius:12px;"
                + "box-shadow:0 2px 12px rgba(0,0,0,0.08);overflow:hidden;'>"

                // HEADER
                + "<tr><td style='background:linear-gradient(135deg,#6366f1,#4f46e5);"
                + "padding:32px 40px;text-align:center;'>"
                + "<h1 style='margin:0;color:#fff;font-size:22px;font-weight:700;'>"
                + "Demo Login System</h1>"
                + "<p style='margin:6px 0 0;color:rgba(255,255,255,0.85);font-size:13px;'>"
                + "Thông tin tài khoản mới của bạn</p>"
                + "</td></tr>"

                // BODY
                + "<tr><td style='padding:36px 40px;'>"
                + "<p style='margin:0 0 16px;color:#374151;font-size:15px;'>Xin chào "
                + "<strong>" + username + "</strong>,</p>"
                + "<p style='margin:0 0 24px;color:#6b7280;font-size:14px;line-height:1.7;'>"
                + "Tài khoản của bạn đã được tạo thành công. Dưới đây là thông tin đăng nhập:</p>"

                // Credentials box
                + "<table width='100%' cellpadding='0' cellspacing='0'"
                + " style='background:#f8faff;border:1.5px solid #e0e7ff;"
                + "border-radius:10px;margin-bottom:24px;'>"
                + "<tr><td style='padding:20px 24px;'>"
                + "<table width='100%' cellpadding='6' cellspacing='0'>"
                + "<tr>"
                + "<td style='color:#6b7280;font-size:13px;width:140px;'>Tên đăng nhập</td>"
                + "<td style='color:#111827;font-size:14px;font-weight:600;'>" + username + "</td>"
                + "</tr>"
                + "<tr>"
                + "<td style='color:#6b7280;font-size:13px;"
                + "border-top:1px solid #e5e7eb;padding-top:12px;'>Mật khẩu tạm thời</td>"
                + "<td style='border-top:1px solid #e5e7eb;padding-top:12px;'>"
                + "<span style='font-family:Courier New,monospace;font-size:18px;"
                + "font-weight:700;color:#6366f1;letter-spacing:2px;"
                + "background:#ede9fe;padding:4px 10px;border-radius:6px;'>"
                + rawPassword + "</span>"
                + "</td>"
                + "</tr>"
                + "</table></td></tr></table>"

                // Warning
                + "<table width='100%' cellpadding='0' cellspacing='0'"
                + " style='background:#fffbeb;border:1px solid #fde68a;"
                + "border-radius:8px;margin-bottom:24px;'>"
                + "<tr><td style='padding:14px 18px;color:#92400e;font-size:13px;line-height:1.6;'>"
                + "⚠️ <strong>Lưu ý:</strong> Vui lòng đổi mật khẩu ngay sau khi đăng nhập "
                + "lần đầu để bảo mật tài khoản."
                + "</td></tr></table>"

                // Login button
                + "<div style='text-align:center;margin-bottom:24px;'>"
                + "<a href='" + loginUrl + "' style='display:inline-block;background:#4f46e5;color:#ffffff;"
                + "text-decoration:none;padding:12px 20px;border-radius:8px;font-size:14px;font-weight:600;'>"
                + "Đăng nhập ngay"
                + "</a>"
                + "</div>"

                + "<p style='margin:0;color:#9ca3af;font-size:12px;'>"
                + "Nếu bạn không yêu cầu tạo tài khoản này, vui lòng liên hệ quản trị viên.</p>"
                + "</td></tr>"

                // FOOTER
                + "<tr><td style='background:#f9fafb;border-top:1px solid #e5e7eb;"
                + "padding:18px 40px;text-align:center;'>"
                + "<p style='margin:0;color:#9ca3af;font-size:12px;'>"
                + "© 2025 Demo Login System — Email tự động, vui lòng không reply.</p>"
                + "</td></tr>"

                + "</table></td></tr></table>"
                + "</body></html>";
    }

    // gui email chua otp
    public boolean sendOtpEmail(String to, String username, String otpCode) {
        if (!isConfigured()) {// kiểm tra cấu hình
            System.out.println("[EmailService]:Email chưa được cấu hình");
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage(); // tạo message
            // true : cho phép gửi email có tệp đính kèm
            // UTF-8: mã hóa ký tự

            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");// tạo helper
            helper.setFrom(from);// set from
            helper.setTo(to);// set to
            helper.setSubject("Mã xác thực OTP");// set subject
            // helper la gi : dung de tao va gui email
            // buildOtpHtml(username, otpCode): tra ve chuoi html chua thong tin
            // true : cho phép gửi email có tệp đính kèm
            helper.setText(buildOtpHtml(username, otpCode), true);

            mailSender.send(message);// gui message
            return true;
        } catch (Exception e) {
            System.err.println("[EmailService]:Loi khi gui email den " + to + ": " + e.getMessage());
            return false;
        }
    }

    // HTML cho email OTP
    private String buildOtpHtml(String username, String otpCode) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head>"
                + "<body style='font-family: Arial, sans-serif;'>"
                + "<div style='max-width: 500px; margin: 0 auto; padding: 20px;'>"
                + "<h2 style='color: #4f46e5;'>Xác thực tạo tài khoản</h2>"
                + "<p>Xin chào <strong>" + username + "</strong>,</p>"
                + "<p>Mã OTP xác thực của bạn là:</p>"
                + "<div style='background: #f3f4f6; padding: 15px; text-align: center; font-size: 32px; "
                + "letter-spacing: 5px; font-weight: bold; border-radius: 8px;'>"
                + otpCode
                + "</div>"
                + "<p>Mã này có hiệu lực trong <strong>5 phút</strong>.</p>"
                + "<p>Nếu bạn không yêu cầu tạo tài khoản, vui lòng bỏ qua email này.</p>"
                + "<hr>"
                + "<p style='color: #6b7280; font-size: 12px;'>Email tự động, vui lòng không reply.</p>"
                + "</div>"
                + "</body></html>";
    }

}
