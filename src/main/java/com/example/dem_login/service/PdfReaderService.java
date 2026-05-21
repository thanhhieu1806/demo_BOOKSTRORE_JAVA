package com.example.dem_login.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class PdfReaderService {

    @Value("${app.upload.dir:D:/demologin/uploads}")
    // Lấy giá trị app.upload.dir trong application.properties
    // Nếu không có thì mặc định dùng D:/demologin/uploads
    private String uploadDir;

    // Hàm đọc toàn bộ nội dung text trong file PDF
    public String extractText(String filePath) {

        // Chuyển đường dẫn nhận vào thành đường dẫn file thật
        Path resolved = resolvePdfPath(filePath);

        // Nếu không tìm thấy file
        if (resolved == null) {

            // Trả về thông báo lỗi
            return "Không đọc được nội dung PDF: file không tồn tại (" + filePath + ")";
        }

        // try-with-resources:
        // Tự động đóng file PDF sau khi đọc xong
        try (PDDocument doc = Loader.loadPDF(resolved.toFile())) {

            // Tạo object dùng để đọc text từ PDF
            PDFTextStripper stripper = new PDFTextStripper();

            // Đọc toàn bộ text trong PDF
            String text = stripper.getText(doc);

            // Nếu PDF không có text
            // Ví dụ PDF scan bằng ảnh
            if (text == null || text.isBlank()) {

                // Trả về thông báo
                return "Không đọc được nội dung PDF: file rỗng hoặc scan ảnh.";
            }

            // Thay nhiều khoảng trắng thành 1 khoảng trắng
            // trim() để xóa khoảng trắng đầu cuối
            text = text.replaceAll("\\s+", " ").trim();

            // Nếu text quá dài hơn 8000 ký tự
            if (text.length() > 8000) {

                // Cắt bớt để tránh quá tải
                text = text.substring(0, 8000) + "\n...[Nội dung còn tiếp]";
            }

            // Trả về nội dung text đã xử lý
            return text;

        } catch (Exception e) {

            // Nếu có lỗi khi đọc PDF
            // Ví dụ file lỗi, file hỏng,...
            return "Không đọc được nội dung PDF: " + e.getMessage();
        }
    }

    /**
     * Hàm chuyển đường dẫn PDF trong DB thành đường dẫn file thật trên máy
     *
     * Ví dụ:
     * - D:/abc/test.pdf
     * - http://localhost:8080/uploads/pdfs/test.pdf
     * - /uploads/pdfs/test.pdf
     */
    public Path resolvePdfPath(String filePath) {

        // Nếu đường dẫn null hoặc rỗng
        if (filePath == null || filePath.isBlank()) {

            // Không xử lý được
            return null;
        }

        // Xóa khoảng trắng đầu cuối
        String raw = filePath.trim();

        try {

            // Nếu là URL web
            if (raw.startsWith("http://") || raw.startsWith("https://")) {

                // Tìm vị trí "/uploads/"
                int uploadsIdx = raw.indexOf("/uploads/");

                // Nếu có uploads
                if (uploadsIdx >= 0) {

                    // Lấy phần phía sau uploads/
                    String afterUploads = raw.substring(uploadsIdx + "/uploads/".length());

                    // Tạo path thật:
                    // uploadDir + phần phía sau
                    Path p = Paths.get(uploadDir, afterUploads.split("/"));

                    // Nếu file tồn tại
                    if (Files.isRegularFile(p)) {

                        // Trả về đường dẫn tuyệt đối
                        return p.toAbsolutePath();
                    }
                }

                // Không tìm thấy file
                return null;
            }

            // Nếu là đường dẫn trực tiếp
            Path direct = Paths.get(raw);

            // Nếu file tồn tại
            if (Files.isRegularFile(direct)) {

                // Trả về đường dẫn tuyệt đối
                return direct.toAbsolutePath();
            }

            // Chuẩn hóa dấu "\" thành "/"
            String normalized = raw.replace('\\', '/');

            // Tìm thư mục /pdfs/
            int pdfsIdx = normalized.indexOf("/pdfs/");

            // Nếu có /pdfs/
            if (pdfsIdx >= 0) {

                // Lấy tên file phía sau /pdfs/
                String fileName = normalized.substring(pdfsIdx + "/pdfs/".length());

                // Tạo đường dẫn:
                // uploadDir/pdfs/fileName
                Path byName = Paths.get(uploadDir, "pdfs", fileName);

                // Nếu file tồn tại
                if (Files.isRegularFile(byName)) {

                    // Trả về path tuyệt đối
                    return byName.toAbsolutePath();
                }
            }

            // Trường hợp cuối: chỉ lấy tên file
            Path byFileName = Paths.get(uploadDir,
                    "pdfs",
                    direct.getFileName().toString());

            // Nếu file tồn tại
            if (Files.isRegularFile(byFileName)) {

                // Trả về file
                return byFileName;
            }

        } catch (Exception ignored) {

            // Bỏ qua lỗi
        }

        // Không tìm được file
        return null;
    }

    // Kiểm tra file PDF có đọc được không
    public boolean isReadable(String filePath) {

        // Resolve đường dẫn
        Path p = resolvePdfPath(filePath);

        // Trả true nếu file tồn tại
        return p != null && Files.isRegularFile(p);
    }

    // Hàm đọc một khoảng trang trong PDF
    // Ví dụ đọc từ trang 1 đến trang 3
    public String extractPage(String filePath,
            int startPage,
            int endPage) {

        // Resolve đường dẫn thật
        Path resolved = resolvePdfPath(filePath);

        // Nếu không tìm thấy file
        if (resolved == null) {

            return "Không đọc được nội dung PDF " + filePath;
        }
        // PDDOCUMENT: class thư viện apache pdfbox dùng để mở và làm việc với file pdf
        // loadPDF: load file pdf
        try (PDDocument doc = Loader.loadPDF(resolved.toFile())) {

            // Object đọc text PDF
            PDFTextStripper stripper = new PDFTextStripper();

            // Trang bắt đầu đọc
            stripper.setStartPage(startPage);

            // Trang kết thúc đọc
            stripper.setEndPage(endPage);

            // Trả về text của các trang được chọn
            return stripper.getText(doc);

        } catch (Exception e) {

            // Nếu lỗi
            return "Không đọc được nội dung PDF " + e.getMessage();
        }
    }
}