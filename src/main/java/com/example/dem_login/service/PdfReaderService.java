package com.example.dem_login.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class PdfReaderService {

    @Value("${app.upload.dir:D:/demologin/uploads}")
    private String uploadDir;

    public String extractText(String filePath) {
        Path resolved = resolvePdfPath(filePath);
        if (resolved == null) {
            return "Không đọc được nội dung PDF: file không tồn tại (" + filePath + ")";
        }
        try (PDDocument doc = Loader.loadPDF(resolved.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                return "Không đọc được nội dung PDF: file rỗng hoặc scan ảnh.";
            }
            text = text.replaceAll("\\s+", " ").trim();
            if (text.length() > 8000) {
                text = text.substring(0, 8000) + "\n...[Nội dung còn tiếp]";
            }
            return text;
        } catch (Exception e) {
            return "Không đọc được nội dung PDF: " + e.getMessage();
        }
    }

    /** Chuyển pdf_path DB (D:/... hoặc URL /uploads/...) thành đường dẫn file thật */
    public Path resolvePdfPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        String raw = filePath.trim();

        try {
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                int uploadsIdx = raw.indexOf("/uploads/");
                if (uploadsIdx >= 0) {
                    String afterUploads = raw.substring(uploadsIdx + "/uploads/".length());
                    Path p = Paths.get(uploadDir, afterUploads.split("/"));
                    if (Files.isRegularFile(p)) {
                        return p.toAbsolutePath();
                    }
                }
                return null;
            }

            Path direct = Paths.get(raw);
            if (Files.isRegularFile(direct)) {
                return direct.toAbsolutePath();
            }

            String normalized = raw.replace('\\', '/');
            int pdfsIdx = normalized.indexOf("/pdfs/");
            if (pdfsIdx >= 0) {
                String fileName = normalized.substring(pdfsIdx + "/pdfs/".length());
                Path byName = Paths.get(uploadDir, "pdfs", fileName);
                if (Files.isRegularFile(byName)) {
                    return byName.toAbsolutePath();
                }
            }

            Path byFileName = Paths.get(uploadDir, "pdfs", direct.getFileName().toString());
            if (Files.isRegularFile(byFileName)) {
                return byFileName;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public boolean isReadable(String filePath) {
        Path p = resolvePdfPath(filePath);
        return p != null && Files.isRegularFile(p);
    }

    public String extractPage(String filePath, int startPage, int endPage) {
        Path resolved = resolvePdfPath(filePath);
        if (resolved == null) {
            return "Không đọc được nội dung PDF " + filePath;
        }
        try (PDDocument doc = Loader.loadPDF(resolved.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            return stripper.getText(doc);
        } catch (Exception e) {
            return "Không đọc được nội dung PDF " + e.getMessage();
        }
    }
}
