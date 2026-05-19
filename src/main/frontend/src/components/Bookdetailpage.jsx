import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';

const fmt = (p) => new Intl.NumberFormat('vi-VN').format(p) + ' đ';
const getCart = () => { try { return JSON.parse(localStorage.getItem('cart') || '[]'); } catch { return []; } };
const saveCart = (c) => localStorage.setItem('cart', JSON.stringify(c));

export default function BookDetailPage() {
    const { user: me, logout } = useAuth();
    const navigate = useNavigate();
    const isAdmin = me?.role === 'ADMIN';

    // Lấy bookId từ URL query: /book-detail?id=1
    const bookId = new URLSearchParams(window.location.search).get('id');

    const [book, setBook] = useState(null);
    const [books, setBooks] = useState([]); // sách liên quan
    const [loading, setLoading] = useState(true);
    const [cart, setCart] = useState(getCart());
    const [toast, setToast] = useState('');
    const [isSidebarOpen, setIsSidebarOpen] = useState(true);
    const [showCart, setShowCart] = useState(false);
    const [showCheckout, setShowCheckout] = useState(false);
    const [checkoutForm, setCheckoutForm] = useState({ customerName: '', phone: '', address: '' });
    const [checkoutError, setCheckoutError] = useState('');
    const [checkoutLoading, setCheckoutLoading] = useState(false);



    useEffect(() => {
        if (!bookId) return;
        Promise.all([
            fetch(`/dem_login-0.0.1-SNAPSHOT/api/books/${bookId}`).then(r => r.json()),
            api.getBooks(),
        ]).then(([bookData, allBooks]) => {
            setBook(bookData);
            setBooks(allBooks.filter(b => b.id !== parseInt(bookId) && b.status === 'ACTIVE').slice(0, 4));
        }).catch(() => { })
            .finally(() => setLoading(false));

        // Lắng nghe sự kiện cập nhật giỏ hàng từ quá trình đăng nhập/sync
        const handleCartUpdate = () => {
            try { setCart(JSON.parse(localStorage.getItem('cart') || '[]')); } catch { }
        };
        window.addEventListener('cartUpdated', handleCartUpdate);
        return () => window.removeEventListener('cartUpdated', handleCartUpdate);
    }, [bookId]);

    // Tự động đồng bộ giỏ hàng lên server khi có thay đổi (nếu đã đăng nhập)
    useEffect(() => {
        if (me && !loading) {
            const timeout = setTimeout(() => {
                fetch('/dem_login-0.0.1-SNAPSHOT/api/users/cart/sync', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ 
                        username: me.username, 
                        cartData: JSON.stringify(cart) 
                    })
                }).catch(e => console.error('Lỗi đồng bộ giỏ hàng:', e));
            }, 1000); // debounce 1s
            return () => clearTimeout(timeout);
        }
    }, [cart, me, loading]);

    const showToastMsg = (msg) => { setToast(msg); setTimeout(() => setToast(''), 3000); };

    const addToCart = (b) => {

        const ex = cart.find(i => i.id === b.id);
        let newCart;
        if (ex) {
            if (ex.quantity >= b.quantity) { showToastMsg('Đã đạt số lượng tối đa!'); return; }
            newCart = cart.map(i => i.id === b.id ? { ...i, quantity: i.quantity + 1 } : i);
        } else {
            newCart = [...cart, {
                id: b.id, title: b.title, price: b.price,
                imageUrl: b.imageUrl, quantity: 1, maxQty: b.quantity
            }];
        }
        setCart(newCart); saveCart(newCart);
        showToastMsg(`Đã thêm "${b.title}" vào giỏ hàng!`);
    };

    const cartCount = cart.reduce((s, i) => s + i.quantity, 0);
    const cartTotal = cart.reduce((s, i) => s + i.price * i.quantity, 0);

    const removeFromCart = (id) => {
        const newCart = cart.filter(i => i.id !== id);
        setCart(newCart); saveCart(newCart);
    };

    const handleCheckout = () => {
        if (!me) {
            localStorage.setItem('redirectAfterLogin', '/cart');
            showToastMsg('Vui lòng đăng nhập để thanh toán!');
            setTimeout(() => navigate('/login'), 1500);
            return;
        }
        // Pre-fill form with profile
        if (me.username) {
            fetch(`/dem_login-0.0.1-SNAPSHOT/api/profiles/${me.username}`)
                .then(r => r.json())
                .then(data => {
                    if (data) setCheckoutForm({
                        customerName: data.customerName || me.username,
                        phone: data.phone || '',
                        address: data.address || ''
                    });
                }).catch(() => setCheckoutForm(f => ({ ...f, customerName: me.username })));
        }
        setShowCheckout(true);
        setShowCart(false);
    };

    const handlePlaceOrder = async (e) => {
        e.preventDefault();
        if (!checkoutForm.customerName.trim()) { setCheckoutError('Vui lòng nhập tên người nhận'); return; }
        if (!checkoutForm.phone.trim()) { setCheckoutError('Vui lòng nhập số điện thoại'); return; }
        if (!checkoutForm.address.trim()) { setCheckoutError('Vui lòng nhập địa chỉ giao hàng'); return; }
        setCheckoutLoading(true); setCheckoutError('');
        try {
            const res = await fetch('/dem_login-0.0.1-SNAPSHOT/api/orders', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username: me.username,
                    customerName: checkoutForm.customerName,
                    phone: checkoutForm.phone,
                    address: checkoutForm.address,
                    items: cart.map(i => ({ bookId: i.id, quantity: i.quantity }))
                })
            });
            const data = await res.json();
            if (res.ok && (data.success === 'true' || data.success === true)) {
                setShowCheckout(false);
                setCart([]); saveCart([]);
                showToastMsg('🎉 Đặt hàng thành công!');
            } else {
                setCheckoutError(data.message || 'Đặt hàng thất bại');
            }
        } catch { setCheckoutError('Lỗi kết nối server'); }
        finally { setCheckoutLoading(false); }
    };

    if (loading) return (
        <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            height: '100vh', gap: 12, color: '#6b7280'
        }}>
            <div style={{
                width: 24, height: 24, border: '3px solid #e5e7eb',
                borderTopColor: '#4f46e5', borderRadius: '50%',
                animation: 'spin 0.8s linear infinite'
            }} />
            Đang tải...
            <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
        </div>
    );

    if (!book) return (
        <div style={{ textAlign: 'center', padding: '80px 20px' }}>
            <div style={{ fontSize: 52 }}>📭</div>
            <h2>Không tìm thấy sách</h2>
            <a href="/dem_login-0.0.1-SNAPSHOT/cart"
                style={{ color: '#4f46e5', fontWeight: 600 }}>← Quay lại mua sách</a>
        </div>
    );

    return (
        <div className="dashboard" style={{ background: '#f0f2ff' }}>
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
                    {isAdmin && <a className="nav-item" href="/dem_login-0.0.1-SNAPSHOT/users">👥 Quản lý người dùng</a>}
                    <a className="nav-item" href="/dem_login-0.0.1-SNAPSHOT/customers">🧑‍💼 Khách hàng</a>
                    {isAdmin && <a className="nav-item" href="/dem_login-0.0.1-SNAPSHOT/books">📚 Quản lý sách</a>}
                    <a className="nav-item active" href="/dem_login-0.0.1-SNAPSHOT/cart">🛒 Mua sách</a>
                    <a className="nav-item" href="/dem_login-0.0.1-SNAPSHOT/orders">📦 Đơn hàng</a>
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
                            <button className="btn-logout" onClick={() => { logout(); setCart([]); saveCart([]); }} title="Đăng xuất">
                                <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none">
                                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                                    <polyline points="16 17 21 12 16 7" />
                                    <line x1="21" y1="12" x2="9" y2="12" />
                                </svg>
                            </button>
                        </>
                    ) : (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', width: '100%' }}>
                            <Link to="/login" style={{ textAlign: 'center', background: '#4f46e5', color: '#fff', padding: '10px', borderRadius: '8px', textDecoration: 'none', fontWeight: 600 }}>Đăng nhập</Link>
                            <Link to="/register" style={{ textAlign: 'center', background: '#f3f4f6', color: '#374151', padding: '10px', borderRadius: '8px', textDecoration: 'none', fontWeight: 600, border: '1px solid #e5e7eb' }}>Đăng ký</Link>
                        </div>
                    )}
                </div>
            </aside>

            {/* MAIN */}
            <main className="main-content" style={{ background: '#f0f2ff', padding: '28px 32px' }}>
                {!isSidebarOpen && (
                    <button className="btn-toggle-sidebar" style={{ marginBottom: 16 }}
                        onClick={() => setIsSidebarOpen(true)}>
                        <svg viewBox="0 0 24 24" width="22" height="22" stroke="currentColor" strokeWidth="2" fill="none">
                            <line x1="3" y1="12" x2="21" y2="12" /><line x1="3" y1="6" x2="21" y2="6" /><line x1="3" y1="18" x2="21" y2="18" />
                        </svg>
                    </button>
                )}

                {/* Header Section */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                    {/* Breadcrumb */}
                    <div style={{ fontSize: 13, color: '#6b7280' }}>
                        <a href="/dem_login-0.0.1-SNAPSHOT/cart"
                            style={{ color: '#4f46e5', textDecoration: 'none', fontWeight: 600 }}>
                            ← Danh sách sách
                        </a>
                        <span style={{ margin: '0 8px' }}>›</span>
                        <span>{book.title}</span>
                    </div>

                    {/* Cart Icon - click to go to cart page */}
                    <div
                        style={{ position: 'relative', cursor: 'pointer', padding: '8px', background: '#fff', borderRadius: '50%', boxShadow: '0 2px 8px rgba(0,0,0,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', width: 40, height: 40 }}
                        onClick={() => navigate('/cart', { state: { tab: 'cart' } })}
                        title="Đi đến giỏ hàng"
                    >
                        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#ee4d2d" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <circle cx="9" cy="21" r="1"></circle><circle cx="20" cy="21" r="1"></circle>
                            <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                        </svg>
                        {cartCount > 0 && (
                            <span style={{ position: 'absolute', top: -4, right: -4, background: '#ee4d2d', color: '#fff', borderRadius: '50%', minWidth: 20, height: 20, fontSize: 12, fontWeight: 'bold', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px solid #f0f2ff' }}>
                                {cartCount}
                            </span>
                        )}
                    </div>
                </div>

                {/* Book detail card */}
                <style>{`
                    .book-detail-card {
                        background: #fff; border-radius: 20px; padding: 32px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.08); margin-bottom: 32px;
                        display: grid; grid-template-columns: 300px 1fr; gap: 32px;
                    }
                    .book-cover-wrap {
                        border-radius: 16px; overflow: hidden; height: 400px;
                        background: linear-gradient(135deg,#ede9fe,#ddd6fe);
                        display: flex; align-items: center; justify-content: center;
                    }
                    .book-actions-wrap { display: flex; gap: 12px; margin-top: auto; }
                    
                    @media (max-width: 800px) {
                        .book-detail-card {
                            grid-template-columns: 1fr;
                            padding: 24px; gap: 24px;
                        }
                        .book-cover-wrap { height: 320px; }
                        .book-actions-wrap { flex-direction: column; }
                    }
                `}</style>
                <div className="book-detail-card">

                    {/* Cover */}
                    <div className="book-cover-wrap">
                        {book.imageUrl
                            ? <img src={book.imageUrl} alt={book.title}
                                style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                            : <span style={{ fontSize: 80 }}>📖</span>
                        }
                    </div>

                    {/* Info */}
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                        <div>
                            {book.category && (
                                <span style={{
                                    fontSize: 12, fontWeight: 700,
                                    background: '#ede9fe', color: '#6d28d9',
                                    padding: '4px 12px', borderRadius: 20, marginBottom: 10,
                                    display: 'inline-block'
                                }}>
                                    {book.category}
                                </span>
                            )}
                            <h1 style={{
                                fontSize: 28, fontWeight: 800, color: '#111827',
                                margin: '10px 0 6px', lineHeight: 1.3
                            }}>
                                {book.title}
                            </h1>
                            {book.author && (
                                <p style={{ fontSize: 15, color: '#6b7280', margin: 0 }}>
                                    Tác giả: <strong style={{ color: '#374151' }}>{book.author}</strong>
                                </p>
                            )}
                        </div>

                        {/* Price */}
                        <div style={{
                            display: 'flex', alignItems: 'center', gap: 16,
                            padding: '16px 20px', background: '#fafafa',
                            borderRadius: 4, border: 'none'
                        }}>
                            <span style={{ fontSize: 32, fontWeight: 500, color: '#ee4d2d' }}>
                                {fmt(book.price)}
                            </span>
                            <span style={{
                                fontSize: 13, color: book.quantity <= 5 ? '#ef4444' : '#757575',
                                fontWeight: 500
                            }}>
                                Còn {book.quantity} cuốn
                            </span>
                        </div>

                        {/* Description */}
                        {book.description && (
                            <div>
                                <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 8 }}>Mô tả</h3>
                                <p style={{
                                    fontSize: 14, color: '#374151', lineHeight: 1.7,
                                    background: '#fafafa', padding: 16, borderRadius: 10,
                                    border: '1px solid #f3f4f6'
                                }}>
                                    {book.description}
                                </p>
                            </div>
                        )}

                        {/* Actions */}
                        <div className="book-actions-wrap">
                            <button
                                disabled={book.quantity === 0}
                                onClick={() => addToCart(book)}
                                style={{
                                    flex: 1, padding: '14px 24px', border: book.quantity === 0 ? '1px solid #e5e7eb' : '1px solid #ee4d2d',
                                    borderRadius: 4, fontSize: 15, fontWeight: 500,
                                    background: book.quantity === 0 ? '#f3f4f6' : 'rgba(255, 87, 34, 0.1)',
                                    color: book.quantity === 0 ? '#9ca3af' : '#ee4d2d',
                                    cursor: book.quantity === 0 ? 'not-allowed' : 'pointer',
                                    transition: 'all .2s',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px'
                                }}
                                onMouseEnter={(e) => { if (book.quantity > 0) e.currentTarget.style.background = 'rgba(255, 87, 34, 0.2)'; }}
                                onMouseLeave={(e) => { if (book.quantity > 0) e.currentTarget.style.background = 'rgba(255, 87, 34, 0.1)'; }}>
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                    <circle cx="9" cy="21" r="1"></circle><circle cx="20" cy="21" r="1"></circle>
                                    <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"></path>
                                </svg>
                                {book.quantity === 0 ? 'Hết hàng' : 'Thêm vào giỏ hàng'}
                            </button>
                            <button
                                disabled={book.quantity === 0}
                                onClick={() => {
                                    if (book.quantity > 0) {
                                        addToCart(book);
                                        navigate('/cart', { state: { tab: 'cart' } });
                                    }
                                }}
                                style={{
                                    flex: 1, padding: '14px 24px', border: 'none',
                                    borderRadius: 4, fontSize: 15, fontWeight: 500,
                                    color: '#fff', cursor: book.quantity === 0 ? 'not-allowed' : 'pointer',
                                    background: book.quantity === 0 ? '#d1d5db' : '#ee4d2d',
                                    transition: 'all .2s',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center'
                                }}
                                onMouseEnter={(e) => { if (book.quantity > 0) e.currentTarget.style.background = '#f05d40'; }}
                                onMouseLeave={(e) => { if (book.quantity > 0) e.currentTarget.style.background = '#ee4d2d'; }}>
                                Mua ngay
                            </button>
                        </div>
                    </div>
                </div>



                {/* Related books */}
                {books.length > 0 && (
                    <div>
                        <h2 style={{ fontSize: 20, fontWeight: 700, marginBottom: 16 }}>
                            📚 Sách liên quan
                        </h2>
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 16
                        }}>
                            {books.map(b => (
                                <a key={b.id}
                                    href={`/dem_login-0.0.1-SNAPSHOT/book-detail?id=${b.id}`}
                                    style={{ textDecoration: 'none', color: 'inherit' }}>
                                    <div style={{
                                        background: '#fff', borderRadius: 14,
                                        border: '1.5px solid #e5e7eb', overflow: 'hidden',
                                        transition: 'all .2s', cursor: 'pointer'
                                    }}
                                        onMouseEnter={e => { e.currentTarget.style.borderColor = '#a5b4fc'; e.currentTarget.style.boxShadow = '0 4px 16px rgba(79,70,229,0.12)'; }}
                                        onMouseLeave={e => { e.currentTarget.style.borderColor = '#e5e7eb'; e.currentTarget.style.boxShadow = 'none'; }}>
                                        <div style={{
                                            height: 140, overflow: 'hidden',
                                            background: 'linear-gradient(135deg,#ede9fe,#ddd6fe)',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center'
                                        }}>
                                            {b.imageUrl
                                                ? <img src={b.imageUrl} alt={b.title}
                                                    style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                                : <span style={{ fontSize: 40 }}>📖</span>
                                            }
                                        </div>
                                        <div style={{ padding: '12px 14px' }}>
                                            <div style={{
                                                fontSize: 13, fontWeight: 700, marginBottom: 6,
                                                display: '-webkit-box', WebkitLineClamp: 2,
                                                WebkitBoxOrient: 'vertical', overflow: 'hidden'
                                            }}>
                                                {b.title}
                                            </div>
                                            <div style={{ fontSize: 15, fontWeight: 800, color: '#4f46e5' }}>
                                                {fmt(b.price)}
                                            </div>
                                        </div>
                                    </div>
                                </a>
                            ))}
                        </div>
                    </div>
                )}
            </main>

            {toast && (
                <div style={{
                    position: 'fixed', bottom: 24, left: '50%',
                    transform: 'translateX(-50%)', background: '#1f2937', color: '#fff',
                    padding: '12px 24px', borderRadius: 12, fontSize: 13, zIndex: 9999,
                    boxShadow: '0 4px 16px rgba(0,0,0,0.25)'
                }}>
                    {toast}
                </div>
            )}



            {/* ===== CHECKOUT MODAL ===== */}
            {showCheckout && (
                <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999, padding: 16 }}
                    onClick={() => setShowCheckout(false)}>
                    <div style={{ background: '#fff', borderRadius: 8, width: '100%', maxWidth: 520, boxShadow: '0 20px 60px rgba(0,0,0,0.2)', overflow: 'hidden' }}
                        onClick={e => e.stopPropagation()}>
                        <div style={{ background: '#ee4d2d', padding: '16px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <span style={{ color: '#fff', fontWeight: 700, fontSize: 17 }}>🛒 Xác nhận đặt hàng</span>
                            <button onClick={() => setShowCheckout(false)} style={{ background: 'rgba(255,255,255,0.2)', border: 'none', borderRadius: '50%', width: 30, height: 30, color: '#fff', cursor: 'pointer', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>✕</button>
                        </div>
                        <form onSubmit={handlePlaceOrder}>
                            <div style={{ padding: '20px 24px', maxHeight: '70vh', overflowY: 'auto' }}>
                                {/* Order Summary */}
                                <div style={{ background: '#fff8f6', border: '1px solid #fde8e2', borderRadius: 8, padding: 16, marginBottom: 20 }}>
                                    <div style={{ fontSize: 13, fontWeight: 700, color: '#ee4d2d', marginBottom: 12 }}>📦 Sản phẩm đặt mua</div>
                                    {cart.map(item => (
                                        <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px dashed #fde8e2' }}>
                                            <div>
                                                <div style={{ fontSize: 13, fontWeight: 600 }}>{item.title}</div>
                                                <div style={{ fontSize: 12, color: '#999' }}>x{item.quantity} × {fmt(item.price)}</div>
                                            </div>
                                            <div style={{ fontWeight: 700, color: '#ee4d2d' }}>{fmt(item.price * item.quantity)}</div>
                                        </div>
                                    ))}
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 10, paddingTop: 10, borderTop: '2px solid #fde8e2' }}>
                                        <span style={{ fontWeight: 700 }}>Tổng thanh toán</span>
                                        <span style={{ fontWeight: 800, fontSize: 18, color: '#ee4d2d' }}>{fmt(cartTotal)}</span>
                                    </div>
                                </div>
                                {/* Delivery fields */}
                                <div style={{ fontSize: 13, fontWeight: 700, color: '#333', marginBottom: 14, display: 'flex', alignItems: 'center', gap: 8 }}>
                                    📍 Thông tin giao hàng
                                </div>
                                {[
                                    { label: 'Tên người nhận', key: 'customerName', placeholder: 'Nhập họ và tên', icon: '👤' },
                                    { label: 'Số điện thoại', key: 'phone', placeholder: '0xxxxxxxxx', icon: '📱' },
                                    { label: 'Địa chỉ giao hàng', key: 'address', placeholder: 'Số nhà, đường, phường, quận, tỉnh/thành', icon: '🏠' },
                                ].map(f => (
                                    <div key={f.key} style={{ marginBottom: 16 }}>
                                        <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#555', marginBottom: 6 }}>
                                            {f.label} <span style={{ color: '#ee4d2d' }}>*</span>
                                        </label>
                                        <div style={{ position: 'relative' }}>
                                            <span style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }}>{f.icon}</span>
                                            <input type="text" value={checkoutForm[f.key]}
                                                onChange={e => setCheckoutForm({ ...checkoutForm, [f.key]: e.target.value })}
                                                placeholder={f.placeholder}
                                                style={{ width: '100%', padding: '10px 14px 10px 40px', border: '1.5px solid #e5e7eb', borderRadius: 6, fontSize: 14, boxSizing: 'border-box', outline: 'none' }}
                                                onFocus={e => e.target.style.borderColor = '#ee4d2d'}
                                                onBlur={e => e.target.style.borderColor = '#e5e7eb'}
                                            />
                                        </div>
                                    </div>
                                ))}
                                {checkoutError && (
                                    <div style={{ background: '#fef2f2', border: '1px solid #fca5a5', borderRadius: 6, padding: '10px 14px', color: '#dc2626', fontSize: 13 }}>
                                        ⚠️ {checkoutError}
                                    </div>
                                )}
                            </div>
                            <div style={{ padding: '16px 24px', borderTop: '1px solid #f3f4f6', display: 'flex', gap: 12, justifyContent: 'flex-end', background: '#fafafa' }}>
                                <button type="button" onClick={() => setShowCheckout(false)} style={{ padding: '10px 20px', border: '1.5px solid #e5e7eb', borderRadius: 4, background: '#fff', color: '#555', cursor: 'pointer', fontSize: 14, fontWeight: 600 }}>Hủy</button>
                                <button type="submit" disabled={checkoutLoading} style={{ padding: '10px 28px', background: '#ee4d2d', color: '#fff', border: 'none', borderRadius: 4, fontWeight: 700, fontSize: 15, cursor: checkoutLoading ? 'not-allowed' : 'pointer', minWidth: 150, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                                    {checkoutLoading ? 'Đang xử lý...' : '🛒 Đặt hàng ngay'}
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}