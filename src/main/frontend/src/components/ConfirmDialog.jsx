export default function ConfirmDialog({ title, message, type, onConfirm, onCancel }) {
    return (
        <div className="modal-overlay" onClick={onCancel}>
            <div className="modal-box modal-sm confirm-box" onClick={e => e.stopPropagation()}>
                <div className={`confirm-icon confirm-${type}`}>
                    {type === 'danger' ? (
                        <svg viewBox="0 0 20 20" fill="currentColor">
                            <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                        </svg>
                    ) : (
                        <svg viewBox="0 0 20 20" fill="currentColor">
                            <path d="M10 2a5 5 0 00-5 5v2a2 2 0 00-2 2v5a2 2 0 002 2h10a2 2 0 002-2v-5a2 2 0 00-2-2H7V7a3 3 0 015.905-.75 1 1 0 001.937-.5A5.002 5.002 0 0010 2z" />
                        </svg>
                    )}
                </div>
                <h3>{title}</h3>
                <p>{message}</p>
                <div className="modal-footer">
                    <button className="btn-cancel" onClick={onCancel}>Hủy</button>
                    <button className={`btn-primary ${type === 'danger' ? 'btn-danger' : ''}`} onClick={onConfirm}>
                        Xác nhận
                    </button>
                </div>
            </div>
        </div>
    );
}