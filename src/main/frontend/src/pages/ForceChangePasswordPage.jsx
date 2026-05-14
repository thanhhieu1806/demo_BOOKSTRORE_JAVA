import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';
import { Eye, EyeOff } from 'lucide-react';

const ForceChangePasswordPage = () => {
    const { user, clearMustChangePassword, logout } = useAuth();
    const navigate = useNavigate();
    const [form, setForm] = useState({
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
    });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [showCurrentPwd, setShowCurrentPwd] = useState(false);
    const [showNewPwd, setShowNewPwd] = useState(false);
    const [showConfirmPwd, setShowConfirmPwd] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!form.currentPassword) {
            setError('Vui lòng nhập mật khẩu hiện tại (mật khẩu tạm từ email)');
            return;
        }
        if (!form.newPassword) {
            setError('Vui lòng nhập mật khẩu mới');
            return;
        }
        if (form.newPassword.length < 6) {
            setError('Mật khẩu mới phải có ít nhất 6 ký tự');
            return;
        }
        if (form.newPassword !== form.confirmPassword) {
            setError('Mật khẩu xác nhận không khớp');
            return;
        }
        if (form.currentPassword === form.newPassword) {
            setError('Mật khẩu mới không được trùng với mật khẩu cũ');
            return;
        }

        setLoading(true);
        try {
            const { ok, data } = await api.forceChangePassword({
                username: user.username,
                currentPassword: form.currentPassword,
                newPassword: form.newPassword,
            });
            if (ok && data.success === 'true') {
                clearMustChangePassword();
            } else {
                setError(data.message || 'Đổi mật khẩu thất bại');
            }
        } catch {
            setError('Không thể kết nối tới server');
        } finally {
            setLoading(false);
        }
    };

    const handleLogout = () => {
        logout();
        navigate('/login', { replace: true });
    };

    return (
        <div className="login-wrapper">
            <div className="login-card" style={{ maxWidth: 460 }}>
                {/* Lock icon */}
                <div className="force-pwd-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" width="28" height="28">
                        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                        <path d="M7 11V7a5 5 0 0110 0v4" />
                    </svg>
                </div>

                <h1 className="login-title">Đổi mật khẩu bắt buộc</h1>
                <p className="login-sub">
                    Đây là lần đầu đăng nhập. Vui lòng thay đổi mật khẩu tạm để bảo mật tài khoản.
                </p>

                <div className="info-box" style={{ marginBottom: 20 }}>
                    <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16" style={{ flexShrink: 0, marginTop: 1 }}>
                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                    </svg>
                    <div>
                        Xin chào <strong>{user?.username}</strong>! Nhập mật khẩu tạm đã gửi qua email, sau đó tạo mật khẩu mới.
                    </div>
                </div>

                <form onSubmit={handleSubmit} className="login-form">
                    {/* Mật khẩu hiện tại */}
                    <div className="field-group">
                        <label>Mật khẩu hiện tại (tạm)</label>
                        <div className="input-wrapper">
                            <input
                                type={showCurrentPwd ? 'text' : 'password'}
                                placeholder="Nhập mật khẩu từ email"
                                value={form.currentPassword}
                                onChange={e => setForm({ ...form, currentPassword: e.target.value })}
                                autoFocus
                            />
                            <button type="button" className="eye-btn" onClick={() => setShowCurrentPwd(!showCurrentPwd)}>
                                {showCurrentPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                            </button>
                        </div>
                    </div>

                    {/* Mật khẩu mới */}
                    <div className="field-group">
                        <label>Mật khẩu mới</label>
                        <div className="input-wrapper">
                            <input
                                type={showNewPwd ? 'text' : 'password'}
                                placeholder="Tối thiểu 6 ký tự"
                                value={form.newPassword}
                                onChange={e => setForm({ ...form, newPassword: e.target.value })}
                            />
                            <button type="button" className="eye-btn" onClick={() => setShowNewPwd(!showNewPwd)}>
                                {showNewPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                            </button>
                        </div>
                    </div>

                    {/* Xác nhận mật khẩu */}
                    <div className="field-group">
                        <label>Xác nhận mật khẩu mới</label>
                        <div className="input-wrapper">
                            <input
                                type={showConfirmPwd ? 'text' : 'password'}
                                placeholder="Nhập lại mật khẩu mới"
                                value={form.confirmPassword}
                                onChange={e => setForm({ ...form, confirmPassword: e.target.value })}
                            />
                            <button type="button" className="eye-btn" onClick={() => setShowConfirmPwd(!showConfirmPwd)}>
                                {showConfirmPwd ? <EyeOff size={16} /> : <Eye size={16} />}
                            </button>
                        </div>
                    </div>

                    {error && <div className="error-box"><span>✕</span> {error}</div>}

                    <button type="submit" className="btn-login" disabled={loading}>
                        {loading ? 'Đang xử lý...' : '🔒 Đổi mật khẩu & Tiếp tục'}
                    </button>

                    <button type="button" className="btn-logout-link" onClick={handleLogout}>
                        ← Đăng xuất
                    </button>
                </form>
            </div>
        </div>
    );
};

export default ForceChangePasswordPage;
