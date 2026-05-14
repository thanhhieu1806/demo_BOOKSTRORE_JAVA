package com.example.dem_login.service;

import com.example.dem_login.repository.BookRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import com.example.dem_login.model.Book;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class BookService {

    // bookRepository → làm việc với bảng book (lưu thông tin sách)
    private final BookRepository bookRepository;

    // constructor
    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    // lay tat ca thong tin sach
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    // lay thong tin sach theo id
    public Book getById(Long id) {
        return bookRepository.findById(id).orElse(null);
    }

    // them sach
    public Map<String, String> addBook(Book req) {
        if (req.getTitle() == null || req.getTitle().isBlank())
            return Map.of("success", "false", "message", "Tên sách không được để trống");

        if (req.getPrice() == null)
            return Map.of("success", "false", "message", "Giá sách không được để trống");

        req.setCreateDate(LocalDateTime.now());
        req.setUpdateDate(LocalDateTime.now());
        req.setStatus(Book.BookStatus.ACTIVE);
        bookRepository.save(req);
        return Map.of("success", "true", "message", "Thêm sách thành công");
    }

    // cap nhat thong tin sach
    public Map<String, String> updateBook(Long id, Book req) {
        return bookRepository.findById(id).map(book -> {
            // kiem tra thong tin cap nhat có thay đổi không
            if (req.getTitle() != null)// nếu title của req != null thì cập nhật title của book
                book.setTitle(req.getTitle());// cập nhật title của book
            if (req.getAuthor() != null)
                book.setAuthor(req.getAuthor());
            if (req.getCategory() != null)
                book.setCategory(req.getCategory());
            if (req.getPrice() != null)
                book.setPrice(req.getPrice());
            if (req.getQuantity() >= 0)
                book.setQuantity(req.getQuantity());
            if (req.getStatus() != null)
                book.setStatus(req.getStatus());
            if (req.getImageUrl() != null)
                book.setImageUrl(req.getImageUrl());

            book.setUpdateDate(LocalDateTime.now());
            bookRepository.save(book);
            return Map.of("success", "true", "message", "Cập nhật thành công!");
        }).orElseGet(() -> Map.of("success", "false", "message", "Không tìm thấy sách!"));
    }

    public Map<String, String> deleteBook(Long id) {
        if (!bookRepository.existsById(id))
            return Map.of("success", "false", "message", "không tìm thấy sách");
        bookRepository.deleteById(id);
        return Map.of("success", "true", "message", "xóa thành công!");

    }

}
