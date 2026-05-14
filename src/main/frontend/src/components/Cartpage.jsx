import { useEffect, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';

//  Cart Context (lưu giỏ hàng trong localStorage) 
const getCart = () => {
    try { return JSON.parse(localStorage.getItem('cart') || '[]'); } catch { return []; }
};
const saveCart = (cart) => localStorage.setItem('cart', JSON.stringify(cart));

// Modal Checkout 
function CheckoutModal({ cart, username, onClose, onSuccess }) {
    const [form, setForm] = useState({ customerName: '', phone: '', address: '' });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        if (username) {
            api.getDeliveryProfile(username)
                .then(data => {
                    if (data) {
                        setForm({
                            customerName: data.customerName || '',
                            phone: data.phone || '',
                            address: data.address || ''
                        });
                    }
                })
                .catch(err => console.log('Lỗi lấy profile:', err));
        }
    }, [username]);

    const total = cart.reduce((s, i) => s + i.price * i.quantity, 0);
    const fmtPrice = (p) => new Intl.NumberFormat('vi-VN').format(p) + ' đ';

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.customerName.trim()) { setError('Vui lòng nhập tên người nhận'); return; }
        if (!form.phone.trim()) { setError('Vui lòng nhập số điện thoại'); return; }
        if (!form.address.trim()) { setError('Vui lòng nhập địa chỉ giao hàng'); return; }
        setLoading(true); setError('');
        try {
            const { ok, data } = await api.checkout({
                username,
                customerName: form.customerName,
                phone: form.phone,
                address: form.address,
                items: cart.map(i => ({ bookId: i.id, quantity: i.quantity })),
            });
            if (ok && data.success === 'true') onSuccess(data.message);
            else setError(data.message || 'Đặt hàng thất bại');
        } catch { setError('Lỗi kết nối server'); }
        finally { setLoading(false); }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box" style={{ maxWidth: 500 }} onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>🛒 Xác nhận đặt hàng</h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>
                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        {/* Tóm tắt đơn hàng */}
                        <div style={{
                            background: 'var(--bg-glass)', border: '1px solid var(--border)',
                            borderRadius: 'var(--radius-sm)', padding: '14px 16px', marginBottom: 4
                        }}>
                            <div style={{
                                fontSize: 12, fontWeight: 700, textTransform: 'uppercase',
                                letterSpacing: '0.7px', color: 'var(--text-dim)', marginBottom: 10
                            }}>
                                Tóm tắt đơn hàng
                            </div>
                            {cart.map(item => (
                                <div key={item.id} style={{
                                    display: 'flex', justifyContent: 'space-between',
                                    fontSize: 13, padding: '4px 0', borderBottom: '1px solid var(--border)'
                                }}>
                                    <span>{item.title} <span style={{ color: 'var(--text-dim)' }}>x{item.quantity}</span></span>
                                    <span style={{ fontWeight: 600, color: 'var(--accent)' }}>
                                        {fmtPrice(item.price * item.quantity)}
                                    </span>
                                </div>
                            ))}
                            <div style={{
                                display: 'flex', justifyContent: 'space-between',
                                marginTop: 10, fontWeight: 700, fontSize: 15
                            }}>
                                <span>Tổng cộng</span>
                                <span style={{ color: 'var(--accent)' }}>{fmtPrice(total)}</span>
                            </div>
                        </div>

                        <div className="field-group">
                            <label>Tên người nhận <span className="req">*</span></label>
                            <input type="text" value={form.customerName}
                                onChange={e => setForm({ ...form, customerName: e.target.value })}
                                placeholder="Nhập họ và tên" />
                        </div>
                        <div className="field-group">
                            <label>Số điện thoại <span className="req">*</span></label>
                            <input type="text" value={form.phone}
                                onChange={e => setForm({ ...form, phone: e.target.value })}
                                placeholder="0xxxxxxxxx" />
                        </div>
                        <div className="field-group">
                            <label>Địa chỉ giao hàng <span className="req">*</span></label>
                            <input type="text" value={form.address}
                                onChange={e => setForm({ ...form, address: e.target.value })}
                                placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành" />
                        </div>
                        {error && <div className="error-box"><span>✕</span> {error}</div>}
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn-cancel" onClick={onClose}>Hủy</button>
                        <button type="submit" className="btn-primary" disabled={loading}>
                            {loading ? <span className="spinner" /> : '✅ Đặt hàng'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

//  Main Cart Page 
export default function CartPage() {
    const { user: me, logout } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const isAdmin = me?.role === 'ADMIN';

    const [books, setBooks] = useState([]);
    const [cart, setCart] = useState(getCart());
    const [loadingBooks, setLoadingBooks] = useState(true);
    const [search, setSearch] = useState('');
    const [showCheckout, setShowCheckout] = useState(false);
    const [toast, setToast] = useState('');
    const [isSidebarOpen, setIsSidebarOpen] = useState(true);
    const [activeTab, setActiveTab] = useState(location.state?.tab || 'shop'); // 'shop' | 'cart'

    useEffect(() => {
        api.getBooks()
            .then(data => setBooks(data.filter(b => b.status === 'ACTIVE')))
            .catch(() => showToastMsg('Không thể tải danh sách sách'))
            .finally(() => setLoadingBooks(false));
    }, []);

    const showToastMsg = (msg) => {
        setToast(msg); setTimeout(() => setToast(''), 3000);
    };

    const fmtPrice = (p) => new Intl.NumberFormat('vi-VN').format(p) + ' đ';

    //  Cart actions 
    const addToCart = (book) => {
        const existing = cart.find(i => i.id === book.id);
        let newCart;
        if (existing) {
            if (existing.quantity >= book.quantity) {
                showToastMsg(`"${book.title}" đã đạt số lượng tối đa!`); return;
            }
            newCart = cart.map(i => i.id === book.id ? { ...i, quantity: i.quantity + 1 } : i);
        } else {
            newCart = [...cart, {
                id: book.id, title: book.title, price: book.price,
                imageUrl: book.imageUrl, quantity: 1, maxQty: book.quantity
            }];
        }
        setCart(newCart); saveCart(newCart);
        showToastMsg(`Đã thêm "${book.title}" vào giỏ!`);
    };

    const updateQty = (id, delta) => {
        const newCart = cart.map(i => {
            if (i.id !== id) return i;
            const q = i.quantity + delta;
            if (q <= 0) return null;
            if (q > i.maxQty) { showToastMsg('Không đủ số lượng tồn kho!'); return i; }
            return { ...i, quantity: q };
        }).filter(Boolean);
        setCart(newCart); saveCart(newCart);
    };

    const removeFromCart = (id) => {
        const newCart = cart.filter(i => i.id !== id);
        setCart(newCart); saveCart(newCart);
    };

    const clearCart = () => { setCart([]); saveCart([]); };

    const cartTotal = cart.reduce((s, i) => s + i.price * i.quantity, 0);
    const cartCount = cart.reduce((s, i) => s + i.quantity, 0);

    const filteredBooks = books.filter(b =>
        b.title?.toLowerCase().includes(search.toLowerCase()) ||
        b.author?.toLowerCase().includes(search.toLowerCase()) ||
        b.category?.toLowerCase().includes(search.toLowerCase())
    );

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
                    <Link className="nav-item active" to="/cart">🛒 Mua sách</Link>
                    <Link className="nav-item" to="/orders">📦 Đơn hàng của tôi</Link>
                </nav>
                <div className="sidebar-footer">
                    {me ? (
                        <>
                            <div className="sidebar-user">
                                <div className="avatar">{me.username?.[0]?.toUpperCase()}</div>
                                <div className="sidebar-user-info">
                                    <div className="sidebar-username">{me.username}</div>
                                    <div className="sidebar-role">{me.role}</div>
                                </div>
                            </div>
                            <button className="btn-logout" onClick={handleLogout} title="Đăng xuất">
                                <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none">
                                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" />
                                </svg>
                            </button>
                        </>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', width: '100%' }}>
                            <Link to="/login" style={{ textAlign: 'center', background: 'var(--accent)', color: '#fff', padding: '10px', borderRadius: '8px', textDecoration: 'none', fontWeight: 600 }}>Đăng nhập</Link>
                            <Link to="/register" style={{ textAlign: 'center', background: 'var(--bg-glass)', color: 'var(--text)', padding: '10px', borderRadius: '8px', textDecoration: 'none', fontWeight: 600, border: '1px solid var(--border)' }}>Đăng ký</Link>
                        </div>
                    )}
                </div>
            </aside>

            {/* MAIN */}
            <main className="main-content cart-main">
                <div className="page-header">
                    <div className="header-left">
                        {!isSidebarOpen && (
                            <button className="btn-toggle-sidebar" onClick={() => setIsSidebarOpen(true)}>
                                <svg viewBox="0 0 24 24" width="22" height="22" stroke="currentColor" strokeWidth="2" fill="none">
                                    <line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="18" x2="21" y2="18" />
                                </svg>
                            </button>
                        )}
                        <h1>🛒 Mua sách</h1>
                    </div>
                </div>

                <div className="cart-page-scroll">
                    {/* Tabs */}
                    <div style={{ display: 'flex', gap: 8, padding: '0 32px 20px' }}>
                        <button
                            onClick={() => setActiveTab('shop')}
                            style={{
                                padding: '8px 20px', borderRadius: 'var(--radius-sm)', border: 'none',
                                background: activeTab === 'shop' ? 'var(--accent)' : 'var(--bg-glass)',
                                color: activeTab === 'shop' ? '#fff' : 'var(--text-dim)',
                                fontWeight: 600, cursor: 'pointer', fontSize: 14, transition: 'all 0.2s'
                            }}>
                            📖 Danh sách sách
                        </button>
                        <button
                            onClick={() => setActiveTab('cart')}
                            style={{
                                padding: '8px 20px', borderRadius: 'var(--radius-sm)', border: 'none',
                                background: activeTab === 'cart' ? 'var(--accent)' : 'var(--bg-glass)',
                                color: activeTab === 'cart' ? '#fff' : 'var(--text-dim)',
                                fontWeight: 600, cursor: 'pointer', fontSize: 14, transition: 'all 0.2s',
                                display: 'flex', alignItems: 'center', gap: 6
                            }}>
                            🛒 Giỏ hàng
                            {cartCount > 0 && (
                                <span style={{
                                    background: '#ef4444', color: '#fff', borderRadius: '50%',
                                    width: 20, height: 20, fontSize: 11, fontWeight: 700,
                                    display: 'flex', alignItems: 'center', justifyContent: 'center'
                                }}>
                                    {cartCount}
                                </span>
                            )}
                        </button>
                    </div>

                    {/*  TAB: SHOP  */}
                    {activeTab === 'shop' && (
                        <>
                            <div className="toolbar">
                                <div className="search-wrap">
                                    <svg viewBox="0 0 20 20" fill="currentColor">
                                        <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd" />
                                    </svg>
                                    <input type="text" placeholder="Tìm tên sách, tác giả, thể loại..."
                                        value={search} onChange={e => setSearch(e.target.value)} />
                                </div>
                            </div>

                            {loadingBooks ? (
                                <div className="loading-state"><div className="loading-spinner" />Đang tải sách...</div>
                            ) : (
                                <div className="book-grid-4">
                                    {filteredBooks.length === 0 ? (
                                        <div style={{
                                            gridColumn: '1/-1', textAlign: 'center', padding: '60px 0',
                                            color: 'var(--text-dim)'
                                        }}>Không tìm thấy sách nào</div>
                                    ) : filteredBooks.map(book => (
                                        <div key={book.id} className="book-card">
                                            {/* Book cover */}
                                            <div className="book-card-cover">
                                                {book.imageUrl ? (
                                                    <img src={book.imageUrl} alt={book.title} />
                                                ) : (
                                                    <span style={{ fontSize: 48 }}>📖</span>
                                                )}
                                                {book.quantity === 0 && (
                                                    <div className="book-card-badge">Hết hàng</div>
                                                )}
                                            </div>
                                            {/* Info */}
                                            <div className="book-card-body">
                                                <div className="book-card-title">{book.title}</div>
                                                {book.author && (
                                                    <div className="book-card-author">{book.author}</div>
                                                )}
                                                <div>
                                                    {book.category && (
                                                        <span className="book-card-category">{book.category}</span>
                                                    )}
                                                </div>
                                                <div className="book-card-footer">
                                                    <div className="book-card-price-row">
                                                        <span className="book-card-price">{fmtPrice(book.price)}</span>
                                                        <span className={`book-card-stock ${book.quantity <= 5 ? 'low' : 'normal'}`}>
                                                            {book.quantity > 0 ? `Còn ${book.quantity}` : '0'}
                                                        </span>
                                                    </div>
                                                    <button
                                                        className="btn-add-cart"
                                                        disabled={book.quantity === 0}
                                                        onClick={() => addToCart(book)}
                                                    >
                                                        {book.quantity === 0 ? 'Hết hàng' : '+ Thêm vào giỏ'}
                                                    </button>
                                                    <a href={`/dem_login-0.0.1-SNAPSHOT/book-detail?id=${book.id}`}
                                                        style={{
                                                            display: 'block', textAlign: 'center', fontSize: 12,
                                                            color: '#6b7280', marginTop: 6, textDecoration: 'none'
                                                        }}>
                                                        Xem chi tiết →
                                                    </a>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </>
                    )}

                    {/*  TAB: CART  */}
                    {activeTab === 'cart' && (
                        <div style={{ padding: '0 32px 32px' }}>
                            {cart.length === 0 ? (
                                <div style={{ textAlign: 'center', padding: '80px 20px', color: 'var(--text-dim)' }}>
                                    <div style={{ fontSize: 64, marginBottom: 16 }}>🛒</div>
                                    <h3 style={{ fontSize: 18, marginBottom: 8, color: 'var(--text)' }}>Giỏ hàng trống</h3>
                                    <p>Hãy chọn sách và thêm vào giỏ hàng!</p>
                                    <button className="btn-primary" style={{ marginTop: 20 }}
                                        onClick={() => setActiveTab('shop')}>
                                        Xem danh sách sách
                                    </button>
                                </div>
                            ) : (
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 20 }}>
                                    {/* Cart items */}
                                    <div>
                                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                                            <h3 style={{ fontSize: 16, fontWeight: 700 }}>Sách trong giỏ ({cartCount} sản phẩm)</h3>
                                            <button onClick={clearCart}
                                                style={{
                                                    background: 'none', border: 'none', color: 'var(--red)',
                                                    cursor: 'pointer', fontSize: 13, fontWeight: 600
                                                }}>
                                                🗑️ Xóa tất cả
                                            </button>
                                        </div>
                                        {cart.map(item => (
                                            <div key={item.id} style={{
                                                display: 'flex', gap: 14, padding: 16,
                                                background: 'var(--bg-2)', border: '1px solid var(--border)',
                                                borderRadius: 'var(--radius)', marginBottom: 10
                                            }}>
                                                <div style={{
                                                    width: 60, height: 80, flexShrink: 0,
                                                    background: 'var(--bg-glass)', borderRadius: 6,
                                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                    overflow: 'hidden'
                                                }}>
                                                    {item.imageUrl
                                                        ? <img src={item.imageUrl} alt={item.title}
                                                            style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                                        : <span style={{ fontSize: 28 }}>📖</span>
                                                    }
                                                </div>
                                                <div style={{ flex: 1 }}>
                                                    <div style={{ fontWeight: 700, fontSize: 14, marginBottom: 4 }}>{item.title}</div>
                                                    <div style={{ color: 'var(--accent)', fontWeight: 700, fontSize: 15, marginBottom: 10 }}>
                                                        {fmtPrice(item.price)}
                                                    </div>
                                                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                                        <div style={{
                                                            display: 'flex', alignItems: 'center', gap: 6,
                                                            background: 'var(--bg-glass)', borderRadius: 8, padding: '4px 4px'
                                                        }}>
                                                            <button onClick={() => updateQty(item.id, -1)}
                                                                style={{
                                                                    width: 28, height: 28, border: '1px solid var(--border)',
                                                                    borderRadius: 6, background: 'var(--bg-2)',
                                                                    cursor: 'pointer', fontWeight: 700, fontSize: 16
                                                                }}>−</button>
                                                            <span style={{ minWidth: 24, textAlign: 'center', fontWeight: 700 }}>{item.quantity}</span>
                                                            <button onClick={() => updateQty(item.id, +1)}
                                                                style={{
                                                                    width: 28, height: 28, border: '1px solid var(--border)',
                                                                    borderRadius: 6, background: 'var(--bg-2)',
                                                                    cursor: 'pointer', fontWeight: 700, fontSize: 16
                                                                }}>+</button>
                                                        </div>
                                                        <span style={{ fontWeight: 600, fontSize: 13, color: 'var(--text-dim)' }}>
                                                            = {fmtPrice(item.price * item.quantity)}
                                                        </span>
                                                    </div>
                                                </div>
                                                <button onClick={() => removeFromCart(item.id)}
                                                    style={{
                                                        background: 'none', border: 'none',
                                                        color: 'var(--text-dim)', cursor: 'pointer', fontSize: 18,
                                                        alignSelf: 'flex-start'
                                                    }}>✕</button>
                                            </div>
                                        ))}
                                    </div>

                                    {/* Order summary */}
                                    <div style={{
                                        background: 'var(--bg-2)', border: '1px solid var(--border)',
                                        borderRadius: 'var(--radius)', padding: 20, height: 'fit-content', position: 'sticky', top: 20
                                    }}>
                                        <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 16 }}>Tóm tắt đơn hàng</h3>
                                        {cart.map(item => (
                                            <div key={item.id} style={{
                                                display: 'flex', justifyContent: 'space-between',
                                                fontSize: 13, padding: '5px 0', borderBottom: '1px solid var(--border)'
                                            }}>
                                                <span style={{ color: 'var(--text-dim)' }}>{item.title} x{item.quantity}</span>
                                                <span style={{ fontWeight: 600 }}>{fmtPrice(item.price * item.quantity)}</span>
                                            </div>
                                        ))}
                                        <div style={{
                                            display: 'flex', justifyContent: 'space-between',
                                            marginTop: 14, paddingTop: 14, borderTop: '2px solid var(--border)',
                                            fontWeight: 700, fontSize: 17
                                        }}>
                                            <span>Tổng cộng</span>
                                            <span style={{ color: 'var(--accent)' }}>{fmtPrice(cartTotal)}</span>
                                        </div>
                                        <button className="btn-primary" style={{
                                            width: '100%', marginTop: 16,
                                            justifyContent: 'center', padding: 14
                                        }}
                                            onClick={() => setShowCheckout(true)}>
                                            ✅ Thanh toán
                                        </button>
                                        <button className="btn-cancel" style={{
                                            width: '100%', marginTop: 8,
                                            textAlign: 'center', padding: 10
                                        }}
                                            onClick={() => setActiveTab('shop')}>
                                            ← Tiếp tục mua sách
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </main>

            {/* MODALS */}
            {showCheckout && (
                <CheckoutModal
                    cart={cart}
                    username={me?.username}
                    onClose={() => setShowCheckout(false)}
                    onSuccess={(msg) => {
                        setShowCheckout(false);
                        clearCart();
                        setActiveTab('shop');
                        showToastMsg(msg);
                    }}
                />
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
