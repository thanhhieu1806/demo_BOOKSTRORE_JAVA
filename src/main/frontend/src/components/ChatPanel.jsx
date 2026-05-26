import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import api from '../service/api';
import { parseAIMessage, ActionButtons, runChatAction } from '../utils/ChatMessageRenderer';

const getCart = () => {
    try { return JSON.parse(localStorage.getItem('cart') || '[]'); } catch { return []; }
};
const saveCart = (cart) => {
    localStorage.setItem('cart', JSON.stringify(cart));
    window.dispatchEvent(new Event('cartUpdated'));
};

const QUICK_ACTIONS = [
    { label: 'Tìm sách hay', sub: 'Gợi ý theo sở thích' },
    { label: 'Sách bán chạy nhất?', sub: null },
    { label: 'Làm sao để đặt hàng?', sub: null },
    { label: 'Có sách lập trình không?', sub: null },
];

/* ─────────────────────────────────────────
   MARKDOWN RENDERER — Gemini AI Style
───────────────────────────────────────── */
function MarkdownRenderer({ content, className = '' }) {
    if (!content) return null;

    return (
        <div className={`gemini-markdown ${className}`}>
            <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                rehypePlugins={[rehypeRaw, rehypeSanitize]}
                components={{
                    // Custom styling for headings
                    h1: ({ node, ...props }) => <h1 className="gemini-h1" {...props} />,
                    h2: ({ node, ...props }) => <h2 className="gemini-h2" {...props} />,
                    h3: ({ node, ...props }) => <h3 className="gemini-h3" {...props} />,
                    // Custom styling for code blocks
                    code: ({ node, inline, className, children, ...props }) => {
                        const match = /language-(\w+)/.exec(className || '');
                        return inline ? (
                            <code className="gemini-inline-code" {...props}>
                                {children}
                            </code>
                        ) : (
                            <pre className="gemini-code-block">
                                <code className={match ? `language-${match[1]}` : ''} {...props}>
                                    {children}
                                </code>
                            </pre>
                        );
                    },
                    // Custom styling for blockquotes
                    blockquote: ({ node, ...props }) => (
                        <blockquote className="gemini-blockquote" {...props} />
                    ),
                    // Custom styling for lists
                    ul: ({ node, ...props }) => <ul className="gemini-ul" {...props} />,
                    ol: ({ node, ...props }) => <ol className="gemini-ol" {...props} />,
                    li: ({ node, ...props }) => <li className="gemini-li" {...props} />,
                    // Custom styling for horizontal rule
                    hr: ({ node, ...props }) => <hr className="gemini-hr" {...props} />,
                    // Custom styling for links
                    a: ({ node, ...props }) => <a className="gemini-link" target="_blank" rel="noopener noreferrer" {...props} />,
                    // Custom styling for tables
                    table: ({ node, ...props }) => <table className="gemini-table" {...props} />,
                    thead: ({ node, ...props }) => <thead className="gemini-thead" {...props} />,
                    tbody: ({ node, ...props }) => <tbody className="gemini-tbody" {...props} />,
                    tr: ({ node, ...props }) => <tr className="gemini-tr" {...props} />,
                    th: ({ node, ...props }) => <th className="gemini-th" {...props} />,
                    td: ({ node, ...props }) => <td className="gemini-td" {...props} />,
                }}
            >
                {content}
            </ReactMarkdown>
        </div>
    );
}

/* ─────────────────────────────────────────
   TYPING DOTS
───────────────────────────────────────── */
function TypingDots() {
    return (
        <div className="bb-typing">
            {[0, 1, 2].map(i => <span key={i} className="bb-dot-anim" style={{ animationDelay: `${i * 0.16}s` }} />)}
        </div>
    );
}

/* ─────────────────────────────────────────
   TYPEWRITER
───────────────────────────────────────── */
function Typewriter({ text, speed = 6, onComplete }) {
    const [displayed, setDisplayed] = useState('');
    const [idx, setIdx] = useState(0);
    useEffect(() => {
        if (idx < text.length) {
            const t = setTimeout(() => { setDisplayed(p => p + text[idx]); setIdx(p => p + 1); }, speed);
            return () => clearTimeout(t);
        } else if (onComplete) onComplete();
    }, [idx, text, speed, onComplete]);

    return idx >= text.length
        ? <MarkdownRenderer content={displayed} />
        : <span style={{ whiteSpace: 'pre-wrap', lineHeight: 1.7, fontSize: 13.5 }}>{displayed}</span>;
}

