import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import api from '../service/api';

const QUICK_ACTIONS_TOP = [
    { label: 'Tìm sách hay', sub: 'Gợi ý sách theo sở thích của bạn' },
];

const FAQ = [
    'Sách nào đang bán chạy nhất?',
    'Làm sao để đặt hàng?',
    'Có sách về lập trình không?',
];

/*  Typing dots  */
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

/*  Message bubble  */
function Bubble({ msg }) {
    const isUser = msg.role === 'user';
    return (
        <div style={{ display: 'flex', justifyContent: isUser ? 'flex-end' : 'flex-start', marginBottom: 14 }}>
            {!isUser && (
                <div style={{
                    width: 30, height: 30, borderRadius: '50%',
                    background: 'linear-gradient(135deg,#7c3aed,#2dd4bf)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    color: '#fff', fontSize: 12, flexShrink: 0, marginRight: 10, marginTop: 2,
                    boxShadow: '0 2px 8px rgba(124,58,237,0.2)'
                }}>✦</div>
            )}
            <div style={{
                maxWidth: '82%',
                background: isUser ? 'linear-gradient(135deg,#7c3aed,#5b21b6)' : '#fff',
                color: isUser ? '#fff' : '#18181b',
                borderRadius: isUser ? '18px 18px 4px 18px' : '18px 18px 18px 4px',
                padding: '10px 15px', fontSize: 14, lineHeight: 1.6,
                whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                boxShadow: isUser ? '0 4px 12px rgba(124,58,237,0.15)' : '0 2px 8px rgba(0,0,0,0.04)',
                border: isUser ? 'none' : '1px solid #f1f1f1'
            }}>
                {msg.message}
            </div>
        </div>
    );
}

/*  Chat Panel (side panel cố định bên phải)  */
function ChatPanel({ onClose, username }) {
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

    const rHover = (e, on) => {
        e.currentTarget.style.background = on ? '#f5f3ff' : '#fff';
        e.currentTarget.style.borderColor = on ? '#c4b5fd' : '#efefef';
    };

    return (
        /* ── side panel: fixed, bên phải, full height ── */
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
                {/* Top row: badge + icons */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                    <span style={{
                        display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, fontWeight: 700,
                        color: '#7c3aed', background: '#f5f3ff', padding: '4px 10px', borderRadius: 20
                    }}>
                        <span style={{ fontSize: 12 }}>✦</span> AI Assistant
                    </span>
                    <div style={{ display: 'flex', gap: 2 }}>
                        {/* new chat */}
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
                        {/* close */}
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

                {/* Welcome heading */}
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
                        {/* Quick actions block */}
                        <p style={{
                            fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
                            letterSpacing: '0.6px', color: '#a1a1aa', margin: '0 0 7px'
                        }}>
                            Gợi ý
                        </p>
                        {QUICK_ACTIONS_TOP.map((a, i) => (
                            <div key={i} onClick={() => send(a.label)}
                                style={{
                                    display: 'flex', alignItems: 'center', gap: 9, padding: '9px 10px',
                                    borderRadius: 8, border: '1px solid #efefef', background: '#fff',
                                    marginBottom: 5, cursor: 'pointer', transition: 'all .15s'
                                }}
                                onMouseEnter={e => rHover(e, true)} onMouseLeave={e => rHover(e, false)}>
                                {/* envelope */}
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

                        {/* Commonly asked questions */}
                        <p style={{
                            fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
                            letterSpacing: '0.6px', color: '#a1a1aa', margin: '14px 0 7px'
                        }}>
                            Câu hỏi thường gặp
                        </p>
                        {FAQ.map((s, i) => (
                            <div key={i} onClick={() => send(s)}
                                style={{
                                    display: 'flex', alignItems: 'center', gap: 8, padding: '9px 10px',
                                    borderRadius: 8, border: '1px solid #efefef', background: '#fff',
                                    marginBottom: 5, cursor: 'pointer', fontSize: 12, color: '#3f3f46', transition: 'all .15s'
                                }}
                                onMouseEnter={e => rHover(e, true)} onMouseLeave={e => rHover(e, false)}>
                                {/* circle-question */}
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
                        {messages.map((m, i) => <Bubble key={i} msg={m} />)}

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

                        {/* Suggestions */}
                        {!loading && messages.length > 0 && messages[messages.length - 1].role === 'model' && (
                            <div style={{ marginTop: 10 }}>
                                <p style={{
                                    fontSize: 11, fontWeight: 700, textTransform: 'uppercase',
                                    letterSpacing: '0.6px', color: '#a1a1aa', margin: '0 0 6px'
                                }}>
                                    Đề xuất
                                </p>
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

/* Main export: nút mở + panel */
export default function ChatWidget() {
    const { user: me } = useAuth();
    const [open, setOpen] = useState(false);

    // Đẩy nội dung trang sang trái khi panel mở
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

            {/* Nút mở/đóng — góc trên phải nằm trong topbar */}
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