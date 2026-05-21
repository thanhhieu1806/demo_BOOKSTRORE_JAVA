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
                            customerName: data.customerName || username,
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
        <div style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            zIndex: 9999, padding: 16, backdropFilter: 'blur(4px)'
        }} onClick={onClose}>
            <div style={{
                background: '#fff', borderRadius: 12, width: '100%', maxWidth: 480,
                boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)', overflow: 'hidden',
                animation: 'modalSlideIn 0.3s ease-out'
            }} onClick={e => e.stopPropagation()}>
                <style>{`
                    @keyframes modalSlideIn {
                        from { transform: translateY(20px); opacity: 0; }
                        to { transform: translateY(0); opacity: 1; }
                    }
                    @keyframes spin { to { transform: rotate(360deg); } }
                `}</style>

                {/* Header - Shopee Style */}
                <div style={{
                    background: '#ee4d2d', padding: '18px 24px',
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                            <circle cx="9" cy="21" r="1" /><circle cx="20" cy="21" r="1" />
                            <path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6" />
                        </svg>
                        <span style={{ color: '#fff', fontWeight: 700, fontSize: 18 }}>Xác nhận đặt hàng</span>
                    </div>
                    <button onClick={onClose} style={{
                        background: 'rgba(255,255,255,0.2)', border: 'none', borderRadius: '50%',
                        width: 32, height: 32, color: '#fff', cursor: 'pointer', fontSize: 18,
                        display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'background 0.2s'
                    }} onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.3)'}
                        onMouseLeave={e => e.currentTarget.style.background = 'rgba(255,255,255,0.2)'}>✕</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div style={{ padding: '24px', maxHeight: '75vh', overflowY: 'auto' }}>

                        {/* Section: SẢN PHẨM ĐẶT MUA */}
                        <div style={{
                            background: '#fff8f6', border: '1px solid #fde8e2',
                            borderRadius: 10, padding: '16px 20px', marginBottom: 24
                        }}>
                            <div style={{
                                fontSize: 13, fontWeight: 700, color: '#ee4d2d',
                                marginBottom: 14, display: 'flex', alignItems: 'center', gap: 8,
                                textTransform: 'uppercase', letterSpacing: '0.5px'
                            }}>
                                📦 Sản phẩm đặt mua
                            </div>
                            <div style={{ maxHeight: '180px', overflowY: 'auto', marginBottom: 14 }}>
                                {cart.map(item => (
                                    <div key={item.id} style={{
                                        display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
                                        padding: '10px 0', borderBottom: '1px dashed #fde8e2'
                                    }}>
                                        <div style={{ flex: 1, paddingRight: 12 }}>
                                            <div style={{ fontSize: 14, fontWeight: 600, color: '#333', marginBottom: 2 }}>{item.title}</div>
                                            <div style={{ fontSize: 12, color: '#999' }}>x{item.quantity} × {fmtPrice(item.price)}</div>
                                        </div>
                                        <div style={{ fontWeight: 700, color: '#ee4d2d', fontSize: 15 }}>
                                            {fmtPrice(item.price * item.quantity)}
                                        </div>
                                    </div>
                                ))}
                            </div>
                            <div style={{
                                display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                                paddingTop: 14, borderTop: '2px solid #fde8e2'
                            }}>
                                <span style={{ fontWeight: 700, fontSize: 16, color: '#333' }}>Tổng thanh toán</span>
                                <span style={{ fontWeight: 800, fontSize: 22, color: '#ee4d2d' }}>{fmtPrice(total)}</span>
                            </div>
                        </div>

                        {/* Section: Thông tin giao hàng */}
                        <div style={{
                            fontSize: 14, fontWeight: 700, color: '#333',
                            marginBottom: 18, display: 'flex', alignItems: 'center', gap: 8
                        }}>
                            <span style={{ color: '#ee4d2d', fontSize: 18 }}>📍</span> Thông tin giao hàng
                        </div>

                        {[
                            { label: 'Tên người nhận', key: 'customerName', placeholder: 'Nhập tên người nhận', type: 'text', icon: '👤' },
                            { label: 'Số điện thoại', key: 'phone', placeholder: 'Nhập số điện thoại', type: 'tel', icon: '📱' },
                            { label: 'Địa chỉ giao hàng', key: 'address', placeholder: 'Nhập địa chỉ đầy đủ', type: 'text', icon: '🏠' },
                        ].map(field => (
                            <div key={field.key} style={{ marginBottom: 18 }}>
                                <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#555', marginBottom: 8 }}>
                                    {field.label} <span style={{ color: '#ee4d2d' }}>*</span>
                                </label>
                                <div style={{ position: 'relative' }}>
                                    <span style={{
                                        position: 'absolute', left: 16, top: '50%',
                                        transform: 'translateY(-50%)', fontSize: 18,
                                        pointerEvents: 'none', filter: 'grayscale(100%)', opacity: 0.7
                                    }}>{field.icon}</span>
                                    <input
                                        type={field.type}
                                        value={form[field.key]}
                                        onChange={e => setForm({ ...form, [field.key]: e.target.value })}
                                        placeholder={field.placeholder}
                                        style={{
                                            width: '100%', padding: '14px 16px 14px 48px',
                                            border: '1.5px solid #e5e7eb', borderRadius: 10,
                                            fontSize: 15, boxSizing: 'border-box', outline: 'none',
                                            transition: 'all 0.2s', background: '#f9fafb'
                                        }}
                                        onFocus={e => {
                                            e.target.style.borderColor = '#ee4d2d';
                                            e.target.style.background = '#fff';
                                            e.target.style.boxShadow = '0 0 0 4px rgba(238, 77, 45, 0.1)';
                                        }}
                                        onBlur={e => {
                                            e.target.style.borderColor = '#e5e7eb';
                                            e.target.style.background = '#f9fafb';
                                            e.target.style.boxShadow = 'none';
                                        }}
                                    />
                                </div>
                            </div>
                        ))}

                        {error && (
                            <div style={{
                                background: '#fef2f2', border: '1px solid #fca5a5', borderRadius: 10,
                                padding: '12px 16px', color: '#dc2626', fontSize: 14,
                                display: 'flex', alignItems: 'center', gap: 10, marginTop: 12
                            }}>
                                <span style={{ fontSize: 18 }}>⚠️</span> {error}
                            </div>
                        )}
                    </div>

                    {/* Footer */}
                    <div style={{
                        padding: '20px 24px', borderTop: '1px solid #f3f4f6',
                        display: 'flex', gap: 16, justifyContent: 'flex-end', background: '#fafafa'
                    }}>
                        <button type="button" onClick={onClose} style={{
                            padding: '12px 28px', border: '1.5px solid #e5e7eb', borderRadius: 8,
                            background: '#fff', color: '#555', cursor: 'pointer', fontSize: 15,
                            fontWeight: 600, transition: 'all 0.2s'
                        }} onMouseEnter={e => e.currentTarget.style.background = '#f3f4f6'}
                            onMouseLeave={e => e.currentTarget.style.background = '#fff'}>
                            Hủy
                        </button>
                        <button type="submit" disabled={loading} style={{
                            padding: '12px 36px', background: loading ? '#f9a58a' : '#ee4d2d',
                            color: '#fff', border: 'none', borderRadius: 8, fontWeight: 700,
                            fontSize: 16, cursor: loading ? 'not-allowed' : 'pointer',
                            display: 'flex', alignItems: 'center', gap: 10, transition: 'all 0.2s',
                            minWidth: 180, justifyContent: 'center', boxShadow: '0 4px 12px rgba(238, 77, 45, 0.2)'
                        }}
                            onMouseEnter={e => { if (!loading) e.currentTarget.style.background = '#d44226'; }}
                            onMouseLeave={e => { if (!loading) e.currentTarget.style.background = '#ee4d2d'; }}>
                            {loading
                                ? <><div style={{ width: 18, height: 18, border: '2px solid rgba(255,255,255,0.3)', borderTopColor: '#fff', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} /> Đang xử lý...</>
                                : <>🛒 Đặt hàng ngay</>
                            }
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

// Modal Profile (Xem và chỉnh sửa thông tin)
function ProfileModal({ username, onClose, mandatory = false, onSaveSuccess }) {
    const [form, setForm] = useState({ customerName: '', phone: '', address: '', email: '' });
    const [loading, setLoading] = useState(false);
    const [msg, setMsg] = useState({ type: '', text: '' });

    useEffect(() => {
        if (username) {
            setLoading(true);
            api.getDeliveryProfile(username)
                .then(data => {
                    if (data) {
                        setForm({
                            customerName: data.customerName || username,
                            phone: data.phone || '',
                            address: data.address || '',
                            email: data.email || ''
                        });
                    }
                })
                .catch(err => console.log('Lỗi lấy profile:', err))
                .finally(() => setLoading(false));
        }
    }, [username]);

    const handleSave = async (e) => {
        e.preventDefault();
        setLoading(true); setMsg({ type: '', text: '' });
        try {
            const res = await fetch('/dem_login-0.0.1-SNAPSHOT/api/orders/profile/update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username,
                    customerName: form.customerName,
                    phone: form.phone,
                    address: form.address,
                    email: form.email
                })
            });
            const data = await res.json();
            if (res.ok && data.success === 'true') {
                setMsg({ type: 'success', text: 'Cập nhật thông tin thành công!' });
                if (onSaveSuccess) onSaveSuccess(form.customerName);
                setTimeout(onClose, 1500);
            } else {
                setMsg({ type: 'error', text: data.message || 'Cập nhật thất bại' });
            }
        } catch { setMsg({ type: 'error', text: 'Lỗi kết nối server' }); }
        finally { setLoading(false); }
    };

    return (
        <div style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            zIndex: 9999, padding: 16, backdropFilter: 'blur(4px)'
        }} onClick={mandatory ? undefined : onClose}>
            <div style={{
                background: '#fff', borderRadius: 16, width: '100%', maxWidth: 440,
                boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)', overflow: 'hidden',
                animation: 'modalSlideIn 0.3s ease-out'
            }} onClick={e => e.stopPropagation()}>
                <div style={{ background: 'var(--accent)', padding: '20px 24px', color: '#fff', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>👤 Thông tin cá nhân</h3>
                    {!mandatory && (
                        <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#fff', fontSize: 20, cursor: 'pointer' }}>✕</button>
                    )}
                </div>

                {mandatory && (
                    <div style={{
                        background: '#fff7ed', borderLeft: '4px solid #f97316',
                        padding: '12px 20px', display: 'flex', alignItems: 'flex-start', gap: 10
                    }}>
                        <span style={{ fontSize: 20 }}>📋</span>
                        <div>
                            <div style={{ fontWeight: 700, color: '#c2410c', fontSize: 14 }}>Cập nhật thông tin bắt buộc</div>
                            <div style={{ fontSize: 13, color: '#9a3412', marginTop: 2 }}>
                                Bạn đăng nhập lần đầu bằng Google. Vui lòng điền đầy đủ thông tin để tiếp tục sử dụng.
                            </div>
                        </div>
                    </div>
                )}

                <form onSubmit={handleSave} style={{ padding: 24 }}>
                    {[
                        { label: 'Họ và tên', key: 'customerName', icon: '👤' },
                        { label: 'Số điện thoại', key: 'phone', icon: '📱' },
                        { label: 'Email liên hệ', key: 'email', icon: '✉️' },
                        { label: 'Địa chỉ giao hàng', key: 'address', icon: '🏠' },
                    ].map(f => (
                        <div key={f.key} style={{ marginBottom: 16 }}>
                            <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#555', marginBottom: 6 }}>{f.label}</label>
                            <div style={{ position: 'relative' }}>
                                <span style={{ position: 'absolute', left: 14, top: '50%', transform: 'translateY(-50%)', opacity: 0.6 }}>{f.icon}</span>
                                <input
                                    type="text"
                                    value={form[f.key]}
                                    onChange={e => setForm({ ...form, [f.key]: e.target.value })}
                                    style={{
                                        width: '100%', padding: '12px 14px 12px 42px',
                                        border: '1.5px solid #e5e7eb', borderRadius: 10,
                                        fontSize: 14, outline: 'none', transition: 'all 0.2s'
                                    }}
                                    onFocus={e => e.target.style.borderColor = 'var(--accent)'}
                                    onBlur={e => e.target.style.borderColor = '#e5e7eb'}
                                />
                            </div>
                        </div>
                    ))}

                    {msg.text && (
                        <div style={{
                            padding: 12, borderRadius: 8, marginBottom: 16, fontSize: 13, fontWeight: 600,
                            background: msg.type === 'success' ? '#ecfdf5' : '#fef2f2',
                            color: msg.type === 'success' ? '#10b981' : '#ef4444',
                            border: `1px solid ${msg.type === 'success' ? '#10b981' : '#ef4444'}`
                        }}>
                            {msg.type === 'success' ? '✅' : '⚠️'} {msg.text}
                        </div>
                    )}

                    <div style={{ display: 'flex', gap: 12, marginTop: 8 }}>
                        {!mandatory && (
                            <button type="button" onClick={onClose} style={{
                                flex: 1, padding: '12px', borderRadius: 10, border: '1.5px solid #e5e7eb',
                                background: '#fff', color: '#555', fontWeight: 600, cursor: 'pointer'
                            }}>Đóng</button>
                        )}
                        <button type="submit" disabled={loading} style={{
                            flex: mandatory ? 'auto' : 1, padding: '12px', borderRadius: 10, border: 'none',
                            background: 'var(--accent)', color: '#fff', fontWeight: 700, cursor: loading ? 'not-allowed' : 'pointer'
                        }}>
                            {loading ? 'Đang lưu...' : 'Lưu thay đổi'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

//  Main Cart Page 
export default function CartPage() {
    const { user: me, logout, updateDisplayName } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const isAdmin = me?.role === 'ADMIN';

    const [books, setBooks] = useState([]);
    const [cart, setCart] = useState(getCart());
    const [loadingBooks, setLoadingBooks] = useState(true);
    const [search, setSearch] = useState(location.state?.search || '');
    const [showCheckout, setShowCheckout] = useState(false);
    const [showProfile, setShowProfile] = useState(false);
    const [profileMandatory, setProfileMandatory] = useState(false);
    const [toast, setToast] = useState('');
    const [isSidebarOpen, setIsSidebarOpen] = useState(true);
    const [activeTab, setActiveTab] = useState(location.state?.tab || 'shop'); // 'shop' | 'cart'

    useEffect(() => {
        api.getBooks()
            .then(data => setBooks(data.filter(b => b.status === 'ACTIVE')))
            .catch(() => showToastMsg('Không thể tải danh sách sách'))
            .finally(() => setLoadingBooks(false));

        // Lắng nghe sự kiện cập nhật giỏ hàng từ quá trình đăng nhập/sync
        const handleCartUpdate = () => {
            try { setCart(JSON.parse(localStorage.getItem('cart') || '[]')); } catch { }
        };
        window.addEventListener('cartUpdated', handleCartUpdate);
        return () => window.removeEventListener('cartUpdated', handleCartUpdate);
    }, []);

    // Tự động mở modal thanh toán sau khi đăng nhập nếu trước đó đang dở dang
    useEffect(() => {
        if (me && localStorage.getItem('autoCheckout') === 'true') {
            localStorage.removeItem('autoCheckout');
            setActiveTab('cart');
            setShowCheckout(true);
        }
        // Bắt buộc cập nhật thông tin lần đầu (Google)
        if (me && localStorage.getItem('mustUpdateProfile') === 'true') {
            localStorage.removeItem('mustUpdateProfile');
            setProfileMandatory(true);
            setShowProfile(true);
        }
    }, [me]);

    useEffect(() => {
        if (location.state?.search != null) {
            setSearch(location.state.search);
            setActiveTab('shop');
        }
        if (location.state?.tab) {
            setActiveTab(location.state.tab);
        }
    }, [location.state]);

    // Tự động đồng bộ giỏ hàng lên server khi có thay đổi (nếu đã đăng nhập)
    useEffect(() => {
        if (me && !loadingBooks) {
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
    }, [cart, me, loadingBooks]);

    const showToastMsg = (msg) => {
        setToast(msg); setTimeout(() => setToast(''), 3000);
    };

    const fmtPrice = (p) => new Intl.NumberFormat('vi-VN').format(p) + ' đ';

    // THÊM VÀO GIỎ - Cho phép người dùng chưa đăng nhập
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

    //  THANH TOÁN - Kiểm tra đăng nhập
    const handleCheckout = () => {
        if (!me) {
            localStorage.setItem('redirectAfterLogin', '/cart');
            localStorage.setItem('autoCheckout', 'true');
            showToastMsg('Vui lòng đăng nhập để thanh toán!');
            setTimeout(() => navigate('/login'), 1500);
            return;
        }
        setShowCheckout(true);
    };

    //  XEM GIỎ HÀNG - Nếu chưa đăng nhập, giỏ hàng vẫn hiển thị nhưng không thanh toán được
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

    const clearCart = () => {
        setCart([]); saveCart([]);
    };

    const cartTotal = cart.reduce((s, i) => s + i.price * i.quantity, 0);
    const cartCount = cart.reduce((s, i) => s + i.quantity, 0);

    const filteredBooks = books.filter(b =>
        b.title?.toLowerCase().includes(search.toLowerCase()) ||
        b.author?.toLowerCase().includes(search.toLowerCase()) ||
        b.category?.toLowerCase().includes(search.toLowerCase())
    );

    const handleLogout = () => {
        logout();
        // Cập nhật state giỏ hàng rỗng để giao diện load lại ngay
        setCart([]);
        saveCart([]);
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
                    <Link className="nav-item active" to="/cart">🛒 Mua sách</Link>
                    <Link className="nav-item" to="/orders">📦 Đơn hàng của tôi</Link>
                </nav>
                <div className="sidebar-footer">
                    {me ? (
                        <>
                            <div className="sidebar-user">
                                <div className="avatar">{(me.displayName || me.username)?.[0]?.toUpperCase()}</div>
                                <div className="sidebar-user-info">
                                    <div className="sidebar-username">{me.displayName || me.username}</div>
                                    <div className="sidebar-role">{me.role}</div>
                                </div>
                            </div>
                            <div style={{ display: 'flex', gap: '8px' }}>
                                <button className="btn-logout" onClick={() => setShowProfile(true)} title="Thông tin cá nhân" style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}>
                                    <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none">
                                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" />
                                    </svg>
                                </button>
                                <button className="btn-logout" onClick={handleLogout} title="Đăng xuất" style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}>
                                    <svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" strokeWidth="2" fill="none">
                                        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><polyline points="16 17 21 12 16 7" /><line x1="21" y1="12" x2="9" y2="12" />
                                    </svg>
                                </button>
                            </div>
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
                                                    <a
                                                        href={`/dem_login-0.0.1-SNAPSHOT/book-detail?id=${book.id}`}
                                                        style={{ textDecoration: 'none', display: 'block' }}
                                                    >
                                                        <button
                                                            className="btn-add-cart"
                                                            style={{ width: '100%' }}
                                                        >
                                                            📖 Xem chi tiết
                                                        </button>
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
                                            onClick={handleCheckout}>
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

            {showProfile && (
                <ProfileModal
                    username={me?.username}
                    mandatory={profileMandatory}
                    onClose={() => {
                        setShowProfile(false);
                        setProfileMandatory(false);
                    }}
                    onSaveSuccess={(newName) => {
                        updateDisplayName(newName);
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