/* ─────────────────────────────────────────
   MESSAGE BUBBLE
───────────────────────────────────────── */
function Bubble({ msg, onNavigate, isLast }) {
    const isUser = msg.role === 'user';
    const [done, setDone] = useState(!isLast || isUser);
    const { text, actions } = isUser ? { text: msg.message, actions: [] } : parseAIMessage(msg.message || '');

    if (isUser) return (
        <div className="bb-row bb-row-user">
            <div className="bb-bubble-user">{msg.message}</div>
        </div>
    );

    return (
        <div className="bb-row bb-row-ai">
            <div className="bb-bubble-ai-wrap">
                <div className="bb-bubble-ai">
                    {isLast && !done
                        ? <Typewriter text={text} onComplete={() => setDone(true)} />
                        : <MarkdownRenderer content={text} />}
                </div>
                {done && (
                    <div className="bb-feedback-block">
                        <span>Câu trả lời này có hữu ích không?</span>
                        <button className="bb-feedback-btn" title="Có" type="button">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
                            </svg>
                        </button>
                        <button className="bb-feedback-btn" title="Không" type="button">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3zm12-5h3a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-3" />
                            </svg>
                        </button>
                    </div>
                )}
                {isLast && done && (
                    <div className="bb-suggestions-section">
                        <div className="bb-suggestions-title">
                            Gợi ý
                        </div>
                        {actions && actions.length > 0 ? (
                            <div className="bb-actions-wrap" style={{ marginTop: '0' }}>
                                <ActionButtons actions={actions} onAction={onNavigate} hint="" />
                            </div>
                        ) : (
                            <div className="bb-suggestions-list">
                                <button className="bb-qa-card-large" onClick={() => onNavigate({ action: 'send', target: 'Tìm kiếm sách' })} type="button">
                                    <div className="bb-qa-icon-wrap" style={{ background: '#ede9fe', color: '#5b21b6' }}>
                                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                                            <polyline points="14 2 14 8 20 8" />
                                        </svg>
                                    </div>
                                    <div className="bb-qa-content">
                                        <div className="bb-qa-card-large-title">Tìm kiếm sách</div>
                                        <div className="bb-qa-card-large-sub">Tìm sách theo tên, tác giả hoặc thể loại</div>
                                    </div>
                                </button>
                                <button className="bb-qa-card-small" onClick={() => onNavigate({ action: 'send', target: 'Làm sao để mua sách?' })} type="button">
                                    <div className="bb-qa-card-small-icon">
                                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                            <circle cx="12" cy="12" r="10" /><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" /><line x1="12" y1="17" x2="12.01" y2="17" />
                                        </svg>
                                    </div>
                                    <div className="bb-qa-card-small-title">Làm sao để mua sách?</div>
                                </button>
                                <button className="bb-qa-card-small" onClick={() => onNavigate({ action: 'send', target: 'Cách theo dõi đơn hàng?' })} type="button">
                                    <div className="bb-qa-card-small-icon">
                                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                            <circle cx="12" cy="12" r="10" /><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" /><line x1="12" y1="17" x2="12.01" y2="17" />
                                        </svg>
                                    </div>
                                    <div className="bb-qa-card-small-title">Cách theo dõi đơn hàng?</div>
                                </button>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

/* ─────────────────────────────────────────
   CHAT PANEL — Floating window (không che content)
───────────────────────────────────────── */
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

    useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages, loading]);

    useEffect(() => {
        if (!attachMenuOpen) return;
        const close = (e) => {
            if (attachMenuRef.current && !attachMenuRef.current.contains(e.target)) setAttachMenuOpen(false);
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
        const label = attachedPdf ? `📎 ${attachedPdf.name}\n\n${msg}` : msg;
        setMessages(prev => [...prev, { role: 'user', message: label }]);
        setLoading(true);
        try {
            if (attachedPdf?.path) {
                const { ok, data } = await api.askPdf(username, msg, attachedPdf.path);
                if (ok && data?.success) {
                    if (data.sessionId) { setSessionId(data.sessionId); localStorage.setItem(`cs_${username}`, data.sessionId); }
                    setMessages(prev => [...prev, { role: 'model', message: data.message || 'Không nhận được phản hồi.' }]);
                } else {
                    setMessages(prev => [...prev, { role: 'model', message: data?.message || 'Không đọc được PDF.' }]);
                }
            } else {
                let aiText = '';
                try {
                    const response = await api.streamChat({ username, sessionId: sessionId || '', message: msg });
                    if (!response.ok) throw new Error("Stream API returned error");

                    setMessages(prev => [...prev, { role: 'model', message: '' }]);
                    const contentType = response.headers.get("content-type") || "";

                    if (contentType.includes("application/json")) {
                        const data = await response.json();
                        setLoading(false);
                        if (data.error) aiText = data.error;
                        else if (data.message) aiText = data.message;
                        else if (data.chunk) aiText = data.chunk;
                        else aiText = JSON.stringify(data);

                        if (data.sessionId && !sessionId) {
                            setSessionId(data.sessionId);
                            localStorage.setItem(`cs_${username}`, data.sessionId);
                        }
                        setMessages(prev => [...prev.slice(0, -1), { role: 'model', message: aiText }]);
                    } else {
                        const reader = response.body.getReader();
                        const decoder = new TextDecoder();
                        let buffer = '';
                        let firstChunkReceived = false;

                        while (true) {
                            const { done, value } = await reader.read();
                            if (done) {
                                if (buffer.trim() && !aiText) {
                                    aiText = buffer.trim();
                                    try {
                                        const data = JSON.parse(aiText);
                                        aiText = data.message || data.chunk || data.error || aiText;
                                    } catch { }
                                    setMessages(prev => [...prev.slice(0, -1), { role: 'model', message: aiText }]);
                                }
                                break;
                            }
                            buffer += decoder.decode(value, { stream: true });

                            if (!firstChunkReceived && buffer.trim()) {
                                setLoading(false);
                                firstChunkReceived = true;
                            }

                            let newlineIdx;
                            while ((newlineIdx = buffer.indexOf('\n')) !== -1) {
                                const line = buffer.slice(0, newlineIdx).trim();
                                buffer = buffer.slice(newlineIdx + 1);

                                if (line) {
                                    let parsedData = null;
                                    if (line.startsWith('data:')) {
                                        let jsonStr = line.slice(5).trim();
                                        try { parsedData = JSON.parse(jsonStr); } catch { }
                                    } else if (line.startsWith('{')) {
                                        try { parsedData = JSON.parse(line); } catch { }
                                    }

                                    if (parsedData) {
                                        if (parsedData.error) aiText += parsedData.error;
                                        else if (typeof parsedData.chunk === 'string') aiText += parsedData.chunk;
                                        else if (typeof parsedData.message === 'string') aiText += parsedData.message;

                                        if (parsedData.sessionId && !sessionId) {
                                            setSessionId(parsedData.sessionId);
                                            localStorage.setItem(`cs_${username}`, parsedData.sessionId);
                                        }
                                        setMessages(prev => [...prev.slice(0, -1), { role: 'model', message: aiText }]);
                                    } else if (!line.startsWith('data:') && !line.startsWith('{')) {
                                        if (line && line !== 'data:') {
                                            aiText += line + '\n';
                                            setMessages(prev => [...prev.slice(0, -1), { role: 'model', message: aiText }]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (streamErr) {
                    console.warn("Stream failed, falling back to /send", streamErr);
                    const { ok, data } = await api.sendChat({ username, sessionId: sessionId || '', message: msg });
                    setLoading(false);
                    if (ok && data) {
                        if (data.sessionId && !sessionId) {
                            setSessionId(data.sessionId);
                            localStorage.setItem(`cs_${username}`, data.sessionId);
                        }
                        aiText = data.message || data.chunk || 'Không nhận được phản hồi.';
                        setMessages(prev => {
                            const newMsgs = [...prev];
                            const last = newMsgs[newMsgs.length - 1];
                            if (last?.role === 'model' && !last.message) {
                                last.message = aiText;
                            } else {
                                newMsgs.push({ role: 'model', message: aiText });
                            }
                            return newMsgs;
                        });
                    } else {
                        throw new Error("Fallback failed");
                    }
                }

                // Nếu stream kết thúc mà không có nội dung, hiển thị lỗi
                if (!aiText.trim()) {
                    setMessages(prev => {
                        const newMsgs = [...prev];
                        const last = newMsgs[newMsgs.length - 1];
                        if (last?.role === 'model' && !last.message) {
                            last.message = '⚠️ AI không trả lời được lúc này. Vui lòng thử lại sau.';
                        }
                        return newMsgs;
                    });
                }
            }
        } catch (err) {
            setMessages(prev => {
                const last = prev[prev.length - 1];
                if (last?.role === 'model' && !last.message) {
                    return [...prev.slice(0, -1), { role: 'model', message: '⚠️ Không thể kết nối AI!' }];
                }
                return [...prev, { role: 'model', message: '⚠️ Không thể kết nối AI!' }];
            });
        } finally {
            setLoading(false);
            inputRef.current?.focus();
        }
    };

    const handlePdfFile = async (e) => {
        const file = e.target.files?.[0];
        e.target.value = '';
        if (!file) return;
        const isPdf = file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
        if (!isPdf) {
            setPhase('chat');
            setMessages(prev => [...prev, { role: 'model', message: '⚠️ Chỉ chấp nhận file **PDF**.' }]);
            return;
        }
        setAttachMenuOpen(false); setUploadingPdf(true); setPhase('chat');
        try {
            const { ok, data } = await api.uploadPdf(file);
            if (ok && (data.success === 'true' || data.success === true) && data.filePath) {
                setAttachedPdf({ path: data.filePath, name: file.name });
                setMessages(prev => [...prev, { role: 'model', message: `📎 Nhận file **${file.name}** thành công.\n\nBạn cần mình tóm tắt hay phân tích điều gì?` }]);
            } else {
                setMessages(prev => [...prev, { role: 'model', message: data?.message || 'Tải PDF thất bại.' }]);
            }
        } catch {
            setMessages(prev => [...prev, { role: 'model', message: 'Không kết nối được server.' }]);
        } finally {
            setUploadingPdf(false); inputRef.current?.focus();
        }
    };

    const handleNew = () => {
        if (sessionId) { api.clearChatHistory(sessionId); localStorage.removeItem(`cs_${username}`); }
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
    const handleBubbleAction = (action) => {
        if (action.action === 'send') {
            send(action.target);
        } else {
            handleNavigate(action);
        }
    };

    return (
        <>
            <style>{`
                @import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@300;400;500;600;700&display=swap');

                /* ── RESET / BASE ── */
                .bb-panel *{box-sizing:border-box;margin:0;padding:0}

                /* ── PANEL ── */
                .bb-panel{
                    position:fixed;
                    bottom:24px;
                    right:24px;
                    width:380px;
                    height:calc(100vh - 96px);
                    max-height:700px;
                    min-height:480px;
                    background:#ffffff;
                    border-radius:12px;
                    box-shadow:0 8px 32px rgba(0,0,0,0.12), 0 2px 8px rgba(0,0,0,0.06);
                    display:flex;
                    flex-direction:column;
                    z-index:999;
                    border:1px solid #e2e4e9;
                    animation:bb-enter .22s cubic-bezier(.16,1,.3,1);
                    overflow:hidden;
                    font-family: 'DM Sans', -apple-system, sans-serif;
                    color: #1a1d23;
                }

                @keyframes bb-enter{from{opacity:0;transform:translateY(14px) scale(0.98)}to{opacity:1;transform:none}}
                @keyframes bb-fade{from{opacity:0;transform:translateY(3px)}to{opacity:1;transform:none}}
                @keyframes bb-dot{0%,60%,100%{transform:translateY(0);opacity:.3}30%{transform:translateY(-5px);opacity:1}}

                /* ── HEADER ── */
                .bb-header{
                    display:flex;justify-content:space-between;align-items:center;
                    padding:14px 18px;
                    background:#ffffff;
                    flex-shrink:0;
                    border-bottom:1px solid #eaecf0;
                }
                .bb-header-left{display:flex;align-items:center;}
                
                /* AI Badge */
                .bb-ai-badge{
                    display:flex;align-items:center;gap:5px;
                    color:#4f35a3;
                    font-size:13px;
                    font-weight:600;
                    letter-spacing:-0.01em;
                }
                .bb-ai-badge-star{
                    width:16px;height:16px;display:flex;align-items:center;justify-content:center;
                    color:#5b21b6;font-size:14px;
                }
                .bb-header-btns{display:flex;align-items:center;gap:4px}
                .bb-hbtn-new{
                    background:transparent;border:none;cursor:pointer;
                    color:#6b7280;padding:6px;display:flex;align-items:center;justify-content:center;
                    border-radius:6px;transition:all 0.15s;
                }
                .bb-hbtn-new:hover{color:#1a1d23;background:#f3f4f6}
                .bb-hbtn-close{
                    background:transparent;border:none;cursor:pointer;
                    color:#6b7280;display:flex;align-items:center;justify-content:center;
                    padding:6px;border-radius:6px;transition:all 0.15s;
                }
                .bb-hbtn-close:hover{color:#1a1d23;background:#f3f4f6}

                /* ── SCROLL BODY ── */
                .bb-body{
                    flex:1;overflow-y:auto;padding:22px 18px 10px;
                    scroll-behavior:smooth;
                    background:#ffffff;
                }
                .bb-body::-webkit-scrollbar{width:4px}
                .bb-body::-webkit-scrollbar-track{background:transparent}
                .bb-body::-webkit-scrollbar-thumb{background:#e2e4e9;border-radius:10px}
                .bb-body::-webkit-scrollbar-thumb:hover{background:#d1d5db}

                /* ── WELCOME ── */
                .bb-welcome{animation:bb-fade .3s ease-out;}
                .bb-welcome-title{
                    font-size:22px;font-weight:700;
                    color:#1a1d23;
                    margin-bottom:4px;line-height:1.25;
                    letter-spacing:-0.02em;
                }
                .bb-welcome-sub{font-size:13.5px;color:#6b7280;margin-bottom:26px;font-weight:400;}
                
                /* Section Headers */
                .bb-section-title{
                    font-size:13px;font-weight:700;
                    color:#1a1d23;
                    margin:0 0 8px;
                }
                
                /* QA Containers */
                .bb-qa-container{
                    display:flex;flex-direction:column;gap:6px;
                }

                /* Large action card */
                .bb-qa-card-large{
                    display:flex;align-items:center;gap:12px;
                    padding:13px 14px;border-radius:8px;
                    border:1px solid #eaecf0;
                    background:#fafafa;cursor:pointer;transition:all 0.14s ease;text-align:left;
                    font-family:inherit;width:100%;
                }
                .bb-qa-card-large:hover{background:#f3f4f6;border-color:#d1d5db;}
                .bb-qa-icon-wrap{
                    display:flex;align-items:center;justify-content:center;
                    width:34px;height:34px;border-radius:7px;
                    flex-shrink:0;
                }
                .bb-qa-content{flex:1;min-width:0}
                .bb-qa-card-large-title{font-size:13.5px;font-weight:600;color:#1a1d23;margin-bottom:1px}
                .bb-qa-card-large-sub{font-size:11.5px;color:#6b7280;font-weight:400}

                /* Small FAQ card */
                .bb-qa-card-small{
                    display:flex;align-items:center;gap:10px;
                    padding:11px 14px;border-radius:8px;border:none;
                    background:transparent;cursor:pointer;transition:all 0.14s ease;text-align:left;
                    font-family:inherit;width:100%;
                }
                .bb-qa-card-small:hover{background:#f3f4f6;}
                .bb-qa-card-small-icon{
                    color:#9ca3af;flex-shrink:0;display:flex;align-items:center;
                }
                .bb-qa-card-small-title{font-size:13px;font-weight:500;color:#374151}

                /* ── MESSAGES ── */
                .bb-row{display:flex;margin-bottom:20px;animation:bb-fade .22s ease-out}
                .bb-row-user{justify-content:flex-end}
                .bb-row-ai{justify-content:flex-start;flex-direction:column;align-items:stretch;}
                
                .bb-bubble-user{
                    max-width:82%;background:#f3f4f6;color:#1a1d23;
                    border-radius:14px 14px 4px 14px;padding:10px 14px;
                    font-size:13.5px;line-height:1.55;word-break:break-word;
                }
                .bb-bubble-ai-wrap{width:100%;}
                .bb-bubble-ai{
                    background:transparent;border:none;padding:0;
                    font-size:13.5px;line-height:1.7;color:#1a1d23;word-break:break-word;
                }
                
                /* Feedback block */
                .bb-feedback-block{
                    display:flex;align-items:center;gap:10px;
                    margin-top:14px;color:#9ca3af;font-size:12px;
                }
                .bb-feedback-btn{
                    border:none;background:transparent;cursor:pointer;color:#9ca3af;
                    padding:3px;display:flex;align-items:center;justify-content:center;
                    border-radius:4px;transition:all 0.14s;
                }
                .bb-feedback-btn:hover{color:#374151;background:#f3f4f6}

                /* Suggestions below AI response */
                .bb-suggestions-section{
                    margin-top:18px;padding-top:14px;
                    border-top:1px solid #eaecf0;
                    animation:bb-fade 0.28s ease-out;
                }
                .bb-suggestions-title{
                    font-size:13px;font-weight:700;
                    color:#1a1d23;
                    margin-bottom:8px;
                }
                .bb-suggestions-list{
                    display:flex;flex-direction:column;gap:6px;
                }

                /* ── TYPING DOTS ── */
                .bb-typing{display:flex;gap:4px;align-items:center;padding:10px 14px;background:#f3f4f6;border-radius:12px;width:fit-content}
                .bb-dot-anim{width:5px;height:5px;border-radius:50%;background:#5b21b6;display:inline-block;animation:bb-dot 1.1s ease-in-out infinite}

                /* ── MARKDOWN ── */
                .gemini-markdown {
                    line-height: 1.7;
                    font-size: 13.5px;
                    color: #1a1d23;
                }
                .gemini-markdown p {
                    margin-bottom: 12px;
                }
                .gemini-markdown p:last-child {
                    margin-bottom: 0;
                }
                .gemini-h1 {
                    font-size: 17px;
                    font-weight: 700;
                    color: #111827;
                    margin: 18px 0 8px;
                    letter-spacing: -0.01em;
                }
                .gemini-h2 {
                    font-size: 15px;
                    font-weight: 700;
                    color: #111827;
                    margin: 16px 0 8px;
                    padding-bottom: 4px;
                    border-bottom: 1px solid #f3f4f6;
                }
                .gemini-h3 {
                    font-size: 14px;
                    font-weight: 700;
                    color: #111827;
                    margin: 14px 0 6px;
                }
                .gemini-code-block {
                    background: #f8f9fa;
                    border: 1px solid #e5e7eb;
                    color: #1f2937;
                    border-radius: 8px;
                    padding: 12px;
                    overflow-x: auto;
                    font-size: 12.5px;
                    line-height: 1.5;
                    margin: 10px 0;
                    font-family: monospace;
                }
                .gemini-inline-code {
                    background: #f3f4f6;
                    color: #1f2937;
                    padding: 1.5px 5px;
                    border-radius: 4px;
                    font-size: 12.5px;
                    font-family: monospace;
                    border: 1px solid #e5e7eb;
                }
                .gemini-blockquote {
                    border-left: 3px solid #6d28d9;
                    padding: 6px 12px;
                    margin: 10px 0;
                    color: #4b5563;
                    background: #f5f3ff;
                    border-radius: 0 8px 8px 0;
                    font-style: italic;
                }
                .gemini-ul {
                    list-style-type: none;
                    padding-left: 0;
                    margin: 8px 0;
                }
                .gemini-ol {
                    list-style-type: decimal;
                    padding-left: 18px;
                    margin: 8px 0;
                }
                .gemini-li {
                    position: relative;
                    margin-bottom: 6px;
                    line-height: 1.6;
                    padding-left: 18px;
                }
                .gemini-ul > .gemini-li::before {
                    content: "•";
                    position: absolute;
                    left: 0;
                    color: #6d28d9;
                    font-weight: bold;
                    font-size: 15px;
                    line-height: 1;
                    top: 1.5px;
                }
                .gemini-li .gemini-ul {
                    margin: 4px 0 2px;
                }
                .gemini-li .gemini-li {
                    padding-left: 14px;
                    font-size: 13px;
                    color: #4b5563;
                }
                .gemini-li .gemini-ul > .gemini-li::before {
                    content: "◦";
                    color: #6d28d9;
                    font-size: 13px;
                    top: 1px;
                }
                .gemini-hr {
                    border: none;
                    border-top: 1px solid #e5e7eb;
                    margin: 14px 0;
                }
                .gemini-link {
                    color: #2563eb;
                    text-decoration: none;
                    border-bottom: 1px dashed rgba(37, 99, 235, 0.4);
                }
                .gemini-link:hover {
                    color: #1d4ed8;
                    border-bottom-style: solid;
                }
                
                /* Tables */
                .gemini-table {
                    width: 100%;
                    border-collapse: collapse;
                    margin: 12px 0;
                    font-size: 12.5px;
                    border: 1px solid #e5e7eb;
                    border-radius: 6px;
                    overflow: hidden;
                }
                .gemini-thead {
                    background: #f9fafb;
                }
                .gemini-tr {
                    border-bottom: 1px solid #e5e7eb;
                }
                .gemini-tr:last-child {
                     border-bottom: none;
                }
                .gemini-th {
                     padding: 8px 10px;
                     text-align: left;
                     font-weight: 600;
                     color: #374151;
                }
                .gemini-td {
                     padding: 8px 10px;
                     color: #4b5563;
                }
                .bb-gap{height:8px}

                /* ── INPUT AREA ── */
                .bb-input-area{
                    flex-shrink:0;padding:10px 16px 14px;
                    background:#ffffff;border-top:1px solid #eaecf0;
                }
                .bb-input-box{
                    display:flex;align-items:flex-end;gap:8px;
                    background:#ffffff;border-radius:8px;padding:4px 4px 4px 10px;
                    border:1px solid #d1d5db;transition:all 0.15s ease;
                }
                .bb-input-box:focus-within{
                    border-color:#5b21b6;
                    box-shadow:0 0 0 3px rgba(91, 33, 182, 0.12);
                }
                .bb-textarea{
                    flex:1;border:none;outline:none;background:transparent;
                    font-size:13px;color:#1a1d23;font-family:inherit;
                    padding:9px 0;resize:none;max-height:140px;line-height:1.45;
                }
                .bb-textarea::placeholder{color:#b0b7c3;font-size:13px}

                /* ── ATTACH ── */
                .bb-attach-wrap{position:relative;flex-shrink:0}
                .bb-attach-btn{
                    width:32px;height:32px;border-radius:6px;border:none;
                    background:transparent;cursor:pointer;
                    display:flex;align-items:center;justify-content:center;
                    transition:all 0.15s;color:#9ca3af;margin-bottom:2px;
                }
                .bb-attach-btn:hover{background:#f3f4f6;color:#374151}
                .bb-attach-btn.open{background:#f3f4f6;color:#5b21b6}
                .bb-attach-menu{
                    position:absolute;left:0;bottom:calc(100% + 6px);
                    background:#ffffff;border:1px solid #e2e4e9;border-radius:8px;
                    box-shadow:0 6px 20px rgba(0,0,0,0.08);padding:4px;
                    min-width:165px;z-index:30;animation:bb-fade .12s ease-out;
                }
                .bb-attach-item{
                    display:flex;align-items:center;gap:8px;width:100%;
                    padding:8px 10px;border:none;background:transparent;border-radius:6px;
                    font-size:12.5px;color:#374151;cursor:pointer;font-family:inherit;text-align:left;
                    transition:background 0.1s;
                }
                .bb-attach-item:hover{background:#f3f4f6;color:#5b21b6}
                .bb-attach-note{font-size:10.5px;color:#9ca3af;padding:2px 10px 4px}

                /* ── SEND BTN ── */
                .bb-send{
                    width:32px;height:32px;border-radius:50%;border:none;flex-shrink:0;
                    display:flex;align-items:center;justify-content:center;
                    transition:all 0.15s ease;cursor:pointer;margin-bottom:2px;
                }
                .bb-send.active{ background:#5b21b6; }
                .bb-send.active:hover{ background:#4c1d95; transform:scale(1.04); }
                .bb-send.inactive{background:#f3f4f6;cursor:default}

                /* ── FOOTER NOTE ── */
                .bb-footer-note{text-align:left;font-size:11px;color:#9ca3af;margin-top:8px;padding-left:2px;line-height:1.5}

                /* ── PDF BADGE ── */
                .bb-pdf-badge{
                    display:flex;align-items:center;gap:8px;margin-bottom:10px;
                    padding:8px 12px;background:#f5f3ff;border:1px solid #ede9fe;
                    border-radius:8px;font-size:12.5px;color:#5b21b6;
                }
                .bb-pdf-remove{border:none;background:transparent;cursor:pointer;color:#9ca3af;padding:2px;line-height:0;flex-shrink:0;transition:color 0.15s;}
                .bb-pdf-remove:hover{color:#ef4444}
                .bb-pdf-name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:500;}
                .bb-gap{height:8px}

                /* ── RESPONSIVE ── */
                @media(max-width:500px){
                    .bb-panel{width:calc(100vw - 16px);right:8px;bottom:8px;height:calc(100vh - 80px);max-height:none;border-radius:10px}
                }
            `}</style>

            <div className="bb-panel">
                {/* HEADER */}
                <div className="bb-header">
                    <div className="bb-header-left">
                        <div className="bb-ai-badge">
                            <span className="bb-ai-badge-star">✦</span>
                            AI-Assisted
                        </div>
                    </div>
                    <div className="bb-header-btns">
                        <button className="bb-hbtn-new" onClick={handleNew} title="Cuộc trò chuyện mới" type="button">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                                <path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                            </svg>
                        </button>
                        <button className="bb-hbtn-close" onClick={onClose} title="Đóng" type="button">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                            </svg>
                        </button>
                    </div>
                </div>

                {/* BODY */}
                <div className="bb-body">
                    {phase === 'welcome' && (
                        <div className="bb-welcome">
                            <div className="bb-welcome-title">Xin chào, {username || 'Khanh'}</div>
                            <div className="bb-welcome-sub">Bạn muốn biết thêm điều gì?</div>

                            <div className="bb-section-title">Thao tác nhanh</div>
                            <div className="bb-qa-container" style={{ marginBottom: '20px' }}>
                                <button className="bb-qa-card-large" onClick={() => send('Tìm kiếm sách')} type="button">
                                    <div className="bb-qa-icon-wrap" style={{ background: '#ede9fe', color: '#5b21b6' }}>
                                        <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                                            <polyline points="14 2 14 8 20 8" />
                                        </svg>
                                    </div>
                                    <div className="bb-qa-content">
                                        <div className="bb-qa-card-large-title">Tìm kiếm sách</div>
                                        <div className="bb-qa-card-large-sub">Tìm sách theo tên, tác giả hoặc thể loại</div>
                                    </div>
                                </button>
                            </div>

                            <div className="bb-section-title">Câu hỏi thường gặp</div>
                            <div className="bb-qa-container">
                                <button className="bb-qa-card-small" onClick={() => send('Làm sao để mua sách?')} type="button">
                                    <div className="bb-qa-card-small-icon">
                                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                            <circle cx="12" cy="12" r="10" /><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" /><line x1="12" y1="17" x2="12.01" y2="17" />
                                        </svg>
                                    </div>
                                    <div className="bb-qa-card-small-title">Làm sao để mua sách?</div>
                                </button>
                                <button className="bb-qa-card-small" onClick={() => send('Cách theo dõi đơn hàng?')} type="button">
                                    <div className="bb-qa-card-small-icon">
                                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                            <circle cx="12" cy="12" r="10" /><path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" /><line x1="12" y1="17" x2="12.01" y2="17" />
                                        </svg>
                                    </div>
                                    <div className="bb-qa-card-small-title">Cách theo dõi đơn hàng?</div>
                                </button>
                            </div>
                        </div>
                    )}

                    {phase === 'chat' && (
                        <>
                            {messages.map((m, i) => (
                                <Bubble key={i} msg={m} onNavigate={handleBubbleAction} isLast={i === messages.length - 1} />
                            ))}
                            {loading && (
                                <div className="bb-row bb-row-ai">
                                    <TypingDots />
                                </div>
                            )}
                            <div ref={endRef} />
                        </>
                    )}
                </div>

                {/* INPUT */}
                <div className="bb-input-area">
                    {attachedPdf && (
                        <div className="bb-pdf-badge">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                                <polyline points="14 2 14 8 20 8" />
                            </svg>
                            <span className="bb-pdf-name">{attachedPdf.name}</span>
                            <button className="bb-pdf-remove" onClick={() => setAttachedPdf(null)} type="button">
                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                                    <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                                </svg>
                            </button>
                        </div>
                    )}

                    <input ref={fileInputRef} type="file" accept=".pdf,application/pdf" style={{ display: 'none' }} onChange={handlePdfFile} />

                    <div className="bb-input-box">
                        {/* Attach */}
                        <div className="bb-attach-wrap" ref={attachMenuRef}>
                            <button
                                className={`bb-attach-btn${attachMenuOpen ? ' open' : ''}`}
                                onClick={() => setAttachMenuOpen(v => !v)}
                                disabled={loading || uploadingPdf}
                                type="button"
                            >
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
                                </svg>
                            </button>
                            {attachMenuOpen && (
                                <div className="bb-attach-menu">
                                    <button className="bb-attach-item" onClick={() => fileInputRef.current?.click()} type="button">
                                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#6d28d9" strokeWidth="2">
                                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                                            <polyline points="14 2 14 8 20 8" />
                                        </svg>
                                        Đính kèm PDF
                                    </button>
                                    <div className="bb-attach-note">Chỉ hỗ trợ file PDF</div>
                                </div>
                            )}
                        </div>

                        {/* Textarea */}
                        <textarea
                            ref={inputRef}
                            className="bb-textarea"
                            value={input}
                            onChange={e => {
                                setInput(e.target.value);
                                e.target.style.height = 'auto';
                                e.target.style.height = Math.min(e.target.scrollHeight, 140) + 'px';
                            }}
                            onKeyDown={e => {
                                if (e.key === 'Enter' && !e.shiftKey) {
                                    e.preventDefault(); send();
                                    e.target.style.height = 'auto';
                                }
                            }}
                            placeholder={attachedPdf ? 'Hỏi về tài liệu đính kèm...' : 'Nhập nội dung...'}
                            disabled={loading || uploadingPdf}
                            rows={1}
                        />

                        {/* Send */}
                        <button
                            className={`bb-send ${input.trim() && !loading && !uploadingPdf ? 'active' : 'inactive'}`}
                            onClick={() => { send(); if (inputRef.current) inputRef.current.style.height = 'auto'; }}
                            disabled={!input.trim() || loading || uploadingPdf}
                            type="button"
                        >
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none"
                                stroke={input.trim() && !loading && !uploadingPdf ? '#fff' : '#9ca3af'} strokeWidth="2.5">
                                <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
                            </svg>
                        </button>
                    </div>

                    <div className="bb-footer-note">
                        {uploadingPdf ? '⏳ Đang tải PDF...' : 'Responses are generated with AI and are not legal advice.'}
                    </div>
                </div>
            </div>
        </>
    );
}