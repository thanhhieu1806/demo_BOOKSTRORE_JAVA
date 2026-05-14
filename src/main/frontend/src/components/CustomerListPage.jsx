import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';
import CustomerFormModal from './CustomerFormModal';
import ConfirmDialog from './ConfirmDialog';

export default function CustomerListPage() {
    const { user: me, logout } = useAuth();
    const navigate = useNavigate();
    const isAdmin  = me?.role === 'ADMIN';
    const isReport = me?.role === 'REPORT';

    const [customers, setCustomers]       = useState([]);
    const [loading, setLoading]           = useState(true);
    const [search, setSearch]             = useState('');
    const [showForm, setShowForm]         = useState(false);
    const [editTarget, setEditTarget]     = useState(null);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [toast, setToast]               = useState('');
    const [isSidebarOpen, setIsSidebarOpen] = useState(true);

    const load = async () => {
        setLoading(true);
        try { setCustomers(await api.getCustomers()); }
        catch { showToast('Không thể tải dữ liệu'); }
        finally { setLoading(false); }
    };

    useEffect(() => { load(); }, []);

    const showToast = (msg) => {
        setToast(msg);
        setTimeout(() => setToast(''), 3000);
    };

    const filtered = customers.filter(c =>
        c.fullName?.toLowerCase().includes(search.toLowerCase()) ||
        c.phone?.includes(search) ||
        c.email?.toLowerCase().includes(search.toLowerCase())
    );

    // Stats
    const stats = {
        total: customers.length,
        active: customers.filter(c => c.status === 'ACTIVE').length,
        inactive: customers.filter(c => c.status === 'INACTIVE').length,
    };

    const handleDelete = async () => {
        const { data } = await api.deleteCustomer(deleteTarget.id);
        setDeleteTarget(null);
        showToast(data.message);
        load();
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
                    <div className="brand-title">Demo Login</div>
                    <button className="btn-toggle-sidebar" onClick={() => setIsSidebarOpen(false)} title="Đóng Sidebar">
                        <svg viewBox="0 0 24 24" width="22" height="22" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round">
                            <line x1="3" y1="12" x2="21" y2="12"></line>
                            <line x1="3" y1="6" x2="21" y2="6"></line>
                            <line x1="3" y1="18" x2="21" y2="18"></line>
                        </svg>
                    </button>
                </div>

                <nav className="sidebar-nav">
                    {isAdmin && (
                        <Link className="nav-item" to="/users">
                            👥 Quản lý người dùng
                        </Link>
                    )}
                    <Link className="nav-item active" to="/customers">
                        🧑‍💼 Khách hàng
                    </Link>
                    {isAdmin && (
                        <Link className="nav-item" to="/books">
                            📚 Quản lý sách
                        </Link>
                    )}
                    <Link className="nav-item" to="/cart">
                        🛒 Mua sách
                    </Link>
                    <Link className="nav-item" to="/orders">
                        📦 Đơn hàng
                    </Link>
                </nav>

                <div className="sidebar-footer">
                    <div className="sidebar-user">
                        <div className="avatar">{me?.username?.[0]?.toUpperCase()}</div>
                        <div className="sidebar-user-info">
                            <div className="sidebar-username">{me?.username}</div>
                        </div>
                    </div>
                    <button className="btn-logout" onClick={handleLogout} title="Đăng xuất">
                        <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round">
                            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path>
                            <polyline points="16 17 21 12 16 7"></polyline>
                            <line x1="21" y1="12" x2="9" y2="12"></line>
                        </svg>
                    </button>
                </div>
            </aside>

            {/* MAIN */}
            <main className="main-content">
                {/* Header */}
                <div className="page-header">
                    <div className="header-left">
                        {!isSidebarOpen && (
                            <button className="btn-toggle-sidebar" onClick={() => setIsSidebarOpen(true)} title="Mở Sidebar">
                                <svg viewBox="0 0 24 24" width="22" height="22" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round">
                                    <line x1="3" y1="12" x2="21" y2="12"></line>
                                    <line x1="3" y1="6" x2="21" y2="6"></line>
                                    <line x1="3" y1="18" x2="21" y2="18"></line>
                                </svg>
                            </button>
                        )}
                        <div>
                            <h1>Quản lý khách hàng</h1>
                        </div>
                    </div>
                    <div className="page-actions">
                        {/* Chỉ ADMIN + USER mới thấy nút Thêm mới */}
                        {!isReport && (
                            <button className="btn-primary" onClick={() => {
                                setEditTarget(null); setShowForm(true);
                            }}>
                                + Thêm khách hàng
                            </button>
                        )}
                    </div>
                </div>

                {/* Stats */}
                <div className="stats-row">
                    <div className="stat-card">
                        <div className="stat-label">Tổng</div>
                        <div className="stat-value cyan">{stats.total}</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-label">Hoạt động</div>
                        <div className="stat-value green">{stats.active}</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-label">Ngừng</div>
                        <div className="stat-value red">{stats.inactive}</div>
                    </div>
                </div>

                {/* Toolbar */}
                <div className="toolbar">
                    <div className="search-wrap">
                        <svg viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd" />
                        </svg>
                        <input
                            type="text"
                            placeholder="Tìm kiếm tên, SĐT, email..."
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                        />
                    </div>
                </div>

                {/* Table */}
                {loading ? (
                    <div className="loading-state">
                        <div className="loading-spinner" />
                        Đang tải dữ liệu...
                    </div>
                ) : (
                    <div className="table-wrap">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Họ tên</th>
                                    <th>Điện thoại</th>
                                    <th>Email</th>
                                    <th>Địa chỉ</th>
                                    <th>Ghi chú</th>
                                    <th>Trạng thái</th>
                                    <th>Ngày tạo</th>
                                    <th>Ngày cập nhật</th>
                                    {/* Cột thao tác: ẩn với REPORT */}
                                    {!isReport && <th>Thao tác</th>}
                                </tr>
                            </thead>
                            <tbody>
                                {filtered.length === 0 ? (
                                    <tr>
                                        <td colSpan={!isReport ? 10 : 9} className="empty-row">
                                            Không tìm thấy khách hàng nào
                                        </td>
                                    </tr>
                                ) : (
                                    filtered.map(c => (
                                        <tr key={c.id}>
                                            <td>{c.id}</td>
                                            <td><strong>{c.fullName}</strong></td>
                                            <td>{c.phone}</td>
                                            <td>{c.email || '—'}</td>
                                            <td>{c.address || '—'}</td>
                                            <td>{c.decription || '—'}</td>
                                            <td>
                                                <span className={`status-badge ${c.status === 'ACTIVE' ? 'badge-act' : 'badge-del'}`}>
                                                    {c.status === 'ACTIVE' ? 'Hoạt động' : 'Ngừng'}
                                                </span>
                                            </td>
                                            <td className="date-cell">{c.createDate || '—'}</td>
                                            <td className="date-cell">{c.updateDate || '—'}</td>

                                            {/* Nút thao tác — ẩn hoàn toàn với REPORT */}
                                            {!isReport && (
                                                <td>
                                                    <div className="action-cell">
                                                        {/* ADMIN + USER đều sửa được */}
                                                        <button className="btn-icon" title="Sửa"
                                                            onClick={() => { setEditTarget(c); setShowForm(true); }}>
                                                            <svg viewBox="0 0 20 20" fill="currentColor">
                                                                <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                                                            </svg>
                                                        </button>

                                                        {/* Chỉ ADMIN mới xóa được */}
                                                        {isAdmin && (
                                                            <button className="btn-icon btn-icon-danger" title="Xóa"
                                                                onClick={() => setDeleteTarget(c)}>
                                                                <svg viewBox="0 0 20 20" fill="currentColor">
                                                                    <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                                                                </svg>
                                                            </button>
                                                        )}
                                                    </div>
                                                </td>
                                            )}
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </main>

            {/* MODALS */}
            {showForm && (
                <CustomerFormModal
                    customer={editTarget}
                    onClose={() => setShowForm(false)}
                    onSaved={(msg) => { setShowForm(false); showToast(msg); load(); }}
                />
            )}

            {deleteTarget && (
                <ConfirmDialog
                    title="Xóa khách hàng"
                    message={`Bạn có chắc muốn xóa "${deleteTarget.fullName}"?`}
                    type="danger"
                    onConfirm={handleDelete}
                    onCancel={() => setDeleteTarget(null)}
                />
            )}

            {/* Toast thông báo */}
            {toast && (
                <div style={{
                    position: 'fixed', bottom: 24, right: 24,
                    background: '#1f2937', color: '#fff',
                    padding: '12px 20px', borderRadius: 10,
                    fontSize: 14, zIndex: 9999, boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
                    animation: 'slideUp 0.3s ease'
                }}>
                    {toast}
                </div>
            )}
        </div>
    );
}
