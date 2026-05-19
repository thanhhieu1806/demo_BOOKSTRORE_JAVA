package com.example.dem_login.controller;

import com.example.dem_login.service.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file) {
        Map<String, String> result = fileUploadService.uploadImage(file);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? 200 : 400).body(result);
    }

    @PostMapping("/upload-pdf")
    public ResponseEntity<Map<String, String>> uploadPdf(
            @RequestParam("file") MultipartFile file) {
        Map<String, String> result = fileUploadService.uploadPdf(file);
        boolean ok = "true".equals(result.get("success"));
        return ResponseEntity.status(ok ? 200 : 400).body(result);
    }
}