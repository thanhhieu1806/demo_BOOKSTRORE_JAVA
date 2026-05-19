package com.example.dem_login.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.dem_login.model.User;
import com.example.dem_login.model.CustomerProfile;
import com.example.dem_login.dto.Dto;
import com.example.dem_login.repository.UserJpaRepository;
import com.example.dem_login.repository.CustomerProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import java.util.Collections;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    @Autowired
    private UserJpaRepository userRepository; // ← JPA thay JSON

    @Autowired
    private CustomerProfileRepository profileRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private EmailService EmailService;

    @Value("${max_login_fail:5}")
    private int maxLoginFail;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final SecureRandom Radom = new SecureRandom();

    // LOGIN
    public Dto.LoginResponse Login(String username, String password) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty())
            return new Dto.LoginResponse(false, "Username không tồn tại");

        User user = opt.get();

        if (user.getStatus() == User.UserStatus.Temp_Lock)
            return new Dto.LoginResponse(false, "Tài khoản đã bị khóa tạm thời.");
        if (user.getStatus() == User.UserStatus.Delete)
            return new Dto.LoginResponse(false, "Tài khoản không tồn tại.");

        String inputPassword = hashPassword(username + password);
        if (!user.getPassword().equals(inputPassword)) {
            user.setCounterLogin(user.getCounterLogin() + 1);
            user.setUpdateDate(LocalDateTime.now());
            if (user.getCounterLogin() >= maxLoginFail) {
                user.setStatus(User.UserStatus.Temp_Lock);
                userRepository.save(user);
                return new Dto.LoginResponse(false, "Sai mật khẩu quá " + maxLoginFail + " lần. Tài khoản bị khóa!");
            }
            userRepository.save(user);
            int remaining = maxLoginFail - user.getCounterLogin();
            return new Dto.LoginResponse(false, "Sai mật khẩu! Còn " + remaining + " lần thử.");
        }

        user.setCounterLogin(0);
        user.setLastLoginDate(LocalDateTime.now());
        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user);

        String roleLogin = user.getRole().name();
        Dto.LoginResponse response = new Dto.LoginResponse(true, "Đăng nhập thành công", roleLogin,
                user.getUsername(), user.isMustChangePassword());

        // Lấy tên hiển thị từ CustomerProfile nếu có
        profileRepository.findByUsername(user.getUsername())
                .map(CustomerProfile::getCustomerName)
                .filter(name -> name != null && !name.isBlank())
                .ifPresent(response::setDisplayName);

        return response;
    }

    // REGISTER (đăng ký tài khoản mới)
    public Map<String, String> register(Dto.RegisterRequest req) {
        // Validate
        if (req.getUsername() == null || req.getUsername().isBlank())
            return Map.of("success", "false", "message", "Username không được trống!");
        if (req.getEmail() == null || req.getEmail().isBlank())
            return Map.of("success", "false", "message", "Email không được trống!");
        if (req.getPassword() == null || req.getPassword().length() < 6)
            return Map.of("success", "false", "message", "Mật khẩu phải có ít nhất 6 ký tự!");
        if (req.getPhone() == null || req.getPhone().isBlank())
            return Map.of("success", "false", "message", "Số điện thoại không được trống!");
        if (userRepository.existsByUsername(req.getUsername()))
            return Map.of("success", "false", "message", "Username đã tồn tại!");
        if (userRepository.existsByEmail(req.getEmail()))
            return Map.of("success", "false", "message", "Email đã được sử dụng!");

        // Tạo user mới → lưu vào SQL Server
        User user = new User();
        user.setUsername(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setAddress(req.getAddress() != null ? req.getAddress() : "");
        user.setPassword(hashPassword(req.getUsername() + req.getPassword()));
        user.setRole(User.UserRole.USER);
        user.setStatus(User.UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        user.setCounterLogin(0);
        user.setCreateDate(LocalDateTime.now());
        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user); // ← lưu vào SQL Server

        // Lưu thông tin giao hàng vào customer_profiles
        CustomerProfile profile = new CustomerProfile();
        profile.setUsername(req.getUsername());
        profile.setCustomerName(
                req.getFullName() != null && !req.getFullName().isBlank() ? req.getFullName() : req.getUsername());
        profile.setPhone(req.getPhone());
        profile.setAddress(req.getAddress() != null ? req.getAddress() : "");
        profile.setEmail(req.getEmail());
        profile.setCreateDate(LocalDateTime.now());
        profile.setUpdateDate(LocalDateTime.now());
        profileRepository.save(profile); // ← lưu vào SQL Server

        return Map.of("success", "true", "message", "Đăng ký thành công!");
    }

    // GET ALL USERS
    public List<Dto.UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getStatus() != User.UserStatus.Delete)
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    // GET USER BY ID ─
    public Dto.UserResponse getUserById(Long id) {
        return userRepository.findById(id).map(this::toUserResponse).orElse(null);
    }

    // ADD USER (ADMIN tạo) ─
    public Dto.CreateUserResponse addUser(Dto.CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername()))
            return createFailedResponse(false, "Username đã tồn tại", null, req.getEmail(), req.getUsername());
        if (userRepository.existsByEmail(req.getEmail()))
            return createFailedResponse(false, "Email đã tồn tại", null, req.getEmail(), req.getUsername());

        String rawPassword = generatePassword();
        User user = new User();
        user.setEmail(req.getEmail());
        user.setUsername(req.getUsername());
        user.setPassword(hashPassword(req.getUsername() + rawPassword));
        user.setRole(parseRole(req.getRole()));
        user.setStatus(User.UserStatus.ACTIVE);
        user.setCounterLogin(0);
        user.setCreateDate(LocalDateTime.now());
        user.setUpdateDate(LocalDateTime.now());
        user.setMustChangePassword(true);

        boolean emailSent = false;
        if (EmailService.isConfigured()) {
            emailSent = EmailService.sendNewAccountPassword(req.getEmail(), req.getUsername(), rawPassword);
            if (!emailSent)
                return new Dto.CreateUserResponse(false,
                        "Email không hợp lệ hoặc không thể gửi.", false, null, req.getEmail(), req.getUsername());
        }

        userRepository.save(user); // ← lưu vào SQL Server

        if (emailSent)
            return new Dto.CreateUserResponse(true,
                    "Tạo tài khoản thành công. Mật khẩu đã gửi email: " + req.getEmail(),
                    true, null, req.getEmail(), req.getUsername());
        else
            return new Dto.CreateUserResponse(true,
                    "Tạo tài khoản thành công. Mật khẩu tạm: " + rawPassword,
                    false, rawPassword, req.getEmail(), req.getUsername());
    }

    // EDIT USER
    public Map<String, String> editUser(Long id, Dto.EditUserRequest req) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty())
            return Map.of("success", "false", "message", "Không tìm thấy user");

        User user = opt.get();
        if (req.getEmail() != null && !req.getEmail().isBlank())
            user.setEmail(req.getEmail());
        if (req.getRole() != null && !req.getRole().isBlank())
            user.setRole(parseRole(req.getRole()));
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            try {
                user.setStatus(User.UserStatus.valueOf(req.getStatus()));
            } catch (Exception e) {
                return Map.of("success", "false", "message", "Trạng thái không hợp lệ");
            }
        }
        if (req.isResetCounter())
            user.setCounterLogin(0);
        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user);
        return Map.of("success", "true", "message", "Cập nhật thành công!");
    }

    // DELETE USER
    public Map<String, String> deleteUser(Long id) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty())
            return Map.of("success", "false", "message", "Không tìm thấy user");
        User user = opt.get();
        user.setStatus(User.UserStatus.Delete);
        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user);
        return Map.of("success", "true", "message", "Xóa tài khoản thành công!");
    }

    // UNLOCK USER
    public Map<String, String> unLockUser(Long id) {
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty())
            return Map.of("success", "false", "message", "Không tìm thấy user");
        User user = opt.get();
        user.setStatus(User.UserStatus.ACTIVE);
        user.setCounterLogin(0);
        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user);
        return Map.of("success", "true", "message", "Mở khóa thành công!");
    }

    // UPDATE PROFILE ─
    public Map<String, String> updateProfile(Dto.UpdateProfileRequest req) {
        Optional<User> opt = userRepository.findByUsername(req.getUsername());
        if (opt.isEmpty())
            return Map.of("success", "false", "message", "Không tìm thấy user");

        User user = opt.get();
        if (req.getEmail() != null && !req.getEmail().isBlank())
            user.setEmail(req.getEmail());

        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getCurrentPassword() == null || req.getCurrentPassword().isBlank())
                return Map.of("success", "false", "message", "Vui lòng nhập mật khẩu hiện tại");
            String hashedCurrent = hashPassword(user.getUsername() + req.getCurrentPassword());
            if (!user.getPassword().equals(hashedCurrent))
                return Map.of("success", "false", "message", "Mật khẩu hiện tại không đúng");
            if (req.getNewPassword().length() < 6)
                return Map.of("success", "false", "message", "Mật khẩu mới phải có ít nhất 6 ký tự");
            user.setPassword(hashPassword(user.getUsername() + req.getNewPassword()));
        }

        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user);
        return Map.of("success", "true", "message", "Cập nhật thành công!");
    }

    // FORCE CHANGE PASSWORD
    public Map<String, String> forceChangePassword(Dto.ForceChangePasswordRequest req) {
        Optional<User> opt = userRepository.findByUsername(req.getUsername());
        if (opt.isEmpty())
            return Map.of("success", "false", "message", "Không tìm thấy user");

        User user = opt.get();
        if (!user.isMustChangePassword())
            return Map.of("success", "false", "message", "Tài khoản không cần đổi mật khẩu");

        String hashedCurrent = hashPassword(user.getUsername() + req.getCurrentPassword());
        if (!user.getPassword().equals(hashedCurrent))
            return Map.of("success", "false", "message", "Mật khẩu hiện tại không đúng");
        if (req.getNewPassword() == null || req.getNewPassword().length() < 6)
            return Map.of("success", "false", "message", "Mật khẩu mới phải có ít nhất 6 ký tự");
        if (req.getCurrentPassword().equals(req.getNewPassword()))
            return Map.of("success", "false", "message", "Mật khẩu mới không được trùng mật khẩu cũ");

        user.setPassword(hashPassword(user.getUsername() + req.getNewPassword()));
        user.setMustChangePassword(false);
        user.setUpdateDate(LocalDateTime.now());
        userRepository.save(user);
        return Map.of("success", "true", "message", "Đổi mật khẩu thành công!");
    }

    // RESET DATABASE ─
    public Map<String, String> resetDatabase() {
        userRepository.deleteAll();
        seedDefaultUsers();
        return Map.of("success", "true", "message", "Reset database thành công!");
    }

    private void seedDefaultUsers() {
        createDefaultUser("admin@gmail.com", "admin", "123456", User.UserRole.ADMIN);
        createDefaultUser("user@gmail.com", "user", "123456", User.UserRole.USER);
        createDefaultUser("report@gmail.com", "report", "123456", User.UserRole.REPORT);
    }

    private void createDefaultUser(String email, String username, String password, User.UserRole role) {
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
        userRepository.save(user);
    }

    // HELPERS
    private String hashPassword(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi mã hóa mật khẩu", e);
        }
    }

    private String generatePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        sb.append(randomChar("0123456789"));
        sb.append(randomChar("ABCDEFGHIJKLMNOPQRSTUVWXYZ"));
        sb.append(randomChar("abcdefghijklmnopqrstuvwxyz"));
        for (int i = 3; i < 9; i++)
            sb.append(chars.charAt(Radom.nextInt(chars.length())));
        List<Character> list = sb.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
        Collections.shuffle(list);
        return list.stream().map(String::valueOf).collect(Collectors.joining());
    }

    private char randomChar(String chars) {
        return chars.charAt(Radom.nextInt(chars.length()));
    }

    private Dto.UserResponse toUserResponse(User u) {
        Dto.UserResponse r = new Dto.UserResponse();
        r.setId(u.getId());
        r.setEmail(u.getEmail());
        r.setUsername(u.getUsername());
        r.setStatus(u.getStatus().name());
        r.setRole(u.getRole().name());
        r.setCounterLogin(u.getCounterLogin());
        r.setLastLoginDate(u.getLastLoginDate() != null ? u.getLastLoginDate().format(FMT) : null);
        r.setCreatedDate(u.getCreateDate() != null ? u.getCreateDate().format(FMT) : null);
        r.setUpdatedDate(u.getUpdateDate() != null ? u.getUpdateDate().format(FMT) : null);
        return r;
    }

    private User.UserRole parseRole(String rawRole) {
        return User.UserRole.valueOf(rawRole.trim().toUpperCase());
    }

    private Dto.CreateUserResponse createFailedResponse(boolean success, String message,
            String tempPass, String email, String username) {
        return new Dto.CreateUserResponse(success, message, false, tempPass, email, username);
    }

    // GET CART DATA
    public String getCartData(String username) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty())
            return "[]";
        return opt.get().getCartData();
    }

    // SYNC CART DATA
    public Map<String, String> syncCartData(Dto.SyncCartRequest req) {
        Optional<User> opt = userRepository.findByUsername(req.getUsername());
        if (opt.isEmpty())
            return Map.of("success", "false", "message", "User không tồn tại");
        User user = opt.get();
        user.setCartData(req.getCartData());
        userRepository.save(user);
        return Map.of("success", "true", "message", "Đồng bộ giỏ hàng thành công!");
    }
}