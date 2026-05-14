import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';
import UserFormModal from './UserFormModal';
import UserDetailModal from './UserDetailModal';
import ConfirmDialog from './ConfirmDialog';
import ProfileEditModal from './ProfileEditModal';

export default function UserListPage() {
    const { user: me, logout } = useAuth();
    const navigate = useNavigate();
    const isAdmin = me?.role === 'ADMIN';
    const [users, setUsers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [isSidebarOpen, setIsSidebarOpen] = useState(true);

    // Search & filter
    const [search, setSearch] = useState('');
    const [filterStatus, setFilterStatus] = useState('ALL');
    const [filterRole, setFilterRole] = useState('ALL');

    // Modal states
    const [showForm, setShowForm] = useState(false);     // them/sua modal
    const [editUser, setEditUser] = useState(null);       // user dang sua
    const [detailUser, setDetailUser] = useState(null);   // xem chi tiet
    const [confirmAction, setConfirmAction] = useState(null); // confirm dialog
    const [showProfile, setShowProfile] = useState(false); // profile edit modal (USER/REPORT)

    const fetchUsers = async () => {
        setLoading(true);
        setError('');
        try {
            const data = await api.getUsers();
            setUsers(data);
        } catch {
            setError('Không thể tải danh sách người dùng');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchUsers();
    }, []);

    // Filter logic
    const filtered = users.filter(u => {
        // user và report chỉ xem được thông tin của chính mình
        if (!isAdmin && u.username !== me?.username) return false;

        const matchSearch = u.username.toLowerCase().includes(search.toLowerCase()) ||
            u.email.toLowerCase().includes(search.toLowerCase());
        const matchStatus = filterStatus === 'ALL' || u.status === filterStatus;
        const matchRole = filterRole === 'ALL' || u.role === filterRole;
        return matchSearch && matchStatus && matchRole;
    });

    // Stats
    const stats = {
        total: users.length,
        active: users.filter(u => u.status === 'ACTIVE').length,
        locked: users.filter(u => u.status === 'Temp_Lock').length,
    };

    // Handlers
    const handleAdd = () => {
        setEditUser(null);
        setShowForm(true);
    };

    const handleEdit = (u) => {
        setEditUser(u);
        setShowForm(true);
    };

    const handleDelete = (u) => {
        setConfirmAction({
            type: 'danger',
            title: 'Xóa tài khoản',
            message: `Bạn có chắc muốn xóa tài khoản "${u.username}"? Thao tác này không thể hoàn tác.`,
            onConfirm: async () => {
                await api.deleteUser(u.id);
                setConfirmAction(null);
                fetchUsers();
            },
        });
    };

    const handleUnlock = (u) => {
        setConfirmAction({
            type: 'warning',
            title: 'Mở khóa tài khoản',
            message: `Mở khóa tài khoản "${u.username}" và reset số lần đăng nhập sai?`,
            onConfirm: async () => {
                await api.unlockUser(u.id);
                setConfirmAction(null);
                fetchUsers();
            },
        });
    };

    const handleFormSaved = () => {
        setShowForm(false);
        setEditUser(null);
        fetchUsers();
    };

    const handleReset = () => {
        setConfirmAction({
            type: 'danger',
            title: 'Reset Database',
            message: 'Xóa TẤT CẢ dữ liệu và khôi phục lại 3 tài khoản mặc định (admin, user, report). Thao tác này KHÔNG THỂ hoàn tác!',
            onConfirm: async () => {
                await api.resetDatabase();
                setConfirmAction(null);
                fetchUsers();
            },
        });
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
                    <Link className="nav-item active" to="/users">
                        👥 Quản lý người dùng
                    </Link>
                    <Link className="nav-item" to="/customers">
                        🧑‍💼 Khách hàng
                    </Link>
                    <Link className="nav-item" to="/books">
                        📚 Quản lý sách
                    </Link>
                    <Link className="nav-item" to="/cart">
                        🛒 Mua sách
                    </Link>
                    <Link className="nav-item" to="/orders">
                        📦 Đơn hàng
                    </Link>
                </nav>

                {/* Footer chứa User Info và Logout */}
                <div className="sidebar-footer">
                    <div className="sidebar-user">
                        <div className="avatar">{me?.username?.[0]?.toUpperCase()}</div>
                        <div className="sidebar-user-info">
                            <div className="sidebar-username">{me?.username}</div>
                            <div className="sidebar-role">{me?.role}</div>
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
                            <h1>Quản lý tài khoản</h1>
                        </div>
                    </div>
                    <div className="page-actions">
                        {isAdmin ? (
                            <>
                                <button className="btn-primary btn-danger" onClick={handleReset}>
                                    ↺ Reset DB
                                </button>
                                <button className="btn-primary" onClick={handleAdd}>
                                    + Thêm tài khoản
                                </button>
                            </>
                        ) : (
                            <button className="btn-primary" onClick={() => setShowProfile(true)}>
                                ✏️ Chỉnh sửa thông tin
                            </button>
                        )}
                    </div>
                </div>

                {/* Toolbar: search + filter */}
                <div className="toolbar">
                    <div className="search-wrap">
                        <svg viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd" />
                        </svg>
                        <input
                            type="text"
                            placeholder="Tìm kiếm username, email..."
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                        />
                    </div>
                    <select className="filter-select" value={filterStatus} onChange={e => setFilterStatus(e.target.value)}>
                        <option value="ALL">Tất cả trạng thái</option>
                        <option value="ACTIVE">Hoạt động</option>
                        <option value="Temp_Lock">Tạm khóa</option>
                    </select>
                    <select className="filter-select" value={filterRole} onChange={e => setFilterRole(e.target.value)}>
                        <option value="ALL">Tất cả role</option>
                        <option value="ADMIN">Admin</option>
                        <option value="USER">User</option>
                        <option value="REPORT">Report</option>
                    </select>
                </div>

                {/* Table */}
                {loading ? (
                    <div className="loading-state">
                        <div className="loading-spinner" />
                        Đang tải dữ liệu...
                    </div>
                ) : error ? (
                    <div className="error-box" style={{ margin: '20px 32px' }}>{error}</div>
                ) : (
                    <div className="table-wrap">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Username</th>
                                    <th>Email</th>
                                    <th>Role</th>
                                    <th>Trạng thái</th>
                                    <th>Ngày đăng nhập cuối</th>
                                    <th>Ngày cập nhật cuối</th>
                                    <th>Ngày tạo tài khoản</th>
                                    {isAdmin && <th>Login sai</th>}
                                    <th>Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filtered.length === 0 ? (
                                    <tr>
                                        <td colSpan={isAdmin ? 10 : 9} className="empty-row">
                                            Không có dữ liệu
                                        </td>
                                    </tr>
                                ) : (
                                    filtered.map(u => (
                                        <tr key={u.id} onClick={() => setDetailUser(u)}>
                                            <td>{u.id}</td>
                                            <td>{u.username}</td>
                                            <td>{u.email}</td>
                                            <td>
                                                <span className={`role-badge role-${u.role?.toLowerCase()}`}>
                                                    {u.role}
                                                </span>
                                            </td>
                                            <td>
                                                <span className={`status-badge ${u.status === 'ACTIVE' ? 'badge-act' :
                                                    u.status === 'Temp_Lock' ? 'badge-lock' : 'badge-del'}`}>
                                                    {u.status === 'ACTIVE' ? 'Hoạt động' :
                                                        u.status === 'Temp_Lock' ? 'Tạm khóa' : 'Đã xóa'}
                                                </span>
                                            </td>
                                            <td className="date-cell">{u.lastLoginDate || '—'}</td>
                                            <td className="date-cell">{u.updatedDate || '—'}</td>
                                            <td className="date-cell">{u.createdDate || '—'}</td>
                                            {isAdmin && <td>{u.counterLogin}/5</td>}
                                            <td>
                                                <div className="action-cell" onClick={e => e.stopPropagation()}>
                                                    {/* Xem chi tiet */}
                                                    <button className="btn-icon" title="Xem chi tiết"
                                                        onClick={() => setDetailUser(u)}>
                                                        <svg viewBox="0 0 20 20" fill="currentColor">
                                                            <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
                                                            <path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" />
                                                        </svg>
                                                    </button>

                                                    {isAdmin && (
                                                        <>
                                                            {/* Sua */}
                                                            <button className="btn-icon" title="Chỉnh sửa"
                                                                onClick={() => handleEdit(u)}>
                                                                <svg viewBox="0 0 20 20" fill="currentColor">
                                                                    <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                                                                </svg>
                                                            </button>

                                                            {/* Mo khoa (chi hien khi bi khoa) */}
                                                            {u.status === 'Temp_Lock' && (
                                                                <button className="btn-icon btn-icon-success" title="Mở khóa"
                                                                    onClick={() => handleUnlock(u)}>
                                                                    <svg viewBox="0 0 20 20" fill="currentColor">
                                                                        <path d="M10 2a5 5 0 00-5 5v2a2 2 0 00-2 2v5a2 2 0 002 2h10a2 2 0 002-2v-5a2 2 0 00-2-2H7V7a3 3 0 015.905-.75 1 1 0 001.937-.5A5.002 5.002 0 0010 2z" />
                                                                    </svg>
                                                                </button>
                                                            )}

                                                            {/* Xoa - không cho phép xóa tài khoản admin */}
                                                            {u.role !== 'ADMIN' && (
                                                                <button className="btn-icon btn-icon-danger" title="Xóa"
                                                                    onClick={() => handleDelete(u)}>
                                                                    <svg viewBox="0 0 20 20" fill="currentColor">
                                                                        <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                                                                    </svg>
                                                                </button>
                                                            )}
                                                        </>
                                                    )}
                                                </div>
                                            </td>
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
                <UserFormModal
                    user={editUser}
                    onClose={() => { setShowForm(false); setEditUser(null); }}
                    onSaved={handleFormSaved}
                />
            )}

            {detailUser && (
                <UserDetailModal
                    user={detailUser}
                    isAdmin={isAdmin}
                    onClose={() => setDetailUser(null)}
                />
            )}

            {confirmAction && (
                <ConfirmDialog
                    type={confirmAction.type}
                    title={confirmAction.title}
                    message={confirmAction.message}
                    onConfirm={confirmAction.onConfirm}
                    onCancel={() => setConfirmAction(null)}
                />
            )}

            {showProfile && (
                <ProfileEditModal
                    user={filtered.find(u => u.username === me?.username) || me}
                    onClose={() => setShowProfile(false)}
                    onSaved={() => {
                        setShowProfile(false);
                        fetchUsers();
                    }}
                />
            )}
        </div>
    );
}
