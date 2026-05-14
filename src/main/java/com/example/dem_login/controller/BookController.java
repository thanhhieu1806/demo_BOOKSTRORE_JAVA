package com.example.dem_login.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import com.example.dem_login.model.Book;
import com.example.dem_login.service.BookService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/books")
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookService.getAllBooks());
    }

    @GetMapping("/books/{id}")
    public ResponseEntity<Book> getById(@PathVariable Long id) {
        Book book = bookService.getById(id);
        return book != null ? ResponseEntity.ok(book) : ResponseEntity.notFound().build();
    }

    @PostMapping("/books")
    public ResponseEntity<Map<String, String>> addBook(@RequestBody Book req) {
        Map<String, String> result = bookService.addBook(req);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST).body(result);
    }

    @PutMapping("/books/{id}")
    // Annotation của Spring Boot: dùng cho HTTP PUT (thường dùng để UPDATE dữ liệu)
    // URL API sẽ là: /books/{id} (ví dụ: /books/1)

    public ResponseEntity<Map<String, String>> updateBook(
            @PathVariable Long id,
            @RequestBody Book req) {
        // @PathVariable Long id:-> Lấy giá trị id từ URL (ví dụ /books/1 thì id = 1)

        // @RequestBody Book req-> Nhận dữ liệu JSON từ request body và convert thành
        // object Book
        Map<String, String> result = bookService.updateBook(id, req);
        // Gọi service để xử lý logic update
        // Trả về Map<String, String>, ví dụ:
        // { "success": "true", "message": "Update thành công" }

        boolean ok = "true".equals(result.get("success"));
        // Lấy giá trị "success" từ Map
        // Nếu = "true" → ok = true
        // Nếu khác → ok = false
        // (dùng "true".equals(...) để tránh NullPointerException)

        return ResponseEntity
                .status(ok ? HttpStatus.OK : HttpStatus.BAD_REQUEST)
                // Nếu ok = true → trả HTTP 200 (OK)
                // Nếu ok = false → trả HTTP 400 (BAD_REQUEST)

                .body(result);
        // Trả dữ liệu result về client (JSON)
    }

    @DeleteMapping("/books/{id}")
    // @PathVariable : tham số được truyền từ đường dẫn
    public ResponseEntity<Map<String, String>> deleteBook(@PathVariable Long id) {
        return ResponseEntity.ok(bookService.deleteBook(id));
    }

}
