package com.ChattingSystem.storage.controller;

import com.ChattingSystem.shared.response.ApiResponse;
import com.ChattingSystem.storage.dto.response.UploadResponse;
import com.ChattingSystem.storage.service.StorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Requires {@code SCOPE_ADMIN} (see {@code SecurityConfig}). */
@RestController
@RequestMapping("/admin/uploads")
public class AdminUploadController {

    private final StorageService storageService;

    public AdminUploadController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> uploadImage(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tải ảnh lên thành công",
                storageService.uploadBlogImage(file)));
    }
}
