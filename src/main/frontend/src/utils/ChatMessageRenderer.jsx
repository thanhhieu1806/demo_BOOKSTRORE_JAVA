/**
 * Parse ActionTrigger tags từ AI response và render nút điều hướng.
 *
 * import { parseAIMessage, ActionButtons, runChatAction } from '../utils/ChatMessageRenderer';
 */

import { useState } from 'react';

// ─── PARSER ────────────────────────────────────────────────────────────────────

/**
 * @typedef {{ type: string, target: string, id: string|null, query: string|null, label: string }} ChatAction
 */

/**
 * Tách text thuần và danh sách actions từ response AI.
 * @param {string} rawText
 * @returns {{ text: string, actions: ChatAction[] }}
 */
export function parseAIMessage(rawText) {
    if (!rawText) return { text: '', actions: [] };

    const actions = [];
    const triggerRegex = /<ActionTrigger\s+([^>]+)>([\s\S]*?)<\/ActionTrigger>/gi;

    let match;
    while ((match = triggerRegex.exec(rawText)) !== null) {
        const attrString = match[1];
        const label = match[2].trim();
        const attrs = {};
        const attrRegex = /(\w+)="([^"]*)"/g;
        let a;
        while ((a = attrRegex.exec(attrString)) !== null) {
            attrs[a[1]] = a[2];
        }

        actions.push({
            type: attrs.type || 'navigate',
            target: attrs.target || '',
            id: attrs.id || null,
            query: attrs.query || null,
            label,
        });
    }

    const cleanText = rawText
        .replace(/<ActionTrigger[\s\S]*?<\/ActionTrigger>/gi, '')
        .replace(/\n{3,}/g, '\n\n')
        .trim();

    return { text: cleanText, actions };
}

// ─── URL BUILDER (tham khảo / fallback link) ───────────────────────────────────

/** Đường dẫn tương đối trong React Router */
export function buildActionUrl(action) {
    switch (action.type) {
        case 'view-detail':
            return action.id ? `/book-detail?id=${action.id}` : '/cart';

        case 'order':
            return action.id ? `/cart?add=${action.id}` : '/cart';

        case 'navigate':
            switch (action.target) {
                case 'books':
                    return '/cart';
                case 'orders':
                    return '/orders';
                case 'cart':
                    return '/cart';
                default:
                    return action.target ? `/${action.target}` : '/cart';
            }

        case 'search':
            return action.query
                ? `/cart?q=${encodeURIComponent(action.query)}`
                : '/cart';

        default:
            return '/cart';
    }
}

// ─── SPA NAVIGATION ────────────────────────────────────────────────────────────

/**
 * Điều hướng trong app (React Router).
 * @param {ChatAction} action
 * @param {{ navigate: Function, addBookToCart?: (id: string) => Promise<boolean> }} handlers
 */
export async function runChatAction(action, { navigate, addBookToCart }) {
    if (!action || !navigate) return;

    const { type, target, id, query } = action;

    if (type === 'view-detail' && target === 'book-detail' && id) {
        navigate(`/book-detail?id=${id}`);
        return;
    }
    if (type === 'order' && target === 'cart' && id) {
        if (addBookToCart) await addBookToCart(id);
        navigate('/cart', { state: { tab: 'cart' } });
        return;
    }
    if (type === 'navigate' && target === 'orders') {
        navigate('/orders');
        return;
    }
    if (type === 'navigate' && target === 'books') {
        navigate('/cart', { state: { tab: 'shop', search: query || '' } });
        return;
    }
    if (type === 'search' && target === 'books') {
        navigate('/cart', { state: { tab: 'shop', search: query || '' } });
        return;
    }
    if (id) {
        navigate(`/book-detail?id=${id}`);
    } else if (target) {
        navigate(buildActionUrl(action));
    }
}

// ─── VANILLA JS ────────────────────────────────────────────────────────────────

export function renderActionButtons(actions, container, onNavigate) {
    if (!actions?.length || !container) return;

    const wrapper = document.createElement('div');
    wrapper.className = 'chat-actions';

    actions.forEach((action) => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = `chat-action-btn chat-action-btn--${action.type}`;
        btn.textContent = action.label;
        btn.setAttribute('aria-label', action.label);
        btn.addEventListener('click', () => onNavigate?.(action));
        wrapper.appendChild(btn);
    });

    container.appendChild(wrapper);
}

// ─── REACT ─────────────────────────────────────────────────────────────────────

function ActionButton({ action, onAction }) {
    const [clicked, setClicked] = useState(false);

    const handleClick = () => {
        setClicked(true);
        onAction?.(action);
    };

    return (
        <div className="chat-action-btn-wrap">
            <button
                type="button"
                onClick={handleClick}
                className={`chat-action-btn chat-action-btn--${action.type}${clicked ? ' chat-action-btn--clicked' : ''}`}
                aria-label={action.label}
            >
                {!clicked ? (
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden>
                        <path d="M5 12h14M12 5l7 7-7 7" />
                    </svg>
                ) : (
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden>
                        <polyline points="20 6 9 17 4 12" />
                    </svg>
                )}
                <span>{clicked ? 'Đang chuyển...' : action.label}</span>
            </button>
        </div>
    );
}

/**
 * @param {{ actions: ChatAction[], onAction: (action: ChatAction) => void }} props
 */
export function ActionButtons({ actions, onAction, hint }) {
    if (!actions?.length) return null;

    const defaultHint = 'Nhấn nút để thực hiện ngay — không cần gõ thêm:';
    const showHint = hint !== '';

    return (
        <div className="chat-actions-panel">
            {showHint && <p className="chat-actions-hint">{hint || defaultHint}</p>}
            <div className="chat-actions">
                {actions.map((action, i) => (
                    <ActionButton key={i} action={action} onAction={onAction} />
                ))}
            </div>
        </div>
    );
}
