import { useState } from 'react';
import api from '../service/api';

export default function ProfileEditModal({ user, onClose, onSaved }) {
    const [form, setForm] = useState({
        email: user?.email || '',
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
    });

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [showCurrentPwd, setShowCurrentPwd] = useState(false);
    const [showNewPwd, setShowNewPwd] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setSuccess('');

        // Validate
        if (!form.email) {
            setError('Email không được để trống');
            return;
        }

        // Nếu đổi mật khẩu, kiểm tra
        if (form.newPassword) {
            if (!form.currentPassword) {
                setError('Vui lòng nhập mật khẩu hiện tại');
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
        }

        setLoading(true);
        try {
            const payload = {
                username: user.username,
                email: form.email,
            };
            // Chỉ gửi password nếu muốn đổi
            if (form.newPassword) {
                payload.currentPassword = form.currentPassword;
                payload.newPassword = form.newPassword;
            }

            const { ok, data } = await api.updateProfile(payload);
            if (ok) {
                setSuccess(data.message || 'Cập nhật thành công');
                // Reset password fields
                setForm(f => ({ ...f, currentPassword: '', newPassword: '', confirmPassword: '' }));
                setTimeout(() => {
                    onSaved?.();
                }, 1200);
            } else {
                setError(data.message || 'Cập nhật thất bại');
            }
        } catch {
            setError('Lỗi kết nối server');
        } finally {
            setLoading(false);
        }
    };

    const EyeIcon = ({ show }) => (
        <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
            {show ? (
                <>
                    <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
                    <path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" />
                </>
            ) : (
                <path fillRule="evenodd" d="M3.707 2.293a1 1 0 00-1.414 1.414l14 14a1 1 0 001.414-1.414l-1.473-1.473A10.014 10.014 0 0019.542 10C18.268 5.943 14.478 3 10 3a9.958 9.958 0 00-4.512 1.074l-1.78-1.781zm4.261 4.26l1.514 1.515a2.003 2.003 0 012.45 2.45l1.514 1.514a4 4 0 00-5.478-5.478z" clipRule="evenodd" />
            )}
        </svg>
    );

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>Chỉnh sửa thông tin cá nhân</h2>
                    <button className="modal-close" onClick={onClose}>x</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        {/* Username (chỉ đọc) */}
                        <div className="field-group">
                            <label>Tên đăng nhập</label>
                            <input type="text" value={user?.username || ''} disabled className="disabled" />
                        </div>

                        {/* Email */}
                        <div className="field-group">
                            <label>Email <span className="req">*</span></label>
                            <input
                                type="email"
                                value={form.email}
                                onChange={e => setForm({ ...form, email: e.target.value })}
                                placeholder="email@example.com"
                            />
                        </div>

                        {/* Divider */}
                        <div className="profile-divider">
                            <span>Đổi mật khẩu</span>
                        </div>

                        {/* Mật khẩu hiện tại */}
                        <div className="field-group">
                            <label>Mật khẩu hiện tại</label>
                            <div className="input-wrapper">
                                <input
                                    type={showCurrentPwd ? 'text' : 'password'}
                                    value={form.currentPassword}
                                    onChange={e => setForm({ ...form, currentPassword: e.target.value })}
                                    placeholder="Nhập mật khẩu hiện tại"
                                />
                                <button type="button" className="eye-btn" onClick={() => setShowCurrentPwd(!showCurrentPwd)}>
                                    <EyeIcon show={showCurrentPwd} />
                                </button>
                            </div>
                        </div>

                        {/* Mật khẩu mới */}
                        <div className="field-group">
                            <label>Mật khẩu mới</label>
                            <div className="input-wrapper">
                                <input
                                    type={showNewPwd ? 'text' : 'password'}
                                    value={form.newPassword}
                                    onChange={e => setForm({ ...form, newPassword: e.target.value })}
                                    placeholder="Nhập mật khẩu mới (tối thiểu 6 ký tự)"
                                />
                                <button type="button" className="eye-btn" onClick={() => setShowNewPwd(!showNewPwd)}>
                                    <EyeIcon show={showNewPwd} />
                                </button>
                            </div>
                        </div>

                        {/* Xác nhận mật khẩu */}
                        <div className="field-group">
                            <label>Xác nhận mật khẩu mới</label>
                            <input
                                type="password"
                                value={form.confirmPassword}
                                onChange={e => setForm({ ...form, confirmPassword: e.target.value })}
                                placeholder="Nhập lại mật khẩu mới"
                            />
                        </div>

                        <div className="info-box">
                            <svg viewBox="0 0 20 20" fill="currentColor">
                                <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                            </svg>
                            <div>Để trống phần mật khẩu nếu bạn chỉ muốn thay đổi email.</div>
                        </div>

                        {error && <div className="error-box"><span>x</span> {error}</div>}
                        {success && (
                            <div className="success-box">
                                <span>✓</span> {success}
                            </div>
                        )}
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn-cancel" onClick={onClose}>Hủy</button>
                        <button type="submit" className="btn-primary" disabled={loading}>
                            {loading ? <span className="spinner" /> : 'Lưu thay đổi'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
