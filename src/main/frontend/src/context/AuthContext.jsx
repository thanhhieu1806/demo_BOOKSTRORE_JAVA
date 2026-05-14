import { createContext, useState, useContext } from 'react';

// ===== Helper cookie =====

// 1. HÀM LƯU COOKIE
const setCookie = (name, value, days = 7) => {
    // name = "auth_user"
    // value = {username: "admin", role: "ADMIN"}

    // Tính thời gian hết hạn: 7 ngày kể từ bây giờ
    // 864e5 = 86400000 mili giây = 1 ngày
    const expires = new Date(Date.now() + days * 864e5).toUTCString();
    // expires = "Wed, 21 May 2026 07:30:00 GMT"

    // ⭐ JSON.stringify(value) 
    // {username:"admin", role:"ADMIN"} 
    // → '{"username":"admin","role":"ADMIN"}'

    // ⭐ encodeURIComponent(...)
    // '{"username":"admin","role":"ADMIN"}'
    // → '%7B%22username%22%3A%22admin%22%2C%22role%22%3A%22ADMIN%22%7D'
    // (chuyển { thành %7B, " thành %22, : thành %3A, , thành %2C, } thành %7D)

    // ⭐ Ghi cookie vào trình duyệt
    // Kết quả: auth_user=%7B%22username%22%3A%22admin%22...;expires=...;path=/;SameSite=Strict
    document.cookie = `${name}=${encodeURIComponent(JSON.stringify(value))};expires=${expires};path=/;SameSite=Strict`;
};

// 2. HÀM ĐỌC COOKIE
const getCookie = (name) => {
    // Tìm cookie có tên "auth_user" trong document.cookie
    // document.cookie = "auth_user=%7B%22username%22...; other_cookie=value"
    const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
    // match[2] = "%7B%22username%22%3A%22admin%22%2C%22role%22%3A%22ADMIN%22%7D"

    if (match) {
        try {
            // ⭐ decodeURIComponent(...)
            // '%7B%22username%22%3A%22admin%22%2C%22role%22%3A%22ADMIN%22%7D'
            // → '{"username":"admin","role":"ADMIN"}'
            const decoded = decodeURIComponent(match[2]);

            // ⭐ JSON.parse(...)
            // '{"username":"admin","role":"ADMIN"}'
            // → {username: "admin", role: "ADMIN"}
            return JSON.parse(decoded);
        } catch {
            return null;
        }
    }
    return null;
};
// Hàm xóa cookie
const deleteCookie = (name) => {
    // Set thời gian về quá khứ để browser tự xóa
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 UTC;path=/;`;
};

// ===== Tạo Context =====
const AuthContext = createContext(null);

// ===== Provider (bọc app để dùng auth toàn cục) =====
export function AuthProvider({ children }) {

    // State user (lưu thông tin người dùng)
    const [user, setUser] = useState(() => {

        // Ưu tiên lấy từ cookie trước
        return getCookie('auth_user') || (() => {
            try {
                // Nếu không có cookie thì fallback localStorage
                const saved = localStorage.getItem('auth_user');
                return saved ? JSON.parse(saved) : null;
            } catch {
                return null;
            }
        })();
    });

    // ===== LOGIN =====
    const login = (userData) => {
        // Lưu user vào cookie (7 ngày)
        setCookie('auth_user', userData, 7);

        // Lưu thêm vào localStorage (backup)
        localStorage.setItem('auth_user', JSON.stringify(userData));

        // Cập nhật state React
        setUser(userData);
    };

    // ===== Xử lý flag đổi mật khẩu =====
    const clearMustChangePassword = () => {
        if (user) {
            // Clone user và cập nhật lại field
            const updated = { ...user, mustChangePassword: false };

            // Cập nhật lại cookie
            setCookie('auth_user', updated, 7);

            // Cập nhật localStorage
            localStorage.setItem('auth_user', JSON.stringify(updated));

            // Update state
            setUser(updated);
        }
    };

    // ===== LOGOUT =====
    const logout = () => {
        // Xóa cookie
        deleteCookie('auth_user');

        // Xóa localStorage
        localStorage.removeItem('auth_user');

        // Reset state
        setUser(null);
    };

    // ===== Cung cấp dữ liệu cho toàn app =====
    return (
        <AuthContext.Provider value={{ user, login, logout, clearMustChangePassword }}>
            {children}
        </AuthContext.Provider>
    );
}

// Hook custom để dùng auth dễ hơn
export const useAuth = () => useContext(AuthContext);