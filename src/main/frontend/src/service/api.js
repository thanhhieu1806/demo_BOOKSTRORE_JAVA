const BASE = '/dem_login-0.0.1-SNAPSHOT/api';

// Helper lấy role từ localStorage
const getRole = () => {
    try {
        const u = JSON.parse(localStorage.getItem('auth_user'));
        return u?.role || 'USER';
    } catch { return 'USER'; }
};

const api = {
    login: async (username, password) => {
        const res = await fetch(`${BASE}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password }),
        });
        const data = await res.json();
        return { ok: res.ok, data };
    },

    getUsers: async () => {
        const res = await fetch(`${BASE}/users`);
        if (!res.ok) throw new Error('Failed to fetch users');
        return res.json();
    },

    getUserById: async (id) => {
        const res = await fetch(`${BASE}/users/${id}`);
        return res.json();
    },

    addUser: async (payload) => {
        const res = await fetch(`${BASE}/users`, {
            method: "POST",
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });
        return { ok: res.ok, data: await res.json() };
    },

    editUser: async (id, payload) => {
        const res = await fetch(`${BASE}/users/${id}`, {
            method: "PUT",
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });
        return { ok: res.ok, data: await res.json() };
    },

    // Cập nhật profile (USER/REPORT tự đổi email, mật khẩu)
    updateProfile: async (payload) => {
        const res = await fetch(`${BASE}/users/profile`, {
            method: "PATCH",
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });
        return { ok: res.ok, data: await res.json() };
    },

    // Đổi mật khẩu bắt buộc (lần đầu đăng nhập)
    forceChangePassword: async (payload) => {
        const res = await fetch(`${BASE}/users/force-change-password`, {
            method: "POST",
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });
        return { ok: res.ok, data: await res.json() };
    },

    deleteUser: async (id) => {
        const res = await fetch(`${BASE}/users/${id}`, { method: 'DELETE' });
        return { ok: res.ok, data: await res.json() };
    },

    unlockUser: async (id) => {
        const res = await fetch(`${BASE}/users/${id}/unlock`, {
            method: "PATCH"
        });
        return { ok: res.ok, data: await res.json() };
    },

    resetDatabase: async () => {
        const res = await fetch(`${BASE}/admin/reset`, {
            method: "POST"
        });
        return { ok: res.ok, data: await res.json() };
    },

    // ===== CUSTOMER API =====
    getCustomers: async () => {
        const res = await fetch(`${BASE}/customers`);
        if (!res.ok) throw new Error('Failed');
        return res.json();
    },

    addCustomer: async (payload) => {
        const res = await fetch(`${BASE}/customers`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'X-Role': getRole() },
            body: JSON.stringify(payload),
        });
        return { ok: res.ok, data: await res.json() };
    },

    updateCustomer: async (id, payload) => {
        const res = await fetch(`${BASE}/customers/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'X-Role': getRole() },
            body: JSON.stringify(payload),
        });
        return { ok: res.ok, data: await res.json() };
    },

    deleteCustomer: async (id) => {
        const res = await fetch(`${BASE}/customers/${id}`, {
            method: 'DELETE',
            headers: { 'X-Role': getRole() },
        });
        return { ok: res.ok, data: await res.json() };
    },



    // Books
    getBooks: async () => {
        const res = await fetch(`${BASE}/books`);
        return res.json();
    },
    addBook: async (payload) => {
        const res = await fetch(`${BASE}/books`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        return { ok: res.ok, data: await res.json() };
    },
    updateBook: async (id, payload) => {
        const res = await fetch(`${BASE}/books/${id}`, {
            method: 'PUT', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        return { ok: res.ok, data: await res.json() };
    },
    deleteBook: async (id) => {
        const res = await fetch(`${BASE}/books/${id}`, { method: 'DELETE' });
        return { ok: res.ok, data: await res.json() };
    },

    // Orders
    checkout: async (payload) => {
        const res = await fetch(`${BASE}/orders/checkout`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        return { ok: res.ok, data: await res.json() };
    },
    getOrders: async () => {
        const res = await fetch(`${BASE}/orders`);
        return res.json();
    },
    getMyOrders: async (username) => {
        const res = await fetch(`${BASE}/orders/user/${username}`);
        return res.json();
    },
    updateOrderStatus: async (id, status) => {
        const res = await fetch(`${BASE}/orders/${id}/status`, {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status })
        });
        return { ok: res.ok, data: await res.json() };
    },
    updateOrder: async (id, payload) => {
        const res = await fetch(`${BASE}/orders/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        return { ok: res.ok, data: await res.json() };
    },
    deleteOrder: async (id, username) => {
        const res = await fetch(`${BASE}/orders/${id}?username=${encodeURIComponent(username)}`, {
            method: 'DELETE'
        });
        return { ok: res.ok, data: await res.json() };
    },
    getDeliveryProfile: async (username) => {
        const res = await fetch(`${BASE}/orders/profile/${username}`);
        if (!res.ok)
            return null;
        return res.json();
    },

    // Chat
    sendChat: async (payload) => {
        const res = await fetch(`${BASE}/chat/send`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        let data = {};
        try { data = await res.json(); } catch { }
        return { ok: res.ok, data };
    },

    streamChat: async (payload) => {
        return fetch(`${BASE}/chat/stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
    },

    getChatHistory: async (sessionId) => {
        const res = await fetch(`${BASE}/chat/history/${sessionId}`);
        return res.json();
    },
    clearChatHistory: async (sessionId) => {
        const res = await fetch(`${BASE}/chat/history/${sessionId}`, { method: 'DELETE' });
        return res.json();
    },


    // REGISTER

    register: async (payload) => {
        const res = await fetch(`${BASE}/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
        });
        return { ok: res.ok, data: await res.json() };
    },


    //login google 
    loginWithGoogle: async (token) => {
        const res = await fetch(`${BASE}/auth/google`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token })
        });
        return { ok: res.ok, data: await res.json() };
    },

    //upload anh bia
    uploadImage: async (file) => {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch(`${BASE}/files/upload-image`, {
            method: "POST",
            body: formData
        });
        return {
            ok: res.ok, data: await res.json()
        };
    },
    uploadPdf: async (file) => {
        const formData = new FormData();
        formData.append('file', file);
        const res = await fetch(`${BASE}/files/upload-pdf`, {
            method: 'POST',
            body: formData
        });
        let data = {};
        try {
            data = await res.json();
        } catch {
            data = { success: 'false', message: `Upload PDF thất bại (HTTP ${res.status})` };
        }
        return { ok: res.ok, data };
    },
    //hỏi đáp pdf
    askPdf: async (username, question, pdfPath) => {
        const params = new URLSearchParams({
            username: username || 'user',
            question: question || '',
            pdfPath: pdfPath || '',
        });
        const res = await fetch(`${BASE}/chat/ask-pdf?${params.toString()}`, {
            method: 'POST',
            headers: { 'Accept': 'application/json' },
        });
        let data = {};
        try {
            data = await res.json();
        } catch {
            data = { success: false, message: `Hỏi PDF thất bại (HTTP ${res.status})` };
        }
        return { ok: res.ok, data };
    },
}

export default api;
