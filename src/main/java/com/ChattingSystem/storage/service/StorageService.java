package com.ChattingSystem.storage.service;

import com.ChattingSystem.storage.dto.response.UploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /** Validates (jpeg/png/webp/avif/gif by magic bytes, ≤ 5 MB) and stores under {@code blog/yyyy/MM/uuid.ext}. */
    UploadResponse uploadBlogImage(MultipartFile file);
}
