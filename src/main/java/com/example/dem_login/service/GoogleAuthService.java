package com.example.dem_login.service;

// Import các thư viện cần thiết
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.dem_login.dto.Dto;
import com.example.dem_login.model.CustomerAccount;
import com.example.dem_login.model.CustomerProfile;
import com.example.dem_login.model.User;
import com.example.dem_login.repository.CustomerAccountRepository;
import com.example.dem_login.repository.CustomerProfileRepository;
import com.example.dem_login.repository.UserJpaRepository;

@Service
public class GoogleAuthService {

    // Inject repository CustomerAccount để thao tác DB
    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    // Inject repository User để thao tác DB
    @Autowired
    private UserJpaRepository userJpaRepository;

    // Inject repository CustomerProfile
    @Autowired
    private CustomerProfileRepository profileRepo;

    // Hàm xử lý đăng nhập bằng Google
    public Dto.LoginResponse loginWithGoogle(String accessToken) {
        try {
            // Tạo URL gọi API lấy thông tin user từ Google
            URL url = new URL("https://www.googleapis.com/oauth2/v3/userinfo");

            // Mở kết nối HTTP
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Gửi access token lên header Authorization
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);

            // Nếu response không phải 200 (OK) → token lỗi hoặc hết hạn
            if (conn.getResponseCode() != 200) {
                return new Dto.LoginResponse(false, "Token không hợp lệ hoặc đã hết hạn");
            }

            // Đọc dữ liệu trả về từ Google (JSON)
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;

            // Đọc từng dòng và nối lại thành chuỗi JSON
            while ((line = reader.readLine()) != null)
                sb.append(line);

            reader.close();

            // Parse JSON thành Map
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> info = mapper.readValue(sb.toString(), Map.class);

            // Lấy thông tin từ JSON
            String email = (String) info.get("email"); // email user
            String googleId = (String) info.get("sub"); // id Google
            String googleName = (String) info.get("name"); // tên hiển thị

            // Nếu không có email → lỗi
            if (email == null) {
                return new Dto.LoginResponse(false, "Không lấy được email từ Google");
            }

            // Tìm customer account trong database theo email
            Optional<CustomerAccount> existingCust = customerAccountRepository.findByEmail(email);

            CustomerAccount cust;
            boolean isFirstLogin = false; // kiểm tra đăng nhập lần đầu

            // Nếu customer đã tồn tại
            if (existingCust.isPresent()) {
                cust = existingCust.get();// lấy dữ liệu ra

                // Nếu tài khoản bị khóa tạm thời
                if (cust.getStatus() == CustomerAccount.AccountStatus.Temp_Lock)
                    return new Dto.LoginResponse(false, "Tài khoản bị khóa tạm thời");

                // Nếu tài khoản đã bị xóa
                if (cust.getStatus() == CustomerAccount.AccountStatus.Delete)
                    return new Dto.LoginResponse(false, "Tài khoản đã bị xóa");

            } else {
                // Nếu chưa tồn tại → tạo customer account mới
                isFirstLogin = true;

                cust = new CustomerAccount();
                cust.setEmail(email);

                // Tạo username từ email (phần trước @)
                String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9]", "");
                String username = baseUsername;

                int count = 1;

                // Nếu username bị trùng thì thêm số phía sau
                while (customerAccountRepository.existsByUsername(username) || userJpaRepository.existsByUsername(username)) {
                    username = baseUsername + count++;
                }

                cust.setUsername(username);

                // Password giả (không dùng để login)
                cust.setPassword("GOOGLE_AUTH_" + googleId);

                cust.setStatus(CustomerAccount.AccountStatus.ACTIVE); // trạng thái active
                cust.setCounterLogin(0);

                // set thời gian tạo & update
                cust.setCreateDate(LocalDateTime.now());
                cust.setUpdateDate(LocalDateTime.now());

                // Lưu customer vào DB
                customerAccountRepository.save(cust);

                // Tạo CustomerProfile cho user lần đầu đăng nhập
                CustomerProfile profile = new CustomerProfile();

                profile.setUsername(cust.getUsername());

                // Nếu có tên từ Google thì dùng, không thì dùng username
                profile.setCustomerName(googleName != null ? googleName : cust.getUsername());

                profile.setEmail(email);

                profile.setCreateDate(LocalDateTime.now());
                profile.setUpdateDate(LocalDateTime.now());

                // Lưu profile
                profileRepo.save(profile);

                System.out.println("✅ Tạo tài khoản customer mới từ Google: " + email + " / " + googleName);
            }

            // Cập nhật thông tin login
            cust.setLastLoginDate(LocalDateTime.now());
            cust.setCounterLogin(0);
            cust.setUpdateDate(LocalDateTime.now());

            // Lưu lại customer account
            customerAccountRepository.save(cust);

            // Lấy tên hiển thị từ profile
            String displayName = profileRepo.findByUsername(cust.getUsername())
                    .map(p -> p.getCustomerName()) // lấy tên
                    .filter(n -> n != null && !n.isBlank()) // đảm bảo không null/blank
                    .orElse(cust.getUsername()); // fallback username

            // Tạo response trả về cho frontend
            Dto.LoginResponse response = new Dto.LoginResponse(
                    true,
                    "Đăng nhập Google thành công",
                    "USER",
                    cust.getUsername(),
                    false);

            // Nếu lần đầu login → yêu cầu update profile
            response.setMustUpdateProfile(isFirstLogin);

            // Set tên hiển thị (dùng cho sidebar UI)
            response.setDisplayName(displayName);

            return response;

        } catch (Exception e) {
            // Log lỗi
            System.err.println("Lỗi Google Auth: " + e.getMessage());

            // Trả về lỗi cho frontend
            return new Dto.LoginResponse(false, "Lỗi xác thực Google: " + e.getMessage());
        }
    }
}