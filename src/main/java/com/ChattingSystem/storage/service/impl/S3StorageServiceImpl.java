package com.ChattingSystem.storage.service.impl;

import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.StorageProperties;
import com.ChattingSystem.storage.dto.response.UploadResponse;
import com.ChattingSystem.storage.service.StorageService;
import com.ChattingSystem.storage.util.ImageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

@Slf4j
@Service
public class S3StorageServiceImpl implements StorageService {

    public static final long MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final StorageProperties storageProperties;

    public S3StorageServiceImpl(ObjectProvider<S3Client> s3ClientProvider,
                                StorageProperties storageProperties) {
        this.s3ClientProvider = s3ClientProvider;
        this.storageProperties = storageProperties;
    }

    @Override
    public UploadResponse uploadBlogImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_FILE);
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null || !storageProperties.isConfigured()) {
            throw new AppException(ErrorCode.STORAGE_NOT_CONFIGURED);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new AppException(ErrorCode.INVALID_FILE);
        }
        ImageType imageType = ImageType.detect(bytes)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_FILE));

        String key = buildKey(imageType);
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storageProperties.bucket())
                            .key(key)
                            .contentType(imageType.contentType())
                            .contentLength((long) bytes.length)
                            .cacheControl(CACHE_CONTROL)
                            .build(),
                    RequestBody.fromBytes(bytes));
        } catch (SdkException exception) {
            log.error("Upload to bucket {} failed for key {}", storageProperties.bucket(), key, exception);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }

        return new UploadResponse(storageProperties.publicBaseUrl() + "/" + key, key);
    }

    private static String buildKey(ImageType imageType) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return "blog/%04d/%02d/%s.%s".formatted(
                now.getYear(), now.getMonthValue(), UUID.randomUUID(), imageType.extension());
    }
}
