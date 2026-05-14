import { useState } from 'react'
import { useAuth } from '../context/AuthContext';
import api from '../service/api';
import { Eye, EyeOff } from 'lucide-react';


const LoginPages = () => {
    const { login } = useAuth();
    const [form, setForm] = useState({ username: '', password: '' });
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [showPass, setShowPass] = useState(false);

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
                });
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