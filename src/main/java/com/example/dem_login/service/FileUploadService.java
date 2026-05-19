package com.example.dem_login.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileUploadService {
    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.base-url}")
    private String baseUrl;

    // upload anhr bia sach
    public Map<String, String> uploadImage(MultipartFile file) {
        return uploadFile(file, "images",
                new String[] { "image/jpeg", "image/png", "image/webp", "image/gif" });
    }

    // upload file PDF sach
    public Map<String, String> uploadPdf(MultipartFile file) {
        return uploadFile(file, "pdfs",
                new String[] { "application/pdf", "application/x-pdf", "application/octet-stream" },
                true);
    }

    public Map<String, String> uploadFile(MultipartFile file, String subFolder, String[] allowedTypes) {
        return uploadFile(file, subFolder, allowedTypes, false);
    }

    public Map<String, String> uploadFile(MultipartFile file, String subFolder, String[] allowedTypes,
            boolean pdfOnly) {
        try {
            if (file == null || file.isEmpty()) {
                return Map.of("success", "false", "message", "File rỗng hoặc không hợp lệ!");
            }

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            String extension = getExtension(originalName).toLowerCase();

            if (pdfOnly && !".pdf".equals(extension)) {
                return Map.of("success", "false", "message", "Chỉ chấp nhận file PDF (.pdf)!");
            }

            // kiem tra loai file (Windows/Chrome đôi khi gửi application/octet-stream)
            String contentType = file.getContentType() != null ? file.getContentType() : "";
            boolean allowed = false;
            for (String type : allowedTypes) {
                if (type.equalsIgnoreCase(contentType)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed && pdfOnly && ".pdf".equals(extension)) {
                allowed = true;
            }
            if (!allowed && !pdfOnly && originalName.matches("(?i).+\\.(jpe?g|png|webp|gif)$")) {
                allowed = true;
            }
            if (!allowed) {
                return Map.of("success", "false", "message",
                        "Loại tệp không được hỗ trợ! (" + contentType + ")");
            }

            Path folderPath = Paths.get(uploadDir, subFolder);
            Files.createDirectories(folderPath);

            String fileName = UUID.randomUUID() + extension;
            Path targetPath = folderPath.resolve(fileName);

            try (var in = file.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String filePath = targetPath.toAbsolutePath().toString().replace('\\', '/');
            String fileUrl = baseUrl + "/uploads/" + subFolder + "/" + fileName;
            return Map.of(
                    "success", "true",
                    "message", "Upload thành công",
                    "url", fileUrl,
                    "fileName", fileName,
                    "filePath", filePath);
        } catch (IOException e) {
            return Map.of("success", "false", "message", "Lỗi upload file: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null)
            return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }
}
