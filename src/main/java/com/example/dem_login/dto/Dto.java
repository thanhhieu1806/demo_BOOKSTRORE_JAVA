package com.example.dem_login.dto;

//file nay dung de gui du lieu fe <-> be

//  LOGIN 
//class : dinh nghia du lieu
//request yeu cau client
//response server tra ve 

//truyen du lieu
public class Dto {

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class LoginResponse {
        private boolean success;
        private String message;
        private String role;
        private String username;
        private boolean mustChangePassword;
        private boolean mustUpdateProfile; // bắt buộc cập nhật thông tin lần đầu (Google)
        private String displayName; // tên hiển thị (Google name hoặc customerName)

        public LoginResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public LoginResponse(boolean success, String message, String role, String username) {
            this.success = success;
            this.message = message;
            this.role = role;
            this.username = username;
        }

        public LoginResponse(boolean success, String message, String role, String username,
                boolean mustChangePassword) {
            this.success = success;
            this.message = message;
            this.role = role;
            this.username = username;
            this.mustChangePassword = mustChangePassword;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getRole() {
            return role;
        }

        public String getUsername() {
            return username;
        }

        public boolean isMustChangePassword() {
            return mustChangePassword;
        }

        public boolean isMustUpdateProfile() {
            return mustUpdateProfile;
        }

        public void setMustUpdateProfile(boolean mustUpdateProfile) {
            this.mustUpdateProfile = mustUpdateProfile;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    // CREATE USER
    public static class CreateUserRequest {
        private String email;
        private String username;
        private String role;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class CreateUserResponse {
        private boolean success;
        private String message;
        private boolean emailSent;
        private String tempPassword;
        private String email;
        private String username;

        public CreateUserResponse() {

        }

        public CreateUserResponse(boolean success, String message, boolean emailSent,
                String tempPassword, String email, String username) {
            this.success = success;
            this.message = message;
            this.emailSent = emailSent;
            this.tempPassword = tempPassword;
            this.email = email;
            this.username = username;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public boolean isEmailSent() {
            return emailSent;
        }

        public String getTempPassword() {
            return tempPassword;
        }

        public String getEmail() {
            return email;
        }

        public String getUsername() {
            return username;
        }
    }

    // UPDATE PROFILE (USER/REPORT tự đổi email, mật khẩu)
    public static class UpdateProfileRequest {
        private String username; // username hiện tại (để xác định user)
        private String email; // email mới (có thể null nếu không đổi)
        private String currentPassword; // mật khẩu hiện tại (bắt buộc khi đổi mật khẩu)
        private String newPassword; // mật khẩu mới (có thể null nếu không đổi)

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    // FORCE CHANGE PASSWORD (đổi mật khẩu bắt buộc lần đầu)
    public static class ForceChangePasswordRequest {
        private String username;
        private String currentPassword;
        private String newPassword;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    // EDIT USER
    public static class EditUserRequest {
        private String email;
        private String role;
        private String status;
        private boolean resetCounter;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isResetCounter() {
            return resetCounter;
        }

        public void setResetCounter(boolean resetCounter) {
            this.resetCounter = resetCounter;
        }
    }

    // USER RESPONSE
    public static class UserResponse {
        private Long id;
        private String email;
        private String username;
        private String status;
        private String role;
        private int counterLogin;
        private String lastLoginDate;
        private String createdDate;
        private String updatedDate;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public int getCounterLogin() {
            return counterLogin;
        }

        public void setCounterLogin(int counterLogin) {
            this.counterLogin = counterLogin;
        }

        public String getLastLoginDate() {
            return lastLoginDate;
        }

        public void setLastLoginDate(String lastLoginDate) {
            this.lastLoginDate = lastLoginDate;
        }

        public String getCreatedDate() {
            return createdDate;
        }

        public void setCreatedDate(String createdDate) {
            this.createdDate = createdDate;
        }

        public String getUpdatedDate() {
            return updatedDate;
        }

        public void setUpdatedDate(String updatedDate) {
            this.updatedDate = updatedDate;
        }
    }

    // === OTP SEND REQUEST ===
    // Controller dùng tên Dto.OtpSendRequest
    public static class OtpSendRequest {
        private String email;
        private String username;
        private String role;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    // === OTP SEND RESPONSE ===
    // Phải nằm ngoài, là class riêng biệt (không lồng trong class khác)
    public static class OtpSendResponse {
        private boolean success;
        private String message;
        private String email;

        public OtpSendResponse(boolean success, String message, String email) {
            this.success = success;
            this.message = message;
            this.email = email;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getEmail() {
            return email;
        }
    }

    // === OTP VERIFY + CREATE USER REQUEST ===
    // Sửa lỗi typo: CreatRequest -> CreateRequest (khớp với Controller)
    public static class OtpVerifyAndCreateRequest {
        private String email;
        private String username;
        private String role;
        private String otpCode;// 6 so otp

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getOtpCode() {
            return otpCode;
        }

        public void setOtpCode(String otpCode) {
            this.otpCode = otpCode;
        }
    }

    // ===CUSTOMER ===
    public static class CustomerRequest {
        private String fullName;
        private String phone;
        private String email;
        private String address;
        private String decription;
        private String status;

        // Getter va Setter
        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;

        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getDecription() {
            return decription;
        }

        public void setDecription(String decription) {
            this.decription = decription;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class CustomerResponse {
        private Long id;
        private String fullName;
        private String phone;
        private String email;
        private String address;
        private String decription;
        private String status;
        private String createDate;
        private String updateDate;

        // getter vs setter
        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getDecription() {
            return decription;
        }

        public void setDecription(String decription) {
            this.decription = decription;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCreateDate() {
            return createDate;
        }

        public void setCreateDate(String createDate) {
            this.createDate = createDate;
        }

        public String getUpdateDate() {
            return updateDate;
        }

        public void setUpdateDate(String updateDate) {
            this.updateDate = updateDate;
        }

    }

    public static class ChatRequest {
        private String username;
        private String sessionId;
        private String message;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

    }

    // chat response
    public static class ChatResponse {
        private boolean success;
        private String message;// cau tra loi cua ai
        private String sessionId;

        public ChatResponse(boolean success, String message, String sessionId) {
            this.success = success;
            this.message = message;// cau tra loi cua ai
            this.sessionId = sessionId;
        }

        public static ChatResponse ok(String message, String sessionId) {
            return new ChatResponse(true, message, sessionId);
        }

        public static ChatResponse error(String message, String sessionId) {
            return new ChatResponse(false, message, sessionId);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getSessionId() {
            return sessionId;
        }
    }

    public static class ChatHistoryItem {
        private String role;
        private String message;
        private String createDate;

        public ChatHistoryItem(String role, String message, String createDate) {
            this.role = role;
            this.message = message;
            this.createDate = createDate;
        }

        public String getRole() {
            return role;
        }

        public String getMessage() {
            return message;
        }

        public String getCreateDate() {
            return createDate;
        }
    }

    public static class RegisterRequest {
        private String username;
        private String fullName;
        private String phone;
        private String email;
        private String password;
        private String address;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }
    }

    // SYNC CART
    public static class SyncCartRequest {
        private String username;
        private String cartData;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getCartData() {
            return cartData;
        }

        public void setCartData(String cartData) {
            this.cartData = cartData;
        }
    }

    // request verify google idToken
    public static class GoogleLoginRequest {
        private String token;// ID tken từ gg

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

    }
}
