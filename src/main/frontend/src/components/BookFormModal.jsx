import { useState } from 'react';
import api from '../service/api';

export default function BookFormModal({ book, onClose, onSaved }) {
    const isEdit = !!book;

    // ── State form ────────────────────────────────────────────
    const [form, setForm] = useState({
        title: book?.title || '',
        author: book?.author || '',
        category: book?.category || '',
        description: book?.description || '',
        imageUrl: book?.imageUrl || '',
        price: book?.price || '',
        quantity: book?.quantity ?? 0,
        status: book?.status || 'ACTIVE',
        pdfPath: book?.pdfPath || '',
        pdfName: book?.pdfName || '',
    });

    const [imageFile, setImageFile] = useState(null);  // file ảnh mới chọn
    const [pdfFile, setPdfFile] = useState(null);  // file PDF mới chọn
    const [imagePreview, setImagePreview] = useState(book?.imageUrl || '');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [progress, setProgress] = useState('');    // trạng thái upload

    // ── Xử lý chọn ảnh ───────────────────────────────────────
    const handleImageChange = (e) => {
        const file = e.target.files[0];
        if (!file) return;

        // Kiểm tra loại file
        if (!file.type.startsWith('image/')) {
            setError('Vui lòng chọn file ảnh (JPG, PNG, WEBP)!');
            return;
        }
        // Kiểm tra kích thước (tối đa 5MB)
        if (file.size > 5 * 1024 * 1024) {
            setError('Ảnh không được vượt quá 5MB!');
            return;
        }

        setImageFile(file);
        setError('');

        // Preview ảnh
        const reader = new FileReader();
        reader.onloadend = () => setImagePreview(reader.result);
        reader.readAsDataURL(file);
    };

    // ── Xử lý chọn PDF ───────────────────────────────────────
    const handlePdfChange = (e) => {
        const file = e.target.files[0];
        if (!file) return;

        const isPdf = file.type === 'application/pdf'
            || file.type === 'application/x-pdf'
            || file.type === 'application/octet-stream'
            || file.name.toLowerCase().endsWith('.pdf');
        if (!isPdf) {
            setError('Vui lòng chọn file PDF!');
            return;
        }
        // Kiểm tra kích thước (tối đa 20MB)
        if (file.size > 20 * 1024 * 1024) {
            setError('File PDF không được vượt quá 20MB!');
            return;
        }

        setPdfFile(file);
        setError('');
    };

    // ── Submit form ───────────────────────────────────────────
    const handleSubmit = async (e) => {
        e.preventDefault();

        // Validate
        if (!form.title.trim()) { setError('Tên sách không được trống!'); return; }
        if (!form.price) { setError('Giá không được trống!'); return; }
        if (parseFloat(form.price) <= 0) { setError('Giá phải lớn hơn 0!'); return; }

        setLoading(true);
        setError('');

        try {
            let imageUrl = form.imageUrl;
            let pdfPath = form.pdfPath;
            let pdfName = form.pdfName;

            // 1. Upload ảnh bìa nếu có chọn file mới
            if (imageFile) {
                setProgress('Đang upload ảnh bìa...');
                const { ok, data } = await api.uploadImage(imageFile);
                if (ok && data.success === 'true') {
                    imageUrl = data.url;
                } else {
                    setError('Upload ảnh thất bại: ' + (data.message || 'Lỗi không xác định'));
                    setLoading(false);
                    setProgress('');
                    return;
                }
            }

            // 2. Upload PDF nếu có chọn file mới
            if (pdfFile) {
                setProgress('Đang upload file PDF...');
                const { ok, data } = await api.uploadPdf(pdfFile);
                if (ok && data.success === 'true') {
                    pdfPath = data.filePath;
                    pdfName = pdfFile.name;
                } else {
                    setError('Upload PDF thất bại: ' + (data.message || 'Lỗi không xác định'));
                    setLoading(false);
                    setProgress('');
                    return;
                }
            }

            setProgress('Đang lưu thông tin sách...');

            // 3. Gọi API thêm/sửa sách
            const payload = {
                ...form,
                price: parseFloat(form.price),
                quantity: parseInt(form.quantity),
                imageUrl,
                pdfPath,
                pdfName,
            };

            const { ok, data } = isEdit
                ? await api.updateBook(book.id, payload)
                : await api.addBook(payload);

            if (ok && data.success === 'true') {
                onSaved(data.message);
            } else {
                setError(data.message || 'Thao tác thất bại');
            }

        } catch (err) {
            console.error('[BookFormModal]', err);
            setError('Lỗi kết nối server');
        } finally {
            setLoading(false);
            setProgress('');
        }
    };

    // ── Render ────────────────────────────────────────────────
    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-box" style={{ maxWidth: 560 }}
                onClick={e => e.stopPropagation()}>

                {/* Header */}
                <div className="modal-header">
                    <h2>{isEdit ? '✏️ Chỉnh sửa sách' : '📚 Thêm sách mới'}</h2>
                    <button className="modal-close" onClick={onClose}>✕</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">

                        {/* Tên sách + Tác giả */}
                        <div className="form-row">
                            <div className="field-group">
                                <label>Tên sách <span className="req">*</span></label>
                                <input type="text" value={form.title}
                                    onChange={e => setForm({ ...form, title: e.target.value })}
                                    placeholder="Nhập tên sách" />
                            </div>
                            <div className="field-group">
                                <label>Tác giả</label>
                                <input type="text" value={form.author}
                                    onChange={e => setForm({ ...form, author: e.target.value })}
                                    placeholder="Nhập tên tác giả" />
                            </div>
                        </div>

                        {/* Thể loại + Giá */}
                        <div className="form-row">
                            <div className="field-group">
                                <label>Thể loại</label>
                                <input type="text" value={form.category}
                                    onChange={e => setForm({ ...form, category: e.target.value })}
                                    placeholder="VD: Văn học, Kỹ năng..." />
                            </div>
                            <div className="field-group">
                                <label>Giá (VNĐ) <span className="req">*</span></label>
                                <input type="number" min="0" value={form.price}
                                    onChange={e => setForm({ ...form, price: e.target.value })}
                                    placeholder="0" />
                            </div>
                        </div>

                        {/* Số lượng + Trạng thái */}
                        <div className="form-row">
                            <div className="field-group">
                                <label>Số lượng tồn kho</label>
                                <input type="number" min="0" value={form.quantity}
                                    onChange={e => setForm({ ...form, quantity: e.target.value })}
                                    placeholder="0" />
                            </div>
                            {isEdit && (
                                <div className="field-group">
                                    <label>Trạng thái</label>
                                    <select value={form.status}
                                        onChange={e => setForm({ ...form, status: e.target.value })}>
                                        <option value="ACTIVE">Đang bán</option>
                                        <option value="INACTIVE">Ngừng bán</option>
                                    </select>
                                </div>
                            )}
                        </div>

                        {/* Upload ảnh bìa */}
                        <div className="field-group">
                            <label>Ảnh bìa sách</label>
                            <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                                {/* Preview ảnh */}
                                <div style={{
                                    width: 80, height: 100, borderRadius: 8, overflow: 'hidden',
                                    background: '#f3f4f6', border: '1px solid #e5e7eb',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    flexShrink: 0,
                                }}>
                                    {imagePreview
                                        ? <img src={imagePreview} alt="preview"
                                            style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                        : <span style={{ fontSize: 28 }}>📖</span>
                                    }
                                </div>
                                <div style={{ flex: 1 }}>
                                    <input type="file" accept="image/*"
                                        onChange={handleImageChange}
                                        style={{ fontSize: 13 }} />
                                    <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 4 }}>
                                        Hỗ trợ: JPG, PNG, WEBP. Tối đa 5MB
                                    </div>
                                    {/* URL ảnh thủ công */}
                                    <input type="text" value={form.imageUrl}
                                        onChange={e => {
                                            setForm({ ...form, imageUrl: e.target.value });
                                            setImagePreview(e.target.value);
                                        }}
                                        placeholder="Hoặc nhập URL ảnh..."
                                        style={{ marginTop: 6, fontSize: 12 }} />
                                </div>
                            </div>
                        </div>

                        {/* Upload PDF */}
                        <div className="field-group">
                            <label>File PDF sách</label>
                            <input type="file" accept=".pdf"
                                onChange={handlePdfChange}
                                style={{ fontSize: 13 }} />
                            <div style={{ fontSize: 11, color: '#9ca3af', marginTop: 4 }}>
                                Chỉ hỗ trợ file PDF. Tối đa 20MB
                            </div>
                            {/* Hiển thị tên PDF đã có */}
                            {form.pdfName && !pdfFile && (
                                <div style={{
                                    fontSize: 12, color: '#4f46e5',
                                    marginTop: 6, display: 'flex', alignItems: 'center', gap: 4
                                }}>
                                    📄 PDF hiện tại: <strong>{form.pdfName}</strong>
                                </div>
                            )}
                            {pdfFile && (
                                <div style={{
                                    fontSize: 12, color: '#059669',
                                    marginTop: 6, display: 'flex', alignItems: 'center', gap: 4
                                }}>
                                    ✅ Đã chọn: <strong>{pdfFile.name}</strong>
                                    <span style={{ color: '#9ca3af' }}>
                                        ({(pdfFile.size / 1024 / 1024).toFixed(1)} MB)
                                    </span>
                                </div>
                            )}
                        </div>

                        {/* Mô tả */}
                        <div className="field-group">
                            <label>Mô tả</label>
                            <textarea rows={3} value={form.description}
                                onChange={e => setForm({ ...form, description: e.target.value })}
                                placeholder="Mô tả ngắn về sách..."
                                style={{
                                    resize: 'vertical', padding: '11px 14px',
                                    background: 'var(--bg-glass)',
                                    border: '1px solid var(--border)',
                                    borderRadius: 'var(--radius-sm)',
                                    color: 'var(--text)', fontSize: 14,
                                    fontFamily: 'inherit', outline: 'none',
                                    width: '100%', boxSizing: 'border-box',
                                }} />
                        </div>

                        {/* Progress upload */}
                        {progress && (
                            <div style={{
                                background: '#ede9fe', borderRadius: 8,
                                padding: '10px 14px', fontSize: 13,
                                color: '#6d28d9', display: 'flex',
                                alignItems: 'center', gap: 8,
                            }}>
                                <div style={{
                                    width: 14, height: 14,
                                    border: '2px solid #c4b5fd',
                                    borderTopColor: '#7c3aed',
                                    borderRadius: '50%',
                                    animation: 'spin 0.8s linear infinite',
                                }} />
                                {progress}
                                <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
                            </div>
                        )}

                        {/* Error */}
                        {error && (
                            <div className="error-box">
                                <span>✕</span> {error}
                            </div>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="modal-footer">
                        <button type="button" className="btn-cancel"
                            onClick={onClose} disabled={loading}>
                            Hủy
                        </button>
                        <button type="submit" className="btn-primary"
                            disabled={loading}>
                            {loading
                                ? <span className="spinner" />
                                : (isEdit ? 'Lưu thay đổi' : 'Thêm sách')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}