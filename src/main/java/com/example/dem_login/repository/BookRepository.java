package com.example.dem_login.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.dem_login.model.Book;

import java.util.List;

// Phải sử dụng JpaRepository để kế thừa các phương thức CRUD
public interface BookRepository extends JpaRepository<Book, Long> {
    // Tìm tất cả các sách có trạng thái ACTIVE
    List<Book> findByStatus(Book.BookStatus status);

    List<Book> findByTitleContainingIgnoreCase(String title);

    boolean existsByTitle(String title);
}
