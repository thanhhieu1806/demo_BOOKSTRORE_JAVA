export default function UserDetailModal({ user, isAdmin, onClose }) {
    const statusDotClass = {
        ACTIVE: 'status-dot-online',
        Temp_Lock: 'status-dot-lock',
        Delete: 'status-dot-deleted',
    }[user.status] || 'status-dot-online';

    let rows = [
        ['ID', `#${user.id}`],
        ['Username', user.username],
        ['Email', user.email],
        ['Role', user.role],
        ['Trạng thái', user.status],
        ['Login sai', `${user.counterLogin} lần`],
        ['Ngày tạo', user.createdDate || '—'],
        ['Cập nhật cuối', user.updatedDate || '—'],
        ['Đăng nhập cuối', user.lastLoginDate || 'Chưa đăng nhập'],
    ];

    if (!isAdmin) {
        rows = rows.filter(r => r[0] !== 'Login sai' && r[0] !== 'Đăng nhập cuối' && r[0] !== 'Cập nhật cuối');
    }

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box modal-sm" onClick={e => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>Chi tiết tài khoản</h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>
                <div className="modal-body">
                    <div className="detail-avatar">
                        <div className="avatar-wrap avatar-wrap-lg">
                            <div className="avatar-lg">{user.username[0].toUpperCase()}</div>
                            <span className={`status-dot ${statusDotClass}`} title={user.status} />
                        </div>
                        <h3>{user.username}</h3>
                    </div>
                    <table className="detail-table">
                        <tbody>
                            {rows.map(([label, val]) => (
                                <tr key={label}>
                                    <td className="detail-label">{label}</td>
                                    <td className="detail-val">{val}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
                <div className="modal-footer">
                    <button className="btn-cancel" onClick={onClose}>Đóng</button>
                </div>
            </div>
        </div>
    );
}
