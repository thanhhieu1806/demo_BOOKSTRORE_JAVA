import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';
import { Eye, EyeOff } from 'lucide-react';

export default function RegisterPage() {
    const { login } = useAuth();
    const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '', phone: '', address: '' });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [showPass, setShowPass] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.username || !form.email || !form.password) {
            setError('Vui lòng điền đầy đủ thông tin'); return;
        }
        if (form.password.length < 6) {
            setError('Mật khẩu phải ít nhất 6 ký tự'); return;
        }
        if (form.password !== form.confirmPassword) {
            setError('Mật khẩu xác nhận không khớp'); return;
        }
        if (!form.phone.trim()) {
            setError('Vui lòng nhập số điện thoại'); return;
        }
        if (!/^0\d{9}$/.test(form.phone)) {
            setError('Số điện thoại không hợp lệ (VD: 0912345678)'); return;
        }
        setLoading(true); setError('');
        try {
            const { ok, data } = await api.register({
                username: form.username,
                email: form.email,
                password: form.password,
                phone: form.phone,
                address: form.address,
            });
            if (ok && data.success === 'true') {
                // Tự động login sau khi đăng ký
                const res = await api.login(form.username, form.password);
                if (res.ok && res.data.success) {
                    login({
                        username: res.data.username,
                        role: res.data.role,
                        mustChangePassword: false,
                    });
                }
            } else {
                setError(data.message || 'Đăng ký thất bại');
            }
        } catch { setError('Không thể kết nối server'); }
        finally { setLoading(false); }
    };

    return (
        <div className="login-wrapper">
            <div className="login-card" style={{ maxWidth: 440 }}>
                <h1 className="login-title">Đăng ký tài khoản</h1>
                <p className="login-sub">Tạo tài khoản mới để mua sách</p>

                <form onSubmit={handleSubmit} className="login-form">
                    <div className="field-group">
                        <label>Tên đăng nhập</label>
                        <input type="text" placeholder="Nhập username"
                            value={form.username}
                            onChange={e => setForm({ ...form, username: e.target.value })}
                            autoFocus />
                    </div>
                    <div className="field-group">
                        <label>Email</label>
                        <input type="email" placeholder="email@example.com"
                            value={form.email}
                            onChange={e => setForm({ ...form, email: e.target.value })} />
                    </div>
                    <div className="field-group">
                        <label>Số điện thoại <span className="req" style={{color: 'red'}}>*</span></label>
                        <input type="text"
                            placeholder="0xxxxxxxxx"
                            value={form.phone}
                            onChange={e => setForm({ ...form, phone: e.target.value })} />
                    </div>
                    <div className="field-group">
                        <label>Địa chỉ</label>
                        <input type="text"
                            placeholder="Số nhà, đường, phường, quận, tỉnh"
                            value={form.address}
                            onChange={e => setForm({ ...form, address: e.target.value })} />
                    </div>
                    <div className="field-group">
                        <label>Mật khẩu</label>
                        <div className="input-wrapper">
                            <input type={showPass ? 'text' : 'password'}
                                placeholder="Tối thiểu 6 ký tự"
                                value={form.password}
                                onChange={e => setForm({ ...form, password: e.target.value })} />
                            <button type="button" className="eye-btn"
                                onClick={() => setShowPass(s => !s)}>
                                {showPass ? <EyeOff size={16} /> : <Eye size={16} />}
                            </button>
                        </div>
                    </div>
                    <div className="field-group">
                        <label>Xác nhận mật khẩu</label>
                        <input type="password" placeholder="Nhập lại mật khẩu"
                            value={form.confirmPassword}
                            onChange={e => setForm({ ...form, confirmPassword: e.target.value })} />
                    </div>

                    {error && <div className="error-box">{error}</div>}

                    <button type="submit" className="btn-login" disabled={loading}>
                        {loading ? 'Đang xử lý...' : 'Đăng ký'}
                    </button>

                    <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13, color: 'var(--text-dim)' }}>
                        Đã có tài khoản?{' '}
                        <a href="/dem_login-0.0.1-SNAPSHOT/login"
                            style={{ color: 'var(--accent)', fontWeight: 600, textDecoration: 'none' }}>
                            Đăng nhập
                        </a>
                    </div>
                </form>
            </div>
        </div>
    );
}