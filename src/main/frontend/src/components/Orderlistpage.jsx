import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';
import ConfirmDialog from './ConfirmDialog';

//  Modal Chi tiết đơn hàng ─
function OrderDetailModal({ order, isAdmin, onClose, onStatusChange }) {
    const fmtPrice = (p) => new Intl.NumberFormat('vi-VN').format(p) + ' đ';

    const statusConfig = {
        PENDING: { label: 'Chờ xác nhận', color: 'var(--yellow)', bg: 'var(--yellow-dim)' },
        CONFIRMED: { label: 'Đã xác nhận', color: 'var(--green)', bg: 'var(--green-dim)' },
        CANCELLED: { label: 'Đã hủy', color: 'var(--red)', bg: 'var(--red-dim)' },
    };
    const s = statusConfig[order.status] || statusConfig.PENDING;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box" style={{ maxWidth: 560 }} onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>📦 Chi tiết đơn hàng #{order.id}</h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>
                <div className="modal-body">
                    {/* Thông tin */}
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                        {[
                            ['Người nhận', order.customerName],
                            ['Điện thoại', order.phone],
                            ['Địa chỉ', order.address],
                            ['Người đặt', order.username],
                            ['Ngày đặt', order.createdDate
                                ? new Date(order.createdDate).toLocaleString('vi-VN') : '—'],
                        ].map(([label, val]) => (
                            <div key={label} style={{
                                background: 'var(--bg-glass)',
                                border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)',
                                padding: '10px 12px'
                            }}>
                                <div style={{
                                    fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
                                    letterSpacing: '0.7px', color: 'var(--text-dim)', marginBottom: 4
                                }}>
                                    {label}
                                </div>
                                <div style={{ fontSize: 13, fontWeight: 600 }}>{val || '—'}</div>
                            </div>
                        ))}
                        <div style={{
                            background: 'var(--bg-glass)', border: '1px solid var(--border)',
                            borderRadius: 'var(--radius-sm)', padding: '10px 12px'
                        }}>
                            <div style={{
                                fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
                                letterSpacing: '0.7px', color: 'var(--text-dim)', marginBottom: 4
                            }}>
                                Trạng thái
                            </div>
                            <span style={{
                                fontSize: 12, fontWeight: 700, padding: '3px 10px',
                                borderRadius: 20, background: s.bg, color: s.color
                            }}>
                                {s.label}
                            </span>
                        </div>
                    </div>

                    {/* Items */}
                    <div>
                        <div style={{
                            fontSize: 12, fontWeight: 700, textTransform: 'uppercase',
                            letterSpacing: '0.7px', color: 'var(--text-dim)', marginBottom: 10
                        }}>
                            Danh sách sách
                        </div>
                        {(order.items || []).map((item, i) => (
                            <div key={i} style={{
                                display: 'flex', justifyContent: 'space-between',
                                alignItems: 'center', padding: '8px 0',
                                borderBottom: '1px solid var(--border)', fontSize: 13
                            }}>
                                <div>
                                    <span style={{ fontWeight: 600 }}>{item.bookTitle}</span>
                                    <span style={{ color: 'var(--text-dim)', marginLeft: 8 }}>
                                        x{item.quantity} × {fmtPrice(item.price)}
                                    </span>
                                </div>
                                <span style={{ fontWeight: 700, color: 'var(--accent)' }}>
                                    {fmtPrice(item.subtotal)}
                                </span>
                            </div>
                        ))}
                        <div style={{
                            display: 'flex', justifyContent: 'space-between',
                            marginTop: 12, fontWeight: 700, fontSize: 16,
                            paddingTop: 12, borderTop: '2px solid var(--border)'
                        }}>
                            <span>Tổng cộng</span>
                            <span style={{ color: 'var(--accent)' }}>{fmtPrice(order.totalAmount)}</span>
                        </div>
                    </div>
                </div>

                <div className="modal-footer">
                    <button className="btn-cancel" onClick={onClose}>Đóng</button>
                    {/* ADMIN: có thể xác nhận hoặc hủy */}
                    {isAdmin && order.status === 'PENDING' && (
                        <>
                            <button className="btn-primary btn-danger"
                                onClick={() => onStatusChange(order.id, 'CANCELLED')}>
                                ✕ Hủy đơn
                            </button>
                            <button className="btn-primary"
                                onClick={() => onStatusChange(order.id, 'CONFIRMED')}>
                                ✓ Xác nhận
                            </button>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
}

function OrderEditModal({ order, username, onClose, onSaved }) {
    const [form, setForm] = useState({
        customerName: order.customerName || '',
        phone: order.phone || '',
        address: order.address || '',
    });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        if (!form.customerName.trim()) {
            setError('Vui lòng nhập tên người nhận');
            return;
        }
        if (!form.phone.trim()) {
            setError('Vui lòng nhập số điện thoại');
            return;
        }
        if (!form.address.trim()) {
            setError('Vui lòng nhập địa chỉ giao hàng');
            return;
        }

        setLoading(true);
        try {
            const { ok, data } = await api.updateOrder(order.id, {
                username,
                customerName: form.customerName,
                phone: form.phone,
                address: form.address,
            });
            if (ok && data.success === 'true') {
                onSaved(data.message);
            } else {
                setError(data.message || 'Cập nhật đơn hàng thất bại');
            }
        } catch {
            setError('Không thể kết nối tới server');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box" style={{ maxWidth: 500 }} onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>Chỉnh sửa đơn hàng #{order.id}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>
                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="field-group">
                            <label>Tên người nhận <span className="req">*</span></label>
                            <input
                                type="text"
                                value={form.customerName}
                                onChange={e => setForm({ ...form, customerName: e.target.value })}
                                autoFocus
                            />
                        </div>
                        <div className="field-group">
                            <label>Số điện thoại <span className="req">*</span></label>
                            <input
                                type="text"
                                value={form.phone}
                                onChange={e => setForm({ ...form, phone: e.target.value })}
                            />
                        </div>
                        <div className="field-group">
                            <label>Địa chỉ giao hàng <span className="req">*</span></label>
                            <input
                                type="text"
                                value={form.address}
                                onChange={e => setForm({ ...form, address: e.target.value })}
                            />
                        </div>
                        {error && <div className="error-box">{error}</div>}
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

//  Main Page 
export default function OrderListPage() {
    const { user: me, logout } = useAuth();
    const navigate = useNavigate();
    const isAdmin = me?.role === 'ADMIN';
    const isUser = me?.role === 'USER';

    const [orders, setOrders] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [filterStatus, setFilterStatus] = useState('ALL');
    const [detailOrder, setDetailOrder] = useState(null);
    const [editOrder, setEditOrder] = useState(null);
    const [deleteOrder, setDeleteOrder] = useState(null);
    const [toast, setToast] = useState('');
    const [isSidebarOpen, setIsSidebarOpen] = useState(true);

    const load = async () => {
        setLoading(true);
        try {
            const data = isAdmin
                ? await api.getOrders()
                : await api.getMyOrders(me?.username);
            setOrders(data);
        } catch { showToastMsg('Không thể tải đơn hàng'); }
        finally { setLoading(false); }
    };

    useEffect(() => { load(); }, []);

    const showToastMsg = (msg) => {
        setToast(msg); setTimeout(() => setToast(''), 3000);
    };

    const handleStatusChange = async (id, status) => {
        const { data } = await api.updateOrderStatus(id, status);
        showToastMsg(data.message);
        setDetailOrder(null);
        load();
    };

    const handleDeleteOrder = async () => {
        const { data } = await api.deleteOrder(deleteOrder.id, me?.username);
        showToastMsg(data.message);
        setDeleteOrder(null);
        load();
    };

    const fmtPrice = (p) => new Intl.NumberFormat('vi-VN').format(p) + ' đ';

    const statusConfig = {
        PENDING: { label: 'Chờ xác nhận', badgeClass: 'badge-lock' },
        CONFIRMED: { label: 'Đã xác nhận', badgeClass: 'badge-act' },
        CANCELLED: { label: 'Đã hủy', badgeClass: 'badge-del' },
    };

    const filtered = orders.filter(o => {
        const matchSearch = o.customerName?.toLowerCase().includes(search.toLowerCase()) ||
            String(o.id).includes(search) ||
            o.username?.toLowerCase().includes(search.toLowerCase());
        const matchStatus = filterStatus === 'ALL' || o.status === filterStatus;
        return matchSearch && matchStatus;
    });

    const stats = {
        total: orders.length,
        pending: orders.filter(o => o.status === 'PENDING').length,
        confirmed: orders.filter(o => o.status === 'CONFIRMED').length,
        cancelled: orders.filter(o => o.status === 'CANCELLED').length,
    };

    const handleLogout = () => {
        logout();
        navigate('/login', { replace: true });
    };

    return (
        <div className="dashboard">
            {/* SIDEBAR */}
            <aside className={`sidebar ${isSidebarOpen ? '' : 'closed'}`}>
                <div className="sidebar-brand">
                    <div className="brand-title">BOOKSTORE</div>
                    <button className="btn-toggle-sidebar" onClick={() => setIsSidebarOpen(false)}>
                        <svg viewBox="0 0 24 24" width="22" height="22" stroke="currentColor" strokeWidth="2" fill="none">
                            <line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="18" x2="21" y2="18" />
                        </svg>
                    </button>
                </div>
                <nav className="sidebar-nav">
                    {isAdmin && <Link className="nav-item" to="/users">👥 Quản lý người dùng</Link>}
                    <Link className="nav-item" to="/customers">🧑‍💼 Khách hàng</Link>
                    {isAdmin && <Link className="nav-item" to="/books">📚 Quản lý sách</Link>}
                    <Link className="nav-item" to="/cart">🛒 Mua sách</Link>
                    <Link className="nav-item active" to="/orders">📦 Đơn hàng</Link>
                </nav>
                <div className="sidebar-footer">
                    <div className="sidebar-user">
                        <div className="avatar">{me?.username?.[0]?.toUpperCase()}</div>
                        <div className="sidebar-user-info">
                            <div className="sidebar-username">{me?.username}</div>
                            <div className="sidebar-role">{me?.role}</div>
                        </div>
                    </div>
                    <button className="btn-logout" onClick={handleLogout} title="Đăng xuất">
                        <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none">
                            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" />
                        </svg>
                    </button>
                </div>
            </aside>

            {/* MAIN */}
            <main className="main-content">
                <div className="page-header">
                    <div className="header-left">
                        {!isSidebarOpen && (
                            <button className="btn-toggle-sidebar" onClick={() => setIsSidebarOpen(true)}>
                                <svg viewBox="0 0 24 24" width="22" height="22" stroke="currentColor" strokeWidth="2" fill="none">
                                    <line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="18" x2="21" y2="18" />
                                </svg>
                            </button>
                        )}
                        <h1>📦 {isAdmin ? 'Quản lý đơn hàng' : 'Đơn hàng của tôi'}</h1>
                    </div>
                </div>

                {/* Stats */}
                <div className="stats-row">
                    <div className="stat-card"><div className="stat-label">Tổng đơn</div><div className="stat-value cyan">{stats.total}</div></div>
                    <div className="stat-card"><div className="stat-label">Chờ xác nhận</div><div className="stat-value yellow">{stats.pending}</div></div>
                    <div className="stat-card"><div className="stat-label">Đã xác nhận</div><div className="stat-value green">{stats.confirmed}</div></div>
                    <div className="stat-card"><div className="stat-label">Đã hủy</div><div className="stat-value red">{stats.cancelled}</div></div>
                </div>

                {/* Toolbar */}
                <div className="toolbar">
                    <div className="search-wrap">
                        <svg viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd" />
                        </svg>
                        <input type="text" placeholder="Tìm mã đơn, tên khách, username..."
                            value={search} onChange={e => setSearch(e.target.value)} />
                    </div>
                    <select className="filter-select" value={filterStatus}
                        onChange={e => setFilterStatus(e.target.value)}>
                        <option value="ALL">Tất cả trạng thái</option>
                        <option value="PENDING">Chờ xác nhận</option>
                        <option value="CONFIRMED">Đã xác nhận</option>
                        <option value="CANCELLED">Đã hủy</option>
                    </select>
                </div>

                {/* Table */}
                {loading ? (
                    <div className="loading-state"><div className="loading-spinner" />Đang tải đơn hàng...</div>
                ) : (
                    <div className="table-wrap">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>Mã đơn</th>
                                    {isAdmin && <th>Người đặt</th>}
                                    <th>Người nhận</th>
                                    <th>SĐT</th>
                                    <th>Địa chỉ</th>
                                    <th>Số sách</th>
                                    <th>Tổng tiền</th>
                                    <th>Trạng thái</th>
                                    <th>Ngày đặt</th>
                                    <th>Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filtered.length === 0 ? (
                                    <tr><td colSpan={isAdmin ? 10 : 9} className="empty-row">Không có đơn hàng nào</td></tr>
                                ) : filtered.map(o => {
                                    const sc = statusConfig[o.status] || statusConfig.PENDING;
                                    return (
                                        <tr key={o.id}>
                                            <td><strong>#{o.id}</strong></td>
                                            {isAdmin && <td>{o.username}</td>}
                                            <td>{o.customerName}</td>
                                            <td>{o.phone}</td>
                                            <td style={{ maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                                {o.address}
                                            </td>
                                            <td>{(o.items || []).length} sách</td>
                                            <td style={{ color: 'var(--accent)', fontWeight: 700 }}>
                                                {fmtPrice(o.totalAmount)}
                                            </td>
                                            <td>
                                                <span className={`status-badge ${sc.badgeClass}`}>
                                                    {sc.label}
                                                </span>
                                            </td>
                                            <td className="date-cell">
                                                {o.createdDate ? new Date(o.createdDate).toLocaleDateString('vi-VN') : '—'}
                                            </td>
                                            <td>
                                                <div className="action-cell">
                                                    <button className="btn-icon" title="Xem chi tiết"
                                                        onClick={() => setDetailOrder(o)}>
                                                        <svg viewBox="0 0 20 20" fill="currentColor">
                                                            <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
                                                            <path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" />
                                                        </svg>
                                                    </button>
                                                    {isAdmin && o.status === 'PENDING' && (
                                                        <>
                                                            <button className="btn-icon btn-icon-success" title="Xác nhận"
                                                                onClick={() => handleStatusChange(o.id, 'CONFIRMED')}>
                                                                <svg viewBox="0 0 20 20" fill="currentColor">
                                                                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                                                                </svg>
                                                            </button>
                                                            <button className="btn-icon btn-icon-danger" title="Hủy đơn"
                                                                onClick={() => handleStatusChange(o.id, 'CANCELLED')}>
                                                                <svg viewBox="0 0 20 20" fill="currentColor">
                                                                    <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                                                                </svg>
                                                            </button>
                                                        </>
                                                    )}
                                                    {isUser && o.status === 'PENDING' && (
                                                        <>
                                                            <button className="btn-icon" title="Chỉnh sửa"
                                                                onClick={() => setEditOrder(o)}>
                                                                <svg viewBox="0 0 20 20" fill="currentColor">
                                                                    <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                                                                </svg>
                                                            </button>
                                                            <button className="btn-icon btn-icon-danger" title="Xóa đơn hàng"
                                                                onClick={() => setDeleteOrder(o)}>
                                                                <svg viewBox="0 0 20 20" fill="currentColor">
                                                                    <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                                                                </svg>
                                                            </button>
                                                        </>
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </main>

            {/* MODALS */}
            {detailOrder && (
                <OrderDetailModal
                    order={detailOrder}
                    isAdmin={isAdmin}
                    onClose={() => setDetailOrder(null)}
                    onStatusChange={handleStatusChange}
                />
            )}

            {editOrder && (
                <OrderEditModal
                    order={editOrder}
                    username={me?.username}
                    onClose={() => setEditOrder(null)}
                    onSaved={(msg) => {
                        setEditOrder(null);
                        showToastMsg(msg);
                        load();
                    }}
                />
            )}

            {deleteOrder && (
                <ConfirmDialog
                    type="danger"
                    title="Xóa đơn hàng"
                    message={`Bạn có chắc muốn xóa đơn hàng #${deleteOrder.id}? Tồn kho sẽ được hoàn lại.`}
                    onConfirm={handleDeleteOrder}
                    onCancel={() => setDeleteOrder(null)}
                />
            )}

            {toast && (
                <div style={{
                    position: 'fixed', bottom: 24, right: 24, background: '#1f2937', color: '#fff',
                    padding: '12px 20px', borderRadius: 10, fontSize: 14, zIndex: 9999,
                    boxShadow: '0 4px 12px rgba(0,0,0,0.3)'
                }}>
                    {toast}
                </div>
            )}
        </div>
    );
}
