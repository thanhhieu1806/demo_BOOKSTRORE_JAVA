import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';

//  Modal Thêm/Sửa Sách 
function BookFormModal({ book, onClose, onSaved }) {
    const isEdit = !!book;
    const getPdfUrl = (path) => path ? "/dem_login-0.0.1-SNAPSHOT/uploads/pdfs/" + path.split(/[\/\\]/).pop() : "";
    const [form, setForm] = useState({
        title: book?.title || '',
        author: book?.author || '',
        category: book?.category || '',
        description: book?.description || '',
        imageUrl: book?.imageUrl || '',
        pdfPath: book?.pdfPath || '',
        price: book?.price || '',
        quantity: book?.quantity ?? 0,
        status: book?.status || 'ACTIVE',
    });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const [imageFile, setImageFile] = useState(null);
    const [pdfFile, setPdfFile] = useState(null);
    const [uploading, setUploading] = useState(false);

    const handleUpload = async () => {
        setUploading(true);
        let imageUrl = form.imageUrl;
        let pdfPath = form.pdfPath;

        if (imageFile) {
            const { ok, data } = await api.uploadImage(imageFile);
            if (ok && (data.success === 'true' || data.success === true)) imageUrl = data.url;
        }
        if (pdfFile) {
            const { ok, data } = await api.uploadPdf(pdfFile);
            if (ok && (data.success === 'true' || data.success === true)) pdfPath = data.filePath;
        }
        setUploading(false);
        return { imageUrl, pdfPath };
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.title.trim()) { setError('Tên sách không được trống!'); return; }
        if (!form.price) { setError('Giá không được trống!'); return; }
        setLoading(true); setError('');
        try {
            const uploaded = await handleUpload();
            const payload = { ...form, price: parseFloat(form.price), quantity: parseInt(form.quantity), imageUrl: uploaded.imageUrl, pdfPath: uploaded.pdfPath };
            const { ok, data } = isEdit
                ? await api.updateBook(book.id, payload)
                : await api.addBook(payload);
            if (ok && (data.success === 'true' || data.success === true)) onSaved(data.message);
            else setError(data.message || 'Thao tác thất bại');
        } catch { setError('Lỗi kết nối server'); }
        finally { setLoading(false); }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box" style={{ maxWidth: 540 }} onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isEdit ? '✏️ Chỉnh sửa sách' : '📚 Thêm sách mới'}</h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>
                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="form-row">
                            <div className="field-group">
                                <label>Tên sách <span className="req">*</span></label>
                                <input type="text" value={form.title}
                                    onChange={e => setForm({ ...form, title: e.target.value })}
                                    placeholder="Nhập tên sách" />
                            </div>
                            <div className="field-group">
                                <label>Tác giả</label>
                                <input type="text" value={form.author}
                                    onChange={e => setForm({ ...form, author: e.target.value })}
                                    placeholder="Nhập tên tác giả" />
                            </div>
                        </div>
                        <div className="form-row">
                            <div className="field-group">
                                <label>Thể loại</label>
                                <input type="text" value={form.category}
                                    onChange={e => setForm({ ...form, category: e.target.value })}
                                    placeholder="VD: Văn học, Kỹ năng..." />
                            </div>
                            <div className="field-group">
                                <label>Giá (VNĐ) <span className="req">*</span></label>
                                <input type="number" min="0" value={form.price}
                                    onChange={e => setForm({ ...form, price: e.target.value })}
                                    placeholder="0" />
                            </div>
                        </div>
                        <div className="form-row">
                            <div className="field-group">
                                <label>Số lượng tồn kho</label>
                                <input type="number" min="0" value={form.quantity}
                                    onChange={e => setForm({ ...form, quantity: e.target.value })}
                                    placeholder="0" />
                            </div>
                            {isEdit && (
                                <div className="field-group">
                                    <label>Trạng thái</label>
                                    <select value={form.status}
                                        onChange={e => setForm({ ...form, status: e.target.value })}>
                                        <option value="ACTIVE">Đang bán</option>
                                        <option value="INACTIVE">Ngừng bán</option>
                                    </select>
                                </div>
                            )}
                        </div>
                        <div className="field-group">
                            <label>Ảnh bìa sách</label>
                            <input type="file" accept="image/*"
                                onChange={e => setImageFile(e.target.files[0])} />
                            {form.imageUrl && (
                                <img src={form.imageUrl} alt="preview"
                                    style={{
                                        width: 80, height: 100,
                                        objectFit: 'cover', marginTop: 8, borderRadius: 6
                                    }} />
                            )}
                        </div>
                        <div className="field-group">
                            <label>File PDF sách</label>
                            <input type="file" accept=".pdf"
                                onChange={e => setPdfFile(e.target.files[0])} />
                            {form.pdfPath && (
                                <a href={getPdfUrl(form.pdfPath)} target="_blank" rel="noreferrer"
                                    style={{ fontSize: 12, color: '#4f46e5' }}>
                                    📄 Xem PDF hiện tại
                                </a>
                            )}
                        </div>
                        <div className="field-group">
                            <label>Mô tả</label>
                            <textarea rows={3} value={form.description}
                                onChange={e => setForm({ ...form, description: e.target.value })}
                                placeholder="Mô tả ngắn về sách..."
                                style={{
                                    resize: 'vertical', padding: '11px 14px',
                                    background: 'var(--bg-glass)', border: '1px solid var(--border)',
                                    borderRadius: 'var(--radius-sm)', color: 'var(--text)',
                                    fontSize: 14, fontFamily: 'inherit', outline: 'none'
                                }} />
                        </div>
                        {error && <div className="error-box"><span>✕</span> {error}</div>}
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn-cancel" onClick={onClose}>Hủy</button>
                        <button type="submit" className="btn-primary" disabled={loading}>
                            {loading ? <span className="spinner" /> : (isEdit ? 'Lưu thay đổi' : 'Thêm sách')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

//  Confirm Dialog 
function ConfirmDelete({ book, onConfirm, onCancel }) {
    return (
        <div className="modal-overlay" onClick={onCancel}>
            <div className="modal-box modal-sm confirm-box" onClick={e => e.stopPropagation()}>
                <div className="confirm-icon confirm-danger" style={{ margin: '24px auto 0' }}>
                    <svg viewBox="0 0 20 20" fill="currentColor" width="24" height="24">
                        <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9z" clipRule="evenodd" />
                    </svg>
                </div>
                <h3>Xóa sách</h3>
                <p>Bạn có chắc muốn xóa <strong>"{book.title}"</strong>?</p>
                <div className="modal-footer">
                    <button className="btn-cancel" onClick={onCancel}>Hủy</button>
                    <button className="btn-primary btn-danger" onClick={onConfirm}>Xác nhận</button>
                </div>
            </div>
        </div>
    );
}

//  Main Page 
export default function BookListPage() {
    const { user: me, logout } = useAuth();
    const navigate = useNavigate();
    const isAdmin = me?.role === 'ADMIN';

    const [books, setBooks] = useState([]);
    const [loading, setLoading] = useState(true);
    const [search, setSearch] = useState('');
    const [showForm, setShowForm] = useState(false);
    const [editTarget, setEditTarget] = useState(null);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [toast, setToast] = useState('');
    const [isSidebarOpen, setIsSidebarOpen] = useState(true);

    const load = async () => {
        setLoading(true);
        try { setBooks(await api.getBooks()); }
        catch { showToast('Không thể tải dữ liệu sách'); }
        finally { setLoading(false); }
    };

    useEffect(() => { load(); }, []);

    const showToast = (msg) => {
        setToast(msg);
        setTimeout(() => setToast(''), 3000);
    };

    const filtered = books.filter(b =>
        b.title?.toLowerCase().includes(search.toLowerCase()) ||
        b.author?.toLowerCase().includes(search.toLowerCase()) ||
        b.category?.toLowerCase().includes(search.toLowerCase())
    );

    const stats = {
        total: books.length,
        active: books.filter(b => b.status === 'ACTIVE').length,
        inactive: books.filter(b => b.status === 'INACTIVE').length,
        lowStock: books.filter(b => b.quantity <= 5).length,
    };

    const handleDelete = async () => {
        const { data } = await api.deleteBook(deleteTarget.id);
        setDeleteTarget(null);
        showToast(data.message);
        load();
    };


    const fmtPrice = (p) => new Intl.NumberFormat('vi-VN').format(p) + ' đ';

    const handleLogout = () => {
        logout();
        navigate('/login', { replace: true });
    };

    return (
        <div className="dashboard">
            {/* SIDEBAR OVERLAY */}
            <div className={`sidebar-overlay ${isSidebarOpen ? 'active' : ''}`} onClick={() => setIsSidebarOpen(false)} />

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
                    <Link className="nav-item active" to="/books">📚 Quản lý sách</Link>
                    <Link className="nav-item" to="/cart">🛒 Mua sách</Link>
                    <Link className="nav-item" to="/orders">📦 Đơn hàng</Link>
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
                        <div><h1>📚 Quản lý sách</h1></div>
                    </div>
                    <div className="page-actions">
                        {isAdmin && (
                            <button className="btn-primary" onClick={() => { setEditTarget(null); setShowForm(true); }}>
                                + Thêm sách
                            </button>
                        )}
                    </div>
                </div>

                {/* Stats */}
                <div className="stats-row">
                    <div className="stat-card"><div className="stat-label">Tổng sách</div><div className="stat-value cyan">{stats.total}</div></div>
                    <div className="stat-card"><div className="stat-label">Đang bán</div><div className="stat-value green">{stats.active}</div></div>
                    <div className="stat-card"><div className="stat-label">Ngừng bán</div><div className="stat-value red">{stats.inactive}</div></div>
                    <div className="stat-card"><div className="stat-label">Sắp hết</div><div className="stat-value yellow">{stats.lowStock}</div></div>
                </div>

                {/* Toolbar */}
                <div className="toolbar">
                    <div className="search-wrap">
                        <svg viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd" />
                        </svg>
                        <input type="text" placeholder="Tìm tên sách, tác giả, thể loại..."
                            value={search} onChange={e => setSearch(e.target.value)} />
                    </div>
                </div>

                {/* Table */}
                {loading ? (
                    <div className="loading-state"><div className="loading-spinner" />Đang tải dữ liệu...</div>
                ) : (
                    <div className="table-wrap">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>Ảnh</th>
                                    <th>Tên sách</th>
                                    <th>Tác giả</th>
                                    <th>Thể loại</th>
                                    <th>Giá</th>
                                    <th>Tồn kho</th>
                                    <th>Trạng thái</th>
                                    {isAdmin && <th>Thao tác</th>}
                                </tr>
                            </thead>
                            <tbody>
                                {filtered.length === 0 ? (
                                    <tr><td colSpan={isAdmin ? 9 : 8} className="empty-row">Không tìm thấy sách nào</td></tr>
                                ) : filtered.map(b => (
                                    <tr key={b.id}>
                                        <td>{b.id}</td>
                                        <td>
                                            {b.imageUrl
                                                ? <img src={b.imageUrl} alt={b.title} style={{ width: 40, height: 54, objectFit: 'cover', borderRadius: 4, border: '1px solid var(--border)' }} />
                                                : <div style={{ width: 40, height: 54, background: 'var(--bg-glass)', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18 }}>📖</div>
                                            }
                                        </td>
                                        <td><strong>{b.title}</strong></td>
                                        <td>{b.author || '—'}</td>
                                        <td>{b.category || '—'}</td>
                                        <td style={{ color: 'var(--accent)', fontWeight: 700 }}>{fmtPrice(b.price)}</td>
                                        <td>
                                            <span style={{ color: b.quantity <= 5 ? 'var(--red)' : 'var(--text)', fontWeight: b.quantity <= 5 ? 700 : 400 }}>
                                                {b.quantity}
                                            </span>
                                        </td>
                                        <td>
                                            <span className={`status-badge ${b.status === 'ACTIVE' ? 'badge-act' : 'badge-del'}`}>
                                                {b.status === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}
                                            </span>
                                        </td>
                                        {isAdmin && (
                                            <td>
                                                <div className="action-cell">
                                                    <button className="btn-icon" title="Sửa"
                                                        onClick={() => { setEditTarget(b); setShowForm(true); }}>
                                                        <svg viewBox="0 0 20 20" fill="currentColor">
                                                            <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
                                                        </svg>
                                                    </button>
                                                    <button className="btn-icon btn-icon-danger" title="Xóa"
                                                        onClick={() => setDeleteTarget(b)}>
                                                        <svg viewBox="0 0 20 20" fill="currentColor">
                                                            <path fillRule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                                                        </svg>
                                                    </button>
                                                </div>
                                            </td>
                                        )}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </main>

            {/* MODALS */}
            {showForm && (
                <BookFormModal book={editTarget} onClose={() => setShowForm(false)}
                    onSaved={(msg) => { setShowForm(false); showToast(msg); load(); }} />
            )}
            {deleteTarget && (
                <ConfirmDelete book={deleteTarget} onConfirm={handleDelete} onCancel={() => setDeleteTarget(null)} />
            )}
            {toast && (
                <div style={{
                    position: 'fixed', bottom: 24, right: 24, background: '#10b981', color: '#fff',
                    padding: '12px 20px', borderRadius: '12px', fontSize: 14, zIndex: 9999,
                    boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)', fontWeight: 600,
                    display: 'flex', alignItems: 'center', gap: '8px'
                }}>
                    <span>✅</span> {toast}
                </div>
            )}
        </div>
    );
}
