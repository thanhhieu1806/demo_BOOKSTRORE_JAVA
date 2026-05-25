import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import ChatPanel from './ChatPanel';

export default function ChatWidget() {
    const { user } = useAuth();
    const [isOpen, setIsOpen] = useState(false);

    // Nếu là ADMIN thì không hiện ChatWidget
    if (user?.role === 'ADMIN') return null;

    return (
        <>
            {/* Nút bong bóng chat */}
            <button
                onClick={() => setIsOpen(!isOpen)}
                style={{
                    position: 'fixed',
                    bottom: '30px',
                    right: '30px',
                    width: '60px',
                    height: '60px',
                    borderRadius: '50%',
                    background: 'linear-gradient(135deg, #7c3aed, #2dd4bf)',
                    color: 'white',
                    border: 'none',
                    cursor: 'pointer',
                    boxShadow: '0 8px 24px rgba(124, 58, 237, 0.4)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    zIndex: 999,
                    transition: 'all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275)',
                    transform: isOpen ? 'rotate(90deg)' : 'none'
                }}
                onMouseEnter={e => {
                    e.currentTarget.style.transform = isOpen ? 'rotate(90deg) scale(1.1)' : 'scale(1.1)';
                    e.currentTarget.style.boxShadow = '0 12px 32px rgba(124, 58, 237, 0.5)';
                }}
                onMouseLeave={e => {
                    e.currentTarget.style.transform = isOpen ? 'rotate(90deg)' : 'scale(1)';
                    e.currentTarget.style.boxShadow = '0 8px 24px rgba(124, 58, 237, 0.4)';
                }}
            >
                {isOpen ? (
                    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
                    </svg>
                ) : (
                    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
                    </svg>
                )}
            </button>

            {/* Panel Chat */}
            {isOpen && (
                <ChatPanel 
                    onClose={() => setIsOpen(false)} 
                    username={user?.username || 'Khách'} 
                />
            )}
        </>
    );
}
