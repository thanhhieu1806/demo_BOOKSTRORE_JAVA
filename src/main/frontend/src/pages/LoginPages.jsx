import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';
import { Eye, EyeOff } from 'lucide-react';
import { useGoogleLogin } from '@react-oauth/google';

const LoginPages = () => {
    const { login } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    
    const [form, setForm] = useState({ username: '', password: '' });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [showPass, setShowPass] = useState(false);

    // Lấy đường dẫn redirect sau khi đăng nhập
    const from = location.state?.from?.pathname || 
                 localStorage.getItem('redirectAfterLogin') || 
                 '/cart';

    useEffect(() => {
        // Xóa redirect sau khi đọc
        localStorage.removeItem('redirectAfterLogin');
    }, []);

    const handleGoogleLogin = useGoogleLogin({
        onSuccess: async (tokenResponse) => {
            setLoading(true);
            setError('');
            try {
                const { ok, data } = await api.loginWithGoogle(tokenResponse.access_token);
                if (ok && data.success) {
                    login({
                        username: data.username,
                        role:     data.role,
                        mustChangePassword: false,
                        displayName: data.displayName || data.username,
                    });
                    // Nếu là lần đầu đăng nhập bằng Google → bắt buộc cập nhật thông tin
                    if (data.mustUpdateProfile) {
                        localStorage.setItem('mustUpdateProfile', 'true');
                    }
                    navigate(from, { replace: true });
                } else {
                    setError(data.message || 'Đăng nhập Google thất bại');
                }
            } catch {
                setError('Lỗi kết nối server');
            } finally {
                setLoading(false);
            }
        },
        onError: () => setError('Đăng nhập Google thất bại'),
    });

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.username || !form.password) {
            setError('Vui lòng nhập đầy đủ thông tin');
            return;
        }
        setLoading(true);
        setError('');
        try {
            const { ok, data } = await api.login(form.username, form.password);
            if (ok && data.success) {
                login({
                    username: data.username,
                    role: data.role,
                    mustChangePassword: data.mustChangePassword || false,
                    displayName: data.displayName || data.username,
                });
                
                // Redirect về trang trước đó hoặc trang mua sách
                if (data.mustChangePassword) {
                    navigate('/change-password');
                } else {
                    navigate(from, { replace: true });
                }
            } else {
                setError(data.message || 'Sai tên đăng nhập hoặc mật khẩu');
            }
        } catch {
            setError('Không thể kết nối tới server');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-wrapper">
            <div className='login-card'>
                <h1 className='login-title'>Đăng nhập</h1>
                <p className='login-sub'>Quản lý tài khoản hệ thống</p>

                <form onSubmit={handleSubmit} className='login-form'>
                    <div className="field-group">
                        <label>Tên đăng nhập</label>
                        <input type="text" placeholder='Nhập tên tài khoản' value={form.username}
                            onChange={e => setForm({ ...form, username: e.target.value })} autoFocus />
                    </div>

                    <div className="field-group">
                        <label>Mật khẩu</label>
                        <div className='input-wrapper'>
                            <input type={showPass ? "text" : "password"}
                                placeholder='Nhập mật khẩu'
                                value={form.password}
                                onChange={e => setForm({ ...form, password: e.target.value })}
                            />
                            <button type="button" className='eye-btn' onClick={() => setShowPass(!showPass)}>
                                {showPass ? <EyeOff /> : <Eye />}
                            </button>
                        </div>
                    </div>

                    {error && <div className='error-box'>{error}</div>}
                    <button type="submit" className="btn-login" disabled={loading}>
                        {loading ? 'Đang xử lý...' : 'Đăng nhập'}
                    </button>

                    <div style={{ margin: '16px 0', textAlign: 'center', color: '#9ca3af', fontSize: 12 }}>
                        ── hoặc ──
                    </div>

                    <button type="button" onClick={handleGoogleLogin} disabled={loading}
                        style={{
                            width: '100%', padding: '11px', borderRadius: 10,
                            border: '1.5px solid #e5e7eb', background: '#fff',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            gap: 10, fontSize: 14, fontWeight: 600, cursor: 'pointer',
                            transition: 'all .2s', color: '#374151',
                        }}
                        onMouseEnter={e => e.currentTarget.style.borderColor = '#4285f4'}
                        onMouseLeave={e => e.currentTarget.style.borderColor = '#e5e7eb'}>
                        <svg width="18" height="18" viewBox="0 0 48 48">
                            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                            <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                            <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                            <path fill="none" d="M0 0h48v48H0z"/>
                        </svg>
                        Đăng nhập với Google
                    </button>
                    <div style={{ textAlign: 'center', marginTop: 16, fontSize: 13 }}>
                        Chưa có tài khoản?{' '}
                        <a href="/dem_login-0.0.1-SNAPSHOT/register"
                            style={{ color: 'var(--accent)', fontWeight: 600 }}>
                            Đăng ký ngay
                        </a>
                    </div>
                </form>
            </div>
        </div>
    )
}

export default LoginPages