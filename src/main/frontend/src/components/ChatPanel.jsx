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

/* ═══════════════════════════════════════════
   MARKDOWN RENDERER — Chuẩn Gemini Scannable
   ═══════════════════════════════════════════ */
function markdownToHtml(text) {
    if (!text) return '';

    // 1. Escape HTML — chống XSS
    let html = text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');

    // 2. Code block (```...```)
    html = html.replace(/```[\w]*\n?([\s\S]*?)```/g, (_, code) =>
        `<pre style="background:#1e1e2e;color:#cdd6f4;border-radius:8px;padding:12px 14px;` +
        `overflow-x:auto;font-size:12px;line-height:1.65;margin:8px 0;` +
        `font-family:Consolas,'Courier New',monospace;white-space:pre-wrap">${code.trim()}</pre>`
    );

    // 3. Inline code (`...`)
    html = html.replace(/`([^`\n]+)`/g,
        `<code style="background:#f1f0ff;color:#7c3aed;padding:2px 6px;` +
        `border-radius:4px;font-size:12.5px;font-family:monospace">$1</code>`
    );

    // 4. Bold (**text**)
    html = html.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>');

    // 5. Italic (*text*) — không chạm dấu * đầu dòng bullet
    html = html.replace(/(?<!\*)\*([^*\n]+)\*(?!\*)/g, '<em>$1</em>');

    // 6. Horizontal rule (---) — chuẩn Gemini
    html = html.replace(/^-{3,}\s*$/gm,
        '<hr style="border:none;border-top:1px solid #e5e7eb;margin:14px 0" />'
    );

    // 7. Blockquote (> text)
    html = html.replace(/^&gt; (.+)$/gm,
        `<div style="border-left:3px solid #7c3aed;padding:6px 12px;margin:8px 0;` +
        `color:#4b5563;background:#f9f8ff;border-radius:0 6px 6px 0;font-style:italic">$1</div>`
    );

    // 8. Headings: ##, ###
    html = html
        .replace(/^### (.+)$/gm,
            `<div style="font-size:13.5px;font-weight:700;color:#111827;margin:12px 0 4px">$1</div>`)
        .replace(/^## (.+)$/gm,
            `<div style="font-size:15px;font-weight:700;color:#111827;margin:16px 0 8px;` +
            `padding-bottom:4px;border-bottom:1px solid #f3f4f6">$1</div>`)
        .replace(/^# (.+)$/gm,
            `<div style="font-size:16.5px;font-weight:700;color:#111827;margin:16px 0 8px">$1</div>`);

    // 9. Numbered list (1. ...)
    html = html.replace(/^(\d+)\. (.+)$/gm,
        `<div style="display:flex;gap:8px;margin:6px 0;padding-left:2px">` +
        `<span style="color:#7c3aed;font-weight:700;min-width:20px;flex-shrink:0">$1.</span>` +
        `<span style="line-height:1.6">$2</span></div>`
    );

    // 10. Nested bullet (thụt ≥2 spaces)
    html = html.replace(/^ {2,}[•\-\*] (.+)$/gm,
        `<div style="display:flex;gap:6px;margin:3px 0;padding-left:22px;font-size:13px;color:#4b5563">` +
        `<span style="color:#a78bfa;flex-shrink:0">◦</span><span>$1</span></div>`
    );

    // 11. Bullet list (*, -, •)
    html = html.replace(/^[•\-\*] (.+)$/gm,
        `<div style="display:flex;gap:8px;margin:5px 0;padding-left:4px">` +
        `<span style="color:#7c3aed;font-size:14px;line-height:1.4;flex-shrink:0">•</span>` +
        `<span style="line-height:1.6">$1</span></div>`
    );

    // 12. Xuống dòng
    html = html
        .replace(/\n\n/g, `<div style="height:10px"></div>`)
        .replace(/\n/g, '<br>');

    return html;
}

/* ════════════════════
   TYPING DOTS
   ════════════════════ */
function TypingDots() {
    return (
        <div style={{ display: 'flex', gap: 4, alignItems: 'center', padding: '10px 0' }}>
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

/* ════════════════════
   TYPEWRITER EFFECT
   ════════════════════ */
function Typewriter({ text, speed = 5, onComplete }) {
    const [displayedText, setDisplayedText] = useState('');
    const [index, setIndex] = useState(0);

    useEffect(() => {
        if (index < text.length) {
            const timeout = setTimeout(() => {
                setDisplayedText(prev => prev + text[index]);
                setIndex(prev => prev + 1);
            }, speed);
            return () => clearTimeout(timeout);
        } else if (onComplete) {
            onComplete();
        }
    }, [index, text, speed, onComplete]);

    return <div dangerouslySetInnerHTML={{ __html: markdownToHtml(displayedText) }} />;
}

/* ════════════════════
   MESSAGE BUBBLE
   ════════════════════ */
function Bubble({ msg, onNavigate, isLast }) {
    const isUser = msg.role === 'user';
    const [isTypingComplete, setIsTypingComplete] = useState(!isLast || isUser);

    if (isUser) {
        return (
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 20 }}>
                <div style={{
                    maxWidth: '85%', background: '#f4f4f5', color: '#18181b',
                    borderRadius: '20px', padding: '10px 16px', fontSize: 14, lineHeight: 1.6,
                    whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                }}>
                    {msg.message}
                </div>
            </div>
        );
    }

    const { text, actions } = parseAIMessage(msg.message || '');

    return (
        <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: 24, alignItems: 'flex-start' }}>
            {/* Avatar AI */}
            <div style={{
                width: 32, height: 32, borderRadius: '50%',
                background: 'linear-gradient(135deg,#7c3aed,#2dd4bf)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                color: '#fff', flexShrink: 0, marginRight: 12,
                boxShadow: '0 2px 8px rgba(124,58,237,0.2)',
            }}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                </svg>
            </div>

            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 12 }}>
                <div style={{ color: '#18181b', fontSize: 14, lineHeight: 1.7, wordBreak: 'break-word' }}>
                    {isLast && !isTypingComplete ? (
                        <Typewriter text={text} onComplete={() => setIsTypingComplete(true)} />
                    ) : (
                        <div dangerouslySetInnerHTML={{ __html: markdownToHtml(text) }} />
                    )}
                </div>

                {isTypingComplete && actions.length > 0 && (
                    <div style={{ marginTop: 4, display: 'flex', flexDirection: 'column', gap: 8, animation: 'fadeIn 0.4s ease-out' }}>
                        <ActionButtons actions={actions} onAction={onNavigate} hint="" />
                    </div>
                )}
            </div>
        </div>
    );
}

/* ════════════════════════════════════
   CHAT PANEL — Main Export
   ════════════════════════════════════ */
export default function ChatPanel({ onClose, username }) {
    const navigate = useNavigate();
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [loading, setLoading] = useState(false);
    const [sessionId, setSessionId] = useState('');
    const [phase, setPhase] = useState('welcome');
    const [attachMenuOpen, setAttachMenuOpen] = useState(false);
    const [attachedPdf, setAttachedPdf] = useState(null);
    const [uploadingPdf, setUploadingPdf] = useState(false);
    const endRef = useRef(null);
    const inputRef = useRef(null);
    const fileInputRef = useRef(null);
    const attachMenuRef = useRef(null);

    useEffect(() => {
        const saved = localStorage.getItem(`cs_${username}`);
        if (saved) { setSessionId(saved); loadHistory(saved); }
    }, []);

    useEffect(() => {
        endRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, loading]);

    useEffect(() => {
        if (!attachMenuOpen) return;
        const close = (e) => {
            if (attachMenuRef.current && !attachMenuRef.current.contains(e.target))
                setAttachMenuOpen(false);
        };
        document.addEventListener('mousedown', close);
        return () => document.removeEventListener('mousedown', close);
    }, [attachMenuOpen]);

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
        if (!msg || loading || uploadingPdf) return;
        setInput('');
        if (inputRef.current) inputRef.current.style.height = 'auto';
        setPhase('chat');
        const userLabel = attachedPdf ? `📎 ${attachedPdf.name}\n\n${msg}` : msg;
        setMessages(prev => [...prev, { role: 'user', message: userLabel }]);
        setLoading(true);
        try {
            if (attachedPdf?.path) {
                const { ok, data } = await api.askPdf(username, msg, attachedPdf.path);
                if (ok && data?.success) {
                    if (data.sessionId) {
                        setSessionId(data.sessionId);
                        localStorage.setItem(`cs_${username}`, data.sessionId);
                    }
                    setMessages(prev => [...prev, { role: 'model', message: data.message || 'Không nhận được phản hồi.' }]);
                } else {
                    setMessages(prev => [...prev, { role: 'model', message: data?.message || 'Không đọc được PDF hoặc AI lỗi mạng.' }]);
                }
            } else {
                const { ok, data } = await api.sendChat({ username, sessionId: sessionId || '', message: msg });
                if (ok && data.success) {
                    if (!sessionId) {
                        setSessionId(data.sessionId);
                        localStorage.setItem(`cs_${username}`, data.sessionId);
                    }
                    setMessages(prev => [...prev, { role: 'model', message: data.message }]);
                } else {
                    setMessages(prev => [...prev, { role: 'model', message: data.message || 'Có lỗi xảy ra!' }]);
                }
            }
        } catch {
            setMessages(prev => [...prev, { role: 'model', message: 'Không thể thiết lập kết nối AI!' }]);
        } finally {
            setLoading(false);
            inputRef.current?.focus();
        }
    };

    const handlePdfFile = async (e) => {
        const file = e.target.files?.[0];
        e.target.value = '';
        if (!file) return;
        const isPdf = file.type === 'application/pdf' || file.type === 'application/x-pdf' || file.name.toLowerCase().endsWith('.pdf');
        if (!isPdf) {
            setPhase('chat');
            setMessages(prev => [...prev, { role: 'model', message: '⚠️ Hệ thống chỉ chấp nhận định dạng file **PDF**. Vui lòng kiểm tra lại.' }]);
            return;
        }
        setAttachMenuOpen(false);
        setUploadingPdf(true);
        setPhase('chat');
        try {
            const { ok, data } = await api.uploadPdf(file);
            if (ok && (data.success === 'true' || data.success === true) && data.filePath) {
                setAttachedPdf({ path: data.filePath, name: file.name });
                setMessages(prev => [...prev, {
                    role: 'model',
                    message: `📎 Nhận file **${file.name}** thành công.\n\nBạn cần mình tóm tắt, phân tích luận điểm nào trong tài liệu này không? Để lại tin nhắn phía dưới nhé!`,
                }]);
            } else {
                setMessages(prev => [...prev, { role: 'model', message: data?.message || 'Tải file PDF lên thất bại.' }]);
            }
        } catch {
            setMessages(prev => [...prev, { role: 'model', message: 'Không kết nối được server để xử lý tải file.' }]);
        } finally {
            setUploadingPdf(false);
            inputRef.current?.focus();
        }
    };

    const handleNew = () => {
        if (sessionId) {
            api.clearChatHistory(sessionId);
            localStorage.removeItem(`cs_${username}`);
        }
        setMessages([]); setSessionId(''); setPhase('welcome'); setInput('');
        setAttachedPdf(null); setAttachMenuOpen(false);
    };

    const addBookToCart = async (bookId) => {
        try {
            const book = await fetch(`/dem_login-0.0.1-SNAPSHOT/api/books/${bookId}`).then(r => r.json());
            if (!book?.id) return false;
            const cart = getCart();
            const existing = cart.find(i => i.id === book.id);
            const newCart = existing
                ? cart.map(i => i.id === book.id ? { ...i, quantity: Math.min(i.quantity + 1, book.quantity || 99) } : i)
                : [...cart, { id: book.id, title: book.title, author: book.author, price: Number(book.price), quantity: 1, imageUrl: book.imageUrl, maxStock: book.quantity }];
            saveCart(newCart);
            return true;
        } catch { return false; }
    };

    const handleNavigate = (action) => runChatAction(action, { navigate, addBookToCart });

    return (
        <div style={{
            position: 'fixed', top: 0, right: 0, bottom: 0, width: 480, background: '#fff',
            borderLeft: '1px solid #e5e7eb', display: 'flex', flexDirection: 'column', zIndex: 1000,
            boxShadow: '-10px 0 30px rgba(0,0,0,0.05)', animation: 'slideIn 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
        }}>
            <style>{`
                @keyframes tdB{0%,60%,100%{transform:translateY(0);opacity:.4}30%{transform:translateY(-5px);opacity:1}}
                @keyframes slideIn{from{transform:translateX(100%)}to{transform:translateX(0)}}
                @keyframes fadeIn{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:translateY(0)}}
                .chat-scroll::-webkit-scrollbar{width:5px}
                .chat-scroll::-webkit-scrollbar-track{background:transparent}
                .chat-scroll::-webkit-scrollbar-thumb{background:#e5e7eb;border-radius:10px}
                .chat-actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}
                .chat-action-btn{display:flex;align-items:center;gap:6px;padding:8px 16px;background:#fff;border:1px solid #e5e7eb;border-radius:20px;color:#3f3f46;font-size:13px;font-weight:500;cursor:pointer;transition:all 0.2s}
                .chat-action-btn:hover{background:#f5f3ff;border-color:#7c3aed;color:#7c3aed}
                .chat-action-btn svg{color:#7c3aed}
                .chat-attach-menu{position:absolute;left:0;bottom:calc(100% + 8px);background:#fff;border:1px solid #e5e7eb;border-radius:12px;box-shadow:0 8px 24px rgba(0,0,0,0.12);padding:6px;min-width:190px;z-index:20;animation:fadeIn 0.15s ease-out}
                .chat-attach-item{display:flex;align-items:center;gap:10px;width:100%;padding:10px 12px;border:none;background:transparent;border-radius:8px;font-size:14px;color:#3f3f46;cursor:pointer;font-family:inherit;text-align:left}
                .chat-attach-item:hover{background:#f5f3ff;color:#7c3aed}
            `}</style>

            {/* ── HEADER ── */}
            <div style={{ padding: '20px 24px', flexShrink: 0, background: '#fff', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'linear-gradient(135deg,#7c3aed,#2dd4bf)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }}>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                        </svg>
                    </div>
                    <span style={{ fontSize: 16, fontWeight: 600, color: '#18181b' }}>BookBot AI</span>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                    <button onClick={handleNew} title="Cuộc trò chuyện mới"
                        style={{ background: '#f4f4f5', border: 'none', cursor: 'pointer', color: '#3f3f46', padding: '8px 12px', borderRadius: 8, fontSize: 13, fontWeight: 500, display: 'flex', alignItems: 'center', gap: 6, transition: 'all .15s' }}
                        onMouseEnter={e => e.currentTarget.style.background = '#e4e4e7'}
                        onMouseLeave={e => e.currentTarget.style.background = '#f4f4f5'}>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M12 5v14M5 12h14" />
                        </svg>
                        Mới
                    </button>
                    <button onClick={onClose}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#a1a1aa', width: 32, height: 32, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 20 }}
                        onMouseEnter={e => e.currentTarget.style.background = '#f4f4f5'}
                        onMouseLeave={e => e.currentTarget.style.background = 'none'}>
                        ×
                    </button>
                </div>
            </div>

            {/* ── BODY ── */}
            <div className="chat-scroll" style={{ flex: 1, overflowY: 'auto', padding: '10px 24px 20px' }}>

                {/* WELCOME */}
                {phase === 'welcome' && (
                    <div style={{ marginTop: 40, animation: 'fadeIn 0.5s ease-out' }}>
                        <h1 style={{ fontSize: 32, fontWeight: 600, marginBottom: 8, background: 'linear-gradient(135deg, #7c3aed, #2dd4bf)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
                            Xin chào, {username}
                        </h1>
                        <p style={{ fontSize: 17, color: '#71717a', marginBottom: 40 }}>
                            Mình có thể đồng hành và hỗ trợ tìm kiếm nguồn tri thức nào cho bạn hôm nay?
                        </p>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                            {QUICK_ACTIONS_TOP.map((a, i) => (
                                <div key={i} onClick={() => send(a.label)}
                                    style={{ padding: '16px', borderRadius: 16, border: '1px solid #e5e7eb', background: '#fff', cursor: 'pointer', transition: 'all .2s' }}
                                    onMouseEnter={e => { e.currentTarget.style.borderColor = '#7c3aed'; e.currentTarget.style.background = '#f5f3ff'; }}
                                    onMouseLeave={e => { e.currentTarget.style.borderColor = '#e5e7eb'; e.currentTarget.style.background = '#fff'; }}>
                                    <div style={{ fontSize: 14, fontWeight: 500, color: '#18181b', marginBottom: 4 }}>{a.label}</div>
                                    <div style={{ fontSize: 12, color: '#71717a' }}>{a.sub}</div>
                                </div>
                            ))}
                            {FAQ.map((s, i) => (
                                <div key={i} onClick={() => send(s)}
                                    style={{ padding: '16px', borderRadius: 16, border: '1px solid #e5e7eb', background: '#fff', cursor: 'pointer', transition: 'all .2s' }}
                                    onMouseEnter={e => { e.currentTarget.style.borderColor = '#7c3aed'; e.currentTarget.style.background = '#f5f3ff'; }}
                                    onMouseLeave={e => { e.currentTarget.style.borderColor = '#e5e7eb'; e.currentTarget.style.background = '#fff'; }}>
                                    <div style={{ fontSize: 14, color: '#3f3f46' }}>{s}</div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}

                {/* CHAT */}
                {phase === 'chat' && (
                    <div style={{ display: 'flex', flexDirection: 'column' }}>
                        {messages.map((m, i) => (
                            <Bubble key={i} msg={m} onNavigate={handleNavigate} isLast={i === messages.length - 1} />
                        ))}
                        {loading && (
                            <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: 20 }}>
                                <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'linear-gradient(135deg,#7c3aed,#2dd4bf)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', flexShrink: 0, marginRight: 12 }}>
                                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                        <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                                    </svg>
                                </div>
                                <TypingDots />
                            </div>
                        )}
                        <div ref={endRef} />
                    </div>
                )}
            </div>

            {/* ── INPUT ── */}
            <div style={{ flexShrink: 0, padding: '16px 24px 28px', background: '#fff' }}>
                {/* PDF badge */}
                {attachedPdf && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10, padding: '8px 12px', background: '#f5f3ff', border: '1px solid #ddd6fe', borderRadius: 12, fontSize: 13, color: '#5b21b6' }}>
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                            <polyline points="14 2 14 8 20 8" />
                        </svg>
                        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{attachedPdf.name}</span>
                        <button type="button" onClick={() => setAttachedPdf(null)} style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: '#9ca3af', padding: 4, lineHeight: 0 }}>
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                            </svg>
                        </button>
                    </div>
                )}

                <input ref={fileInputRef} type="file" accept=".pdf,application/pdf" style={{ display: 'none' }} onChange={handlePdfFile} />

                {/* Input box */}
                <div style={{ position: 'relative', background: '#f0f2f5', borderRadius: '24px', padding: '4px 4px 4px 8px', display: 'flex', alignItems: 'flex-end', gap: 4, transition: 'all 0.2s', border: '1px solid transparent' }}
                    onFocusCapture={e => { e.currentTarget.style.background = '#fff'; e.currentTarget.style.borderColor = '#e5e7eb'; e.currentTarget.style.boxShadow = '0 4px 20px rgba(0,0,0,0.08)'; }}
                    onBlurCapture={e => { e.currentTarget.style.background = '#f0f2f5'; e.currentTarget.style.borderColor = 'transparent'; e.currentTarget.style.boxShadow = 'none'; }}>

                    {/* Attach button */}
                    <div ref={attachMenuRef} style={{ position: 'relative', flexShrink: 0 }}>
                        <button type="button" onClick={() => setAttachMenuOpen(v => !v)} disabled={loading || uploadingPdf}
                            style={{ width: 40, height: 40, borderRadius: '50%', border: 'none', background: attachMenuOpen ? '#ede9fe' : 'transparent', cursor: loading || uploadingPdf ? 'default' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 2 }}>
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#6b7280" strokeWidth="2">
                                <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
                            </svg>
                        </button>
                        {attachMenuOpen && (
                            <div className="chat-attach-menu">
                                <button type="button" className="chat-attach-item" onClick={() => fileInputRef.current?.click()}>
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#7c3aed" strokeWidth="2">
                                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                                        <polyline points="14 2 14 8 20 8" />
                                    </svg>
                                    Tải lên tài liệu PDF
                                </button>
                                <div style={{ fontSize: 11, color: '#9ca3af', padding: '4px 12px 6px' }}>Chỉ file PDF</div>
                            </div>
                        )}
                    </div>

                    {/* ★ Textarea — hỗ trợ multi-line (thay thế input text) */}
                    <textarea
                        ref={inputRef}
                        value={input}
                        onChange={e => {
                            setInput(e.target.value);
                            e.target.style.height = 'auto';
                            e.target.style.height = Math.min(e.target.scrollHeight, 200) + 'px';
                        }}
                        onKeyDown={e => {
                            if (e.key === 'Enter' && !e.shiftKey) {
                                e.preventDefault();
                                send();
                                e.target.style.height = 'auto';
                            }
                        }}
                        placeholder={attachedPdf ? 'Hỏi bất cứ điều gì về tài liệu này...' : 'Nhập câu hỏi của bạn...'}
                        disabled={loading || uploadingPdf}
                        rows={1}
                        style={{
                            flex: 1, border: 'none', outline: 'none', background: 'transparent',
                            fontSize: 14, color: '#1f2937', fontFamily: 'inherit',
                            padding: '12px 0', resize: 'none', maxHeight: '200px', lineHeight: '1.5',
                        }}
                    />

                    {/* Send button */}
                    <button type="button" onClick={() => { send(); if (inputRef.current) inputRef.current.style.height = 'auto'; }}
                        disabled={!input.trim() || loading || uploadingPdf}
                        style={{
                            width: 40, height: 40, borderRadius: '50%', border: 'none', flexShrink: 0,
                            background: input.trim() && !loading && !uploadingPdf ? '#7c3aed' : 'transparent',
                            cursor: input.trim() && !loading && !uploadingPdf ? 'pointer' : 'default',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            transition: 'all .2s', marginBottom: '2px',
                        }}>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"
                            stroke={input.trim() && !loading && !uploadingPdf ? '#fff' : '#9ca3af'} strokeWidth="2.5">
                            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
                        </svg>
                    </button>
                </div>

                <p style={{ margin: '10px 0 0', fontSize: 11, color: '#a1a1aa', textAlign: 'center' }}>
                    {uploadingPdf ? 'Đang tải PDF lên server...' : 'BookBot có thể mắc sai sót. Hãy kiểm tra những thông tin quan trọng.'}
                </p>
            </div>
        </div>
    );
}