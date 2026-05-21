import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';
import { parseAIMessage, ActionButtons, runChatAction } from '../utils/ChatMessageRenderer';

const getCart = () => {
    try { return JSON.parse(localStorage.getItem('cart') || '[]'); } catch { return []; }
};
const saveCart = (cart) => {
    localStorage.setItem('cart', JSON.stringify(cart));
    window.dispatchEvent(new Event('cartUpdated'));
};

const QUICK_ACTIONS_TOP = [
    { label: 'Tìm sách hay', sub: 'Gợi ý sách theo sở thích của bạn' },
];

const FAQ = [
    'Sách nào đang bán chạy nhất?',
    'Làm sao để đặt hàng?',
    'Có sách về lập trình không?',
];

/* ─── MARKDOWN RENDERER ─────────────────────────────────────────────────── */

/**
 * Chuyển chuỗi Markdown thành HTML an toàn.
 * Hỗ trợ: **bold**, *italic*, `inline-code`, ```block```,
 *         # Heading, ## H2, ### H3, > blockquote, - / • / 1. lists.
 */
function markdownToHtml(text) {
    if (!text) return '';

    // 1. Escape HTML entities trước (an toàn XSS)
    let html = text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');

    // 2. Code block  (```...```)
    html = html.replace(/```[\w]*\n?([\s\S]*?)```/g, (_, code) =>
        `<pre style="background:#1e1e2e;color:#cdd6f4;border-radius:8px;padding:12px 14px;` +
        `overflow-x:auto;font-size:12px;line-height:1.65;margin:8px 0;` +
        `font-family:Consolas,'Courier New',monospace;white-space:pre-wrap">${code.trim()}</pre>`
    );

    // 3. Inline code  (`...`)
    html = html.replace(/`([^`\n]+)`/g,
        `<code style="background:#f1f0ff;color:#7c3aed;padding:2px 6px;` +
        `border-radius:4px;font-size:12.5px;font-family:monospace">$1</code>`
    );

    // 4. Bold  (**text**)
    html = html.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>');

    // 5. Italic  (*text*)  – không đụng đến dấu * đứng đầu dòng (bullet)
    html = html.replace(/(?<!\*)\*([^*\n]+)\*(?!\*)/g, '<em>$1</em>');

    // 6. Blockquote  (> text)  – &gt; vì đã escape
    html = html.replace(/^&gt; (.+)$/gm,
        `<div style="border-left:3px solid #7c3aed;padding:4px 10px;margin:4px 0;` +
        `color:#555;background:#f9f8ff;border-radius:0 6px 6px 0">$1</div>`
    );

    // 7. Headings
    html = html
        .replace(/^### (.+)$/gm,
            `<div style="font-size:13px;font-weight:700;color:#18181b;margin:10px 0 3px">$1</div>`)
        .replace(/^## (.+)$/gm,
            `<div style="font-size:14px;font-weight:700;color:#18181b;margin:10px 0 4px">$1</div>`)
        .replace(/^# (.+)$/gm,
            `<div style="font-size:15px;font-weight:700;color:#18181b;margin:10px 0 5px">$1</div>`);

    // 8. Bullet list  (•  /  -  /  *)
    html = html.replace(/^[•\-\*] (.+)$/gm,
        `<div style="display:flex;gap:6px;margin:3px 0;padding-left:4px">` +
        `<span style="color:#7c3aed;font-size:16px;line-height:1.3;flex-shrink:0;margin-top:-1px">•</span>` +
        `<span>$1</span></div>`
    );

    // 9. Numbered list  (1. ...)
    html = html.replace(/^(\d+)\. (.+)$/gm,
        `<div style="display:flex;gap:8px;margin:3px 0;padding-left:4px">` +
        `<span style="color:#7c3aed;font-weight:700;min-width:18px;flex-shrink:0">$1.</span>` +
        `<span>$2</span></div>`
    );

    // 10. Xuống dòng
    html = html
        .replace(/\n\n/g, `<div style="height:8px"></div>`)
        .replace(/\n/g, '<br>');

    return html;
}

/* ─── TYPING DOTS ────────────────────────────────────────────────────────── */

function TypingDots() {
    return (
        <div style={{ display: 'flex', gap: 4, alignItems: 'center', padding: '2px 0' }}>
            {[0, 1, 2].map(i => (
                <span key={i} style={{
                    width: 6, height: 6, borderRadius: '50%', background: '#7c3aed',
                    display: 'inline-block',
                    animation: 'tdB 1.1s ease-in-out infinite',
                    animationDelay: `${i * 0.18}s`,
                }} />
            ))}
        </div>
    );
}

/* ─── MESSAGE BUBBLE ─────────────────────────────────────────────────────── */

/**
 * Bubble hiển thị tin nhắn.
 * – User: bubble tím đơn giản.
 * – AI:   render Markdown + bắt ActionTrigger → nút điều hướng.
 *
 * onNavigate(target, id) — được truyền từ ChatPanel.
 */
function Bubble({ msg, onNavigate }) {
    const isUser = msg.role === 'user';

    /* ── User bubble ── */
    if (isUser) {
        return (
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 14 }}>
                <div style={{
                    maxWidth: '82%',
                    background: 'linear-gradient(135deg,#7c3aed,#5b21b6)',
                    color: '#fff',
                    borderRadius: '18px 18px 4px 18px',
                    padding: '10px 15px', fontSize: 14, lineHeight: 1.6,
                    whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                    boxShadow: '0 4px 12px rgba(124,58,237,0.15)',
                }}>
                    {msg.message}
                </div>
            </div>
        );
    }

    const { text, actions } = parseAIMessage(msg.message || '');

    return (
        <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: 14 }}>
            <div style={{
                width: 30, height: 30, borderRadius: '50%',
                background: 'linear-gradient(135deg,#7c3aed,#2dd4bf)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: '#fff', fontSize: 12, flexShrink: 0, marginRight: 10, marginTop: 2,
                boxShadow: '0 2px 8px rgba(124,58,237,0.2)',
            }}>✦</div>

            <div style={{ maxWidth: '82%', display: 'flex', flexDirection: 'column', gap: 0 }}>
                {text ? (
                    <div
                        style={{
                            background: '#fff', color: '#18181b',
                            borderRadius: '18px 18px 18px 4px',
                            padding: '10px 15px', fontSize: 14, lineHeight: 1.6,
                            wordBreak: 'break-word',
                            boxShadow: '0 2px 8px rgba(0,0,0,0.04)',
                            border: '1px solid #f1f1f1',
                        }}
                        dangerouslySetInnerHTML={{ __html: markdownToHtml(text) }}
                    />
                ) : null}
                <ActionButtons actions={actions} onAction={onNavigate} />
            </div>
        </div>
    );
}

/* ─── CHAT PANEL ─────────────────────────────────────────────────────────── */

function ChatPanel({ onClose, username }) {
    const navigate = useNavigate();
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [loading, setLoading] = useState(false);
    const [sessionId, setSessionId] = useState('');
    const [phase, setPhase] = useState('welcome');
    const endRef = useRef(null);
    const inputRef = useRef(null);

    useEffect(() => {
        const saved = localStorage.getItem(`cs_${username}`);
        if (saved) { setSessionId(saved); loadHistory(saved); }
    }, []);

    useEffect(() => {
        endRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, loading]);

    const loadHistory = async (sid) => {
        try {
            const data = await api.getChatHistory(sid);
            if (data?.length > 0) { setMessages(data); setPhase('chat'); }
        } catch (err) {
            if (err.message === '401' || err.message === '403') {
                localStorage.removeItem(`cs_${username}`);
                setSessionId('');
            }
        }
    };

    const send = async (text) => {
        const msg = (text || input).trim();
        if (!msg || loading) return;
        setInput(''); setPhase('chat');
        setMessages(prev => [...prev, { role: 'user', message: msg }]);
        setLoading(true);
        try {
            const { ok, data } = await api.sendChat({
                username, sessionId: sessionId || '', message: msg,
            });
            if (ok && data.success) {
                if (!sessionId) {
                    setSessionId(data.sessionId);
                    localStorage.setItem(`cs_${username}`, data.sessionId);
                }
                setMessages(prev => [...prev, { role: 'model', message: data.message }]);
            } else {
                setMessages(prev => [...prev, { role: 'model', message: data.message || 'Có lỗi xảy ra!' }]);
            }
        } catch {
            setMessages(prev => [...prev, { role: 'model', message: 'Không thể kết nối AI!' }]);
        } finally {
            setLoading(false);
            inputRef.current?.focus();
        }
    };

    const handleNew = () => {
        if (sessionId) {
            api.clearChatHistory(sessionId);
            localStorage.removeItem(`cs_${username}`);
        }
        setMessages([]); setSessionId(''); setPhase('welcome'); setInput('');
    };

    const addBookToCart = async (bookId) => {
        try {
            const book = await fetch(`/dem_login-0.0.1-SNAPSHOT/api/books/${bookId}`).then(r => r.json());
            if (!book?.id) return false;
            const cart = getCart();
            const existing = cart.find(i => i.id === book.id);
            let newCart;
            if (existing) {
                newCart = cart.map(i =>
                    i.id === book.id ? { ...i, quantity: Math.min(i.quantity + 1, book.quantity || 99) } : i
                );
            } else {
                newCart = [...cart, {
                    id: book.id,
                    title: book.title,
                    author: book.author,
                    price: Number(book.price),
                    quantity: 1,
                    imageUrl: book.imageUrl,
                    maxStock: book.quantity,
                }];
            }
            saveCart(newCart);
            return true;
        } catch {
            return false;
        }
    };

    const handleNavigate = (action) =>
        runChatAction(action, { navigate, addBookToCart });

    const rHover = (e, on) => {
        e.currentTarget.style.background = on ? '#f5f3ff' : '#fff';
        e.currentTarget.style.borderColor = on ? '#c4b5fd' : '#efefef';
    };

    return (
        <div style={{
            position: 'fixed', top: 0, right: 0, bottom: 0,
            width: 380,
            background: '#fbfbff',
            borderLeft: '1px solid #e5e7eb',
            display: 'flex', flexDirection: 'column',
            zIndex: 1000,
            boxShadow: '-10px 0 30px rgba(0,0,0,0.05)',
            animation: 'slideIn 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
        }}>
            <style>{`
                @keyframes tdB{0%,60%,100%{transform:translateY(0);opacity:.4}30%{transform:translateY(-5px);opacity:1}}
                @keyframes slideIn{from{transform:translateX(100%)}to{transform:translateX(0)}}
            `}</style>

            {/* ── HEADER ── */}
            <div style={{ padding: '18px 20px 14px', flexShrink: 0, background: '#fff' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                    <span style={{
                        display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700,
                        color: '#7c3aed', background: '#f5f3ff', padding: '4px 10px', borderRadius: 20
                    }}>
                        <span style={{ fontSize: 12 }}>✦</span> AI Assistant
                    </span>
                    <div style={{ display: 'flex', gap: 2 }}>
                        <button onClick={handleNew} title="Cuộc trò chuyện mới"
                            style={{
                                background: 'none', border: 'none', cursor: 'pointer', color: '#a1a1aa',
                                width: 26, height: 26, borderRadius: 6,
                                display: 'flex', alignItems: 'center', justifyContent: 'center', transition: 'color .15s'
                            }}
                            onMouseEnter={e => e.currentTarget.style.color = '#7c3aed'}
                            onMouseLeave={e => e.currentTarget.style.color = '#a1a1aa'}>
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                            </svg>
                        </button>
                        <button onClick={onClose} title="Đóng"
                            style={{
                                background: 'none', border: 'none', cursor: 'pointer', color: '#a1a1aa',
                                width: 26, height: 26, borderRadius: 6,
                                display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, transition: 'color .15s'
                            }}
                            onMouseEnter={e => e.currentTarget.style.color = '#18181b'}
                            onMouseLeave={e => e.currentTarget.style.color = '#a1a1aa'}>
                            ×
                        </button>
                    </div>
                </div>

                {phase === 'welcome' && (
                    <div style={{ marginBottom: 14 }}>
                        <h2 style={{ fontSize: 18, fontWeight: 700, color: '#18181b', margin: '0 0 2px' }}>
                            Xin chào, {username}
                        </h2>
                        <p style={{ fontSize: 12, color: '#71717a', margin: 0 }}>
                            Tôi có thể giúp gì cho bạn?
                        </p>
                    </div>
                )}

                <div style={{ height: '0.5px', background: '#efefef', margin: '0 -16px' }} />
            </div>

            {/* ── BODY ── */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '12px 16px' }}>

                {/* WELCOME */}
                {phase === 'welcome' && (
                    <>
                        <p style={{
                            fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
                            letterSpacing: '0.6px', color: '#a1a1aa', margin: '0 0 7px'
                        }}>Gợi ý</p>
                        {QUICK_ACTIONS_TOP.map((a, i) => (
                            <div key={i} onClick={() => send(a.label)}
                                style={{
                                    display: 'flex', alignItems: 'center', gap: 9, padding: '9px 10px',
                                    borderRadius: 8, border: '1px solid #efefef', background: '#fff',
                                    marginBottom: 5, cursor: 'pointer', transition: 'all .15s'
                                }}
                                onMouseEnter={e => rHover(e, true)} onMouseLeave={e => rHover(e, false)}>
                                <div style={{
                                    width: 30, height: 30, borderRadius: 7, background: '#f4f4f5',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
                                }}>
                                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#71717a" strokeWidth="1.8">
                                        <rect x="2" y="4" width="20" height="16" rx="2" />
                                        <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
                                    </svg>
                                </div>
                                <div>
                                    <div style={{ fontSize: 12, fontWeight: 600, color: '#18181b', marginBottom: 1 }}>{a.label}</div>
                                    <div style={{ fontSize: 11, color: '#a1a1aa' }}>{a.sub}</div>
                                </div>
                            </div>
                        ))}

                        <p style={{
                            fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
                            letterSpacing: '0.6px', color: '#a1a1aa', margin: '14px 0 7px'
                        }}>Câu hỏi thường gặp</p>
                        {FAQ.map((s, i) => (
                            <div key={i} onClick={() => send(s)}
                                style={{
                                    display: 'flex', alignItems: 'center', gap: 8, padding: '9px 10px',
                                    borderRadius: 8, border: '1px solid #efefef', background: '#fff',
                                    marginBottom: 5, cursor: 'pointer', fontSize: 12, color: '#3f3f46', transition: 'all .15s'
                                }}
                                onMouseEnter={e => rHover(e, true)} onMouseLeave={e => rHover(e, false)}>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#a1a1aa" strokeWidth="2" style={{ flexShrink: 0 }}>
                                    <circle cx="12" cy="12" r="10" />
                                    <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
                                    <line x1="12" y1="17" x2="12.01" y2="17" />
                                </svg>
                                {s}
                            </div>
                        ))}
                    </>
                )}

                {/* CHAT */}
                {phase === 'chat' && (
                    <>
                        {messages.map((m, i) => (
                            <Bubble key={i} msg={m} onNavigate={handleNavigate} />
                        ))}

                        {loading && (
                            <div style={{ display: 'flex', marginBottom: 10 }}>
                                <div style={{
                                    width: 26, height: 26, borderRadius: '50%',
                                    background: 'linear-gradient(135deg,#7c3aed,#5b21b6)',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    color: '#fff', fontSize: 11, flexShrink: 0, marginRight: 7
                                }}>✦</div>
                                <div style={{ background: '#f4f4f5', borderRadius: '14px 14px 14px 3px', padding: '9px 13px' }}>
                                    <TypingDots />
                                </div>
                            </div>
                        )}

                        {!loading && messages.length > 0 && messages[messages.length - 1].role === 'model' && (
                            <div style={{ marginTop: 10 }}>
                                <p style={{
                                    fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
                                    letterSpacing: '0.6px', color: '#a1a1aa', margin: '0 0 6px'
                                }}>Đề xuất</p>
                                {FAQ.map((s, i) => (
                                    <div key={i} onClick={() => send(s)}
                                        style={{
                                            display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px',
                                            borderRadius: 8, border: '1px solid #efefef', background: '#fff',
                                            marginBottom: 4, cursor: 'pointer', fontSize: 12, color: '#3f3f46', transition: 'all .15s'
                                        }}
                                        onMouseEnter={e => rHover(e, true)} onMouseLeave={e => rHover(e, false)}>
                                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#a1a1aa" strokeWidth="2" style={{ flexShrink: 0 }}>
                                            <circle cx="12" cy="12" r="10" />
                                            <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
                                            <line x1="12" y1="17" x2="12.01" y2="17" />
                                        </svg>
                                        {s}
                                    </div>
                                ))}
                            </div>
                        )}
                        <div ref={endRef} />
                    </>
                )}
            </div>

            {/* ── INPUT ── */}
            <div style={{ flexShrink: 0, padding: '16px 20px 12px', background: '#fff', borderTop: '1px solid #f3f4f6' }}>
                <div style={{
                    display: 'flex', alignItems: 'center', gap: 10,
                    border: '1.5px solid #e5e7eb', borderRadius: 14, padding: '8px 8px 8px 16px',
                    background: '#fff', transition: 'all 0.2s',
                    boxShadow: '0 2px 6px rgba(0,0,0,0.02)'
                }}
                    onFocusCapture={e => {
                        e.currentTarget.style.borderColor = '#7c3aed';
                        e.currentTarget.style.boxShadow = '0 0 0 4px rgba(124,58,237,0.1)';
                    }}
                    onBlurCapture={e => {
                        e.currentTarget.style.borderColor = '#e5e7eb';
                        e.currentTarget.style.boxShadow = '0 2px 6px rgba(0,0,0,0.02)';
                    }}>
                    <input ref={inputRef} value={input}
                        onChange={e => setInput(e.target.value)}
                        onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } }}
                        placeholder="Ask me anything..."
                        style={{
                            flex: 1, border: 'none', outline: 'none', background: 'transparent',
                            fontSize: 14, color: '#1f2937', fontFamily: 'inherit'
                        }} />
                    <button onClick={() => send()} disabled={!input.trim() || loading}
                        style={{
                            width: 36, height: 36, borderRadius: 10, border: 'none', flexShrink: 0,
                            background: input.trim() && !loading ? '#7c3aed' : '#f3f4f6',
                            cursor: input.trim() && !loading ? 'pointer' : 'not-allowed',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            transition: 'all .2s',
                        }}>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                            stroke={input.trim() && !loading ? '#fff' : '#9ca3af'} strokeWidth="2.5">
                            <line x1="22" y1="2" x2="11" y2="13" />
                            <polygon points="22 2 15 22 11 13 2 9 22 2" />
                        </svg>
                    </button>
                </div>
                <p style={{ margin: '5px 0 0', fontSize: 10, color: '#a1a1aa', textAlign: 'center' }}>
                    Không phải lời khuyên pháp lý.
                </p>
            </div>
        </div>
    );
}

/* ─── MAIN EXPORT ────────────────────────────────────────────────────────── */

export default function ChatWidget() {
    const { user: me } = useAuth();
    const [open, setOpen] = useState(false);

    useEffect(() => {
        const main = document.querySelector('.main-content');
        if (main) {
            main.style.transition = 'margin-right 0.25s cubic-bezier(0.16, 1, 0.3, 1)';
            main.style.marginRight = open ? '380px' : '0';
        }
        return () => {
            const m = document.querySelector('.main-content');
            if (m) m.style.marginRight = '0';
        };
    }, [open]);

    return (
        <>
            {open && (
                <ChatPanel
                    onClose={() => setOpen(false)}
                    username={me?.username || 'Bạn'}
                />
            )}

            {!open && (
                <button onClick={() => setOpen(true)}
                    style={{
                        position: 'fixed', bottom: 24, right: 24, zIndex: 1001,
                        width: 56, height: 56, borderRadius: '50%',
                        background: 'linear-gradient(135deg,#7c3aed,#2dd4bf)',
                        border: 'none', cursor: 'pointer',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        boxShadow: '0 4px 20px rgba(124,58,237,.35)',
                        transition: 'all .25s cubic-bezier(0.34, 1.56, 0.64, 1)',
                    }}
                    onMouseEnter={e => {
                        e.currentTarget.style.transform = 'scale(1.1) rotate(5deg)';
                        e.currentTarget.style.boxShadow = '0 8px 25px rgba(124,58,237,.45)';
                    }}
                    onMouseLeave={e => {
                        e.currentTarget.style.transform = 'scale(1) rotate(0deg)';
                        e.currentTarget.style.boxShadow = '0 4px 20px rgba(124,58,237,.35)';
                    }}>
                    <svg width="28" height="28" viewBox="0 0 24 24" fill="#fff">
                        <path d="M12 1L14.8 9.2L23 12L14.8 14.8L12 23L9.2 14.8L1 12L9.2 9.2L12 1Z" />
                        <circle cx="4.5" cy="4.5" r="1.5" />
                    </svg>
                </button>
            )}
        </>
    );
}