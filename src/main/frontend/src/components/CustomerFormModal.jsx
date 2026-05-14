import { useState } from 'react';
import api from '../service/api';
import { useAuth } from '../context/AuthContext';

export default function CustomerFormModal({ customer, onClose, onSaved }) {
    const { user } = useAuth();
    const isAdmin = user?.role === 'ADMIN';
    const isEdit = !!customer;

    const [form, setForm] = useState({
        fullName: customer?.fullName || '',
        phone:    customer?.phone    || '',
        email:    customer?.email    || '',
        address:  customer?.address  || '',
        decription: customer?.decription || '',
        status:   customer?.status   || 'ACTIVE',
    });
    const [loading, setLoading] = useState(false);
    const [error, setError]     = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.fullName.trim() || !form.phone.trim()) {
            setError('Vui lòng nhập họ tên và số điện thoại');
            return;
        }
        setLoading(true);
        setError('');
        try {
            const { ok, data } = isEdit
                ? await api.updateCustomer(customer.id, form)
                : await api.addCustomer(form);
            if (ok && data.success === 'true') onSaved(data.message);
            else setError(data.message || 'Thao tác thất bại');
        } catch {
            setError('Lỗi kết nối server');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isEdit ? 'Chỉnh sửa khách hàng' : 'Thêm khách hàng mới'}</h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">

                        <div className="field-group">
                            <label>Họ và tên <span className="req">*</span></label>
                            <input type="text" value={form.fullName}
                                onChange={e => setForm({ ...form, fullName: e.target.value })}
                                placeholder="Nhập họ và tên" />
                        </div>

                        <div className="field-group">
                            <label>Số điện thoại <span className="req">*</span></label>
                            <input type="text" value={form.phone}
                                onChange={e => setForm({ ...form, phone: e.target.value })}
                                placeholder="0xxxxxxxxx" />
                        </div>

                        <div className="field-group">
                            <label>Email</label>
                            <input type="email" value={form.email}
                                onChange={e => setForm({ ...form, email: e.target.value })}
                                placeholder="email@example.com" />
                        </div>

                        <div className="field-group">
                            <label>Địa chỉ</label>
                            <input type="text" value={form.address}
                                onChange={e => setForm({ ...form, address: e.target.value })}
                                placeholder="Nhập địa chỉ" />
                        </div>

                        <div className="field-group">
                            <label>Ghi chú</label>
                            <input type="text" value={form.decription}
                                onChange={e => setForm({ ...form, decription: e.target.value })}
                                placeholder="Ghi chú thêm" />
                        </div>

                        {/* Chỉ ADMIN mới thấy ô đổi trạng thái khi sửa */}
                        {isEdit && isAdmin && (
                            <div className="field-group">
                                <label>Trạng thái</label>
                                <select value={form.status}
                                    onChange={e => setForm({ ...form, status: e.target.value })}>
                                    <option value="ACTIVE">Hoạt động</option>
                                    <option value="INACTIVE">Ngừng hoạt động</option>
                                </select>
                            </div>
                        )}

                        {error && <div className="error-box"><span>✕</span> {error}</div>}
                    </div>

                    <div className="modal-footer">
                        <button type="button" className="btn-cancel" onClick={onClose}>Hủy</button>
                        <button type="submit" className="btn-primary" disabled={loading}>
                            {loading ? <span className="spinner" /> : (isEdit ? 'Lưu thay đổi' : 'Thêm mới')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
