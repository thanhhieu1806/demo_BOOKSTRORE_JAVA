import { useState } from 'react';
import api from '../service/api';
import { useAuth } from '../context/AuthContext';

// step: 'form' | 'done'
export default function UserFormModal({ user, onClose, onSaved }) {
    const { user: me } = useAuth();
    const isAdmin = me?.role === 'ADMIN';
    const isEdit = !!user;

    const [form, setForm] = useState({
        email: user?.email || '',
        username: user?.username || '',
        role: user?.role || 'USER',
        status: user?.status || 'ACTIVE',
        resetCounter: false,
    });

    const [step, setStep] = useState('form'); // 'form' | 'done'
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [createdResult, setCreatedResult] = useState(null);

    // ── Submit form → tạo tài khoản trực tiếp ──────────────────────
    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.email || (!isEdit && !form.username)) {
            setError('Vui lòng điền đầy đủ thông tin bắt buộc');
            return;
        }
        setLoading(true);
        setError('');
        try {
            if (isEdit) {
                const { ok, data } = await api.editUser(user.id, {
                    email: form.email,
                    role: form.role,
                    status: form.status,
                    resetCounter: form.resetCounter,
                });
                if (ok) onSaved();
                else setError(data.message);
            } else {
                // Tạo tài khoản trực tiếp (không cần OTP)
                const { ok, data } = await api.addUser({
                    email: form.email,
                    username: form.username,
                    role: form.role,
                });
                if (ok) {
                    setCreatedResult(data);
                    setStep('done');
                } else {
                    setError(data.message || 'Không thể tạo tài khoản');
                }
            }
        } catch {
            setError('Lỗi kết nối server');
        } finally {
            setLoading(false);
        }
    };

    const handleDone = () => {
        onSaved(createdResult?.message || 'Tạo tài khoản thành công');
    };

    // ── TITLE theo step ────────────────────────────────────────────
    const titles = {
        form: isEdit ? 'Chỉnh sửa tài khoản' : 'Thêm tài khoản mới',
        done: 'Tài khoản đã được tạo',
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{titles[step]}</h2>
                    <button className="modal-close" onClick={onClose}>x</button>
                </div>

                {/* ── STEP: form ── */}
                {step === 'form' && (
                    <form onSubmit={handleSubmit}>
                        <div className="modal-body">
                            {!isEdit && (
                                <div className="field-group">
                                    <label>Tên đăng nhập <span className="req">*</span></label>
                                    <input
                                        type="text"
                                        value={form.username}
                                        onChange={e => setForm({ ...form, username: e.target.value })}
                                        placeholder="Nhập username"
                                    />
                                </div>
                            )}
                            {isEdit && (
                                <div className="field-group">
                                    <label>Tên đăng nhập</label>
                                    <input type="text" value={form.username} disabled className="disabled" />
                                </div>
                            )}

                            <div className="field-group">
                                <label>Email <span className="req">*</span></label>
                                <input
                                    type="email"
                                    value={form.email}
                                    onChange={e => setForm({ ...form, email: e.target.value })}
                                    placeholder="email@example.com"
                                />
                            </div>

                            {isAdmin && (
                                <div className="form-row">
                                    <div className="field-group">
                                        <label>Role</label>
                                        <select value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}>
                                            <option value="USER">User</option>
                                            <option value="ADMIN">Admin</option>
                                            <option value="REPORT">Report</option>
                                        </select>
                                    </div>
                                    {isEdit && (
                                        <div className="field-group">
                                            <label>Trạng thái</label>
                                            <select value={form.status} onChange={e => setForm({ ...form, status: e.target.value })}>
                                                <option value="ACTIVE">Hoạt động</option>
                                                <option value="Temp_Lock">Tạm khóa</option>
                                            </select>
                                        </div>
                                    )}
                                </div>
                            )}

                            {isEdit && isAdmin && (
                                <div className="field-check">
                                    <input
                                        type="checkbox"
                                        id="resetCounter"
                                        checked={form.resetCounter}
                                        onChange={e => setForm({ ...form, resetCounter: e.target.checked })}
                                    />
                                    <label htmlFor="resetCounter">Reset số lần đăng nhập sai về 0</label>
                                </div>
                            )}

                            {!isEdit && (
                                <div className="info-box">
                                    <svg viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                                    </svg>
                                    <div>Mật khẩu sẽ được tạo tự động và gửi đến email của người dùng.</div>
                                </div>
                            )}

                            {error && <div className="error-box"><span>x</span> {error}</div>}
                        </div>
                        <div className="modal-footer">
                            <button type="button" className="btn-cancel" onClick={onClose}>Hủy</button>
                            <button type="submit" className="btn-primary" disabled={loading}>
                                {loading ? <span className="spinner" /> : (isEdit ? 'Lưu thay đổi' : 'Tạo tài khoản')}
                            </button>
                        </div>
                    </form>
                )}

                {/* ── STEP: done ── */}
                {step === 'done' && createdResult && (
                    <>
                        <div className="modal-body">
                            <div className="info-box">
                                <svg viewBox="0 0 20 20" fill="currentColor">
                                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
                                </svg>
                                <div>{createdResult.message}</div>
                            </div>

                            <div className="pwd-reveal">
                                <div className="field-group">
                                    <label>Tên đăng nhập</label>
                                    <input type="text" value={createdResult.username || ''} disabled className="disabled" />
                                </div>

                                {createdResult.emailSent ? (
                                    <>
                                        <div className="field-group">
                                            <label>📧 Mật khẩu đã gửi đến</label>
                                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '6px' }}>
                                                <span style={{
                                                    background: '#ede9fe', color: '#6366f1',
                                                    borderRadius: '20px', padding: '4px 12px',
                                                    fontSize: '13px', fontWeight: 600
                                                }}>{createdResult.email}</span>
                                            </div>
                                        </div>
                                        <p className="pwd-hint">
                                            ✅ Mật khẩu đã được gửi qua email. Người dùng chỉ cần mở email và đăng nhập.
                                        </p>
                                    </>
                                ) : (
                                    <>
                                        <div className="field-group">
                                            <label>Email</label>
                                            <input type="text" value={createdResult.email || ''} disabled className="disabled" />
                                        </div>
                                        <div className="pwd-box">{createdResult.tempPassword}</div>
                                        <p className="pwd-hint">
                                            ⚠️ SMTP chưa gửi được email. Hãy gửi <strong>mật khẩu tạm</strong> này cho người dùng để đăng nhập.
                                        </p>
                                    </>
                                )}
                            </div>
                        </div>
                        <div className="modal-footer">
                            <button type="button" className="btn-primary" onClick={handleDone}>Đóng</button>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}