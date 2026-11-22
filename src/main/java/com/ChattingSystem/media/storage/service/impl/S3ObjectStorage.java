package com.ChattingSystem.media.storage.service.impl;

import com.ChattingSystem.media.storage.dto.request.CompletedPartRequest;
import com.ChattingSystem.media.storage.dto.request.PresignGetRequest;
import com.ChattingSystem.media.storage.dto.response.ObjectMetadataResponse;
import com.ChattingSystem.media.storage.dto.response.PartSummaryResponse;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;
import com.ChattingSystem.media.storage.exception.NoSuchUploadStorageException;
import com.ChattingSystem.media.storage.service.ObjectStorage;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final MediaStorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3ObjectStorage(MediaStorageProperties properties) {
        this.properties = properties;

        if (properties.isConfigured()) {
            ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofSeconds(60))
                    .apiCallAttemptTimeout(Duration.ofSeconds(30))
                    .build();

            S3ClientBuilder builder = S3Client.builder()
                    .region(Region.of(properties.region()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(properties.pathStyle())
                            .build())
                    .overrideConfiguration(overrideConfig)
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);

            if (StringUtils.hasText(properties.endpoint())) {
                builder.endpointOverride(URI.create(properties.endpoint().trim()));
            }
            this.s3Client = builder.build();

            S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                    .region(Region.of(properties.region()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(properties.pathStyle())
                            .build());

            String signHost = StringUtils.hasText(properties.publicEndpoint())
                    ? properties.publicEndpoint().trim()
                    : properties.endpoint();
            if (StringUtils.hasText(signHost)) {
                presignerBuilder.endpointOverride(URI.create(signHost.trim()));
            }
            this.s3Presigner = presignerBuilder.build();
        } else {
            this.s3Client = null;
            this.s3Presigner = null;
        }
    }

    // Constructor visible for testing
    public S3ObjectStorage(MediaStorageProperties properties, S3Client s3Client, S3Presigner s3Presigner) {
        this.properties = properties;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public String createMultipartUpload(String key, String contentType) {
        requireConfigured();
        try {
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(b -> b
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(contentType));
            return response.uploadId();
        } catch (SdkException e) {
            log.error("Failed to create multipart upload for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public PresignedUrlResponse presignUploadPart(String key, String uploadId, int partNumber, long contentLength, Duration ttl) {
        requireConfigured();
        try {
            Duration duration = (ttl != null) ? ttl : properties.putUrlTtl();
            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(duration)
                    .uploadPartRequest(b -> b
                            .bucket(properties.bucket())
                            .key(key)
                            .uploadId(uploadId)
                            .partNumber(partNumber)
                            .contentLength(contentLength))
                    .build();

            PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(presignRequest);
            return new PresignedUrlResponse(
                    presigned.url().toString(),
                    "PUT",
                    filterSignedHeaders(presigned.signedHeaders()),
                    presigned.expiration()
            );
        } catch (SdkException e) {
            log.error("Failed to presign upload part for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public List<PartSummaryResponse> listParts(String key, String uploadId) {
        requireConfigured();
        try {
            List<PartSummaryResponse> result = new ArrayList<>();
            Integer partNumberMarker = null;

            while (true) {
                final Integer marker = partNumberMarker;
                ListPartsResponse response = s3Client.listParts(b -> {
                    b.bucket(properties.bucket()).key(key).uploadId(uploadId);
                    if (marker != null) {
                        b.partNumberMarker(marker);
                    }
                });

                for (Part p : response.parts()) {
                    result.add(new PartSummaryResponse(
                            p.partNumber(),
                            p.eTag() != null ? p.eTag().replace("\"", "") : "",
                            p.size(),
                            p.lastModified()
                    ));
                }

                if (Boolean.TRUE.equals(response.isTruncated())) {
                    Integer nextMarker = response.nextPartNumberMarker();
                    if (nextMarker == null || Objects.equals(nextMarker, marker)) {
                        log.warn("listParts for key {} is truncated but nextPartNumberMarker is invalid ({}), breaking loop", key, nextMarker);
                        break;
                    }
                    partNumberMarker = nextMarker;
                } else {
                    break;
                }
            }

            return result;
        } catch (SdkException e) {
            if (isNoSuchUpload(e)) {
                log.warn("NoSuchUpload encountered when listing parts for key {}, throwing NoSuchUploadStorageException", key);
                throw new NoSuchUploadStorageException("Phiên tải lên không tồn tại hoặc đã được hoàn tất: " + uploadId, e);
            }
            log.error("Failed to list parts for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void completeMultipartUpload(String key, String uploadId, List<CompletedPartRequest> parts) {
        requireConfigured();
        try {
            List<CompletedPart> completedParts = parts.stream()
                    .map(p -> CompletedPart.builder()
                            .partNumber(p.partNumber())
                            .eTag(p.etag())
                            .build())
                    .toList();

            s3Client.completeMultipartUpload(b -> b
                    .bucket(properties.bucket())
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build()));
        } catch (SdkException e) {
            if (isNoSuchUpload(e)) {
                log.warn("NoSuchUpload on complete for key {}. Checking if file exists via headObject", key);
                Optional<ObjectMetadataResponse> meta = head(key);
                if (meta.isEmpty()) {
                    throw new AppException(ErrorCode.STORAGE_ERROR);
                }
                return;
            }
            log.error("Failed to complete multipart upload for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        requireConfigured();
        try {
            s3Client.abortMultipartUpload(b -> b
                    .bucket(properties.bucket())
                    .key(key)
                    .uploadId(uploadId));
        } catch (SdkException e) {
            if (isNoSuchUpload(e)) {
                log.debug("NoSuchUpload on abort for key {}, ignoring", key);
                return;
            }
            log.error("Failed to abort multipart upload for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR, "Không thể hủy phiên tải lên trên dịch vụ lưu trữ");
        }
    }

    @Override
    public PresignedUrlResponse presignSinglePut(String key, String contentType, long contentLength, Duration ttl) {
        requireConfigured();
        try {
            Duration duration = (ttl != null) ? ttl : properties.putUrlTtl();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .putObjectRequest(b -> b
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength(contentLength))
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
            return new PresignedUrlResponse(
                    presigned.url().toString(),
                    "PUT",
                    filterSignedHeaders(presigned.signedHeaders()),
                    presigned.expiration()
            );
        } catch (SdkException e) {
            log.error("Failed to presign single put for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public Optional<ObjectMetadataResponse> head(String key) {
        requireConfigured();
        try {
            HeadObjectResponse response = s3Client.headObject(b -> b
                    .bucket(properties.bucket())
                    .key(key));

            return Optional.of(new ObjectMetadataResponse(
                    response.contentLength(),
                    response.contentType(),
                    response.eTag() != null ? response.eTag().replace("\"", "") : ""
            ));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            log.error("S3 error when calling head for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        } catch (SdkException e) {
            log.error("Sdk error when calling head for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public byte[] readRange(String key, long startByte, long endByte) {
        requireConfigured();
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObject(
                    b -> b.bucket(properties.bucket())
                            .key(key)
                            .range("bytes=" + startByte + "-" + endByte),
                    ResponseTransformer.toBytes()
            );
            return response.asByteArray();
        } catch (S3Exception e) {
            if (e.statusCode() == 416 || "InvalidRange".equals(e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null)) {
                log.warn("Range 416 / InvalidRange for key {}: returning empty byte array", key);
                return new byte[0];
            }
            log.error("S3 error when reading range for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        } catch (SdkException e) {
            log.error("Sdk error when reading range for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public PresignedUrlResponse presignGet(String key, PresignGetRequest request) {
        requireConfigured();
        try {
            Duration duration = (request != null && request.ttl() != null) ? request.ttl() : properties.getUrlTtl();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(duration)
                    .getObjectRequest(b -> {
                        b.bucket(properties.bucket()).key(key);
                        if (request != null) {
                            if (StringUtils.hasText(request.responseContentType())) {
                                b.responseContentType(request.responseContentType());
                            }
                            if (StringUtils.hasText(request.contentDisposition())) {
                                b.responseContentDisposition(request.contentDisposition());
                            }
                            if (StringUtils.hasText(request.responseCacheControl())) {
                                b.responseCacheControl(request.responseCacheControl());
                            }
                        }
                    })
                    .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
            return new PresignedUrlResponse(
                    presigned.url().toString(),
                    "GET",
                    filterSignedHeaders(presigned.signedHeaders()),
                    presigned.expiration()
            );
        } catch (SdkException e) {
            log.error("Failed to presign get for key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String key) {
        requireConfigured();
        try {
            s3Client.deleteObject(b -> b.bucket(properties.bucket()).key(key));
        } catch (SdkException e) {
            log.error("Failed to delete object key {}: {}", key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR, "Không thể xóa tệp trên dịch vụ lưu trữ");
        }
    }

    @Override
    public Path downloadToTemp(String key, Path targetPath, long maxBytes) {
        requireConfigured();
        try {
            Optional<ObjectMetadataResponse> meta = head(key);
            if (meta.isEmpty()) {
                throw new AppException(ErrorCode.STORAGE_ERROR, "Tệp không tồn tại để tải về");
            }
            if (meta.get().contentLength() > maxBytes) {
                log.error("File size {} exceeds maxBytes threshold {} for key {}", meta.get().contentLength(), maxBytes, key);
                throw new AppException(ErrorCode.MEDIA_TOO_LARGE, "Dung lượng tệp vượt ngưỡng tối đa cho phép xử lý");
            }

            Files.deleteIfExists(targetPath);
            s3Client.getObject(b -> b.bucket(properties.bucket()).key(key), targetPath);
            return targetPath;
        } catch (AppException e) {
            try {
                Files.deleteIfExists(targetPath);
            } catch (Exception ex) {
                log.warn("Failed to delete temp file {} on error: {}", targetPath, ex.getMessage());
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to download object {} to temp: {}", key, e.getMessage(), e);
            try {
                Files.deleteIfExists(targetPath);
            } catch (Exception ex) {
                log.warn("Failed to delete temp file {} on error: {}", targetPath, ex.getMessage());
            }
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void putFile(String key, Path filePath, String contentType) {
        requireConfigured();
        try {
            s3Client.putObject(
                    b -> b.bucket(properties.bucket()).key(key).contentType(contentType),
                    filePath
            );
        } catch (SdkException e) {
            log.error("Failed to put file {} to key {}: {}", filePath, key, e.getMessage(), e);
            throw new AppException(ErrorCode.STORAGE_ERROR);
        }
    }

    @PreDestroy
    public void close() {
        if (s3Client != null) {
            try {
                s3Client.close();
            } catch (Exception e) {
                log.warn("Error closing s3Client: {}", e.getMessage());
            }
        }
        if (s3Presigner != null) {
            try {
                s3Presigner.close();
            } catch (Exception e) {
                log.warn("Error closing s3Presigner: {}", e.getMessage());
            }
        }
    }

    private static boolean isNoSuchUpload(Exception e) {
        if (e instanceof NoSuchUploadException) {
            return true;
        }
        if (e instanceof S3Exception s3Ex) {
            return s3Ex.awsErrorDetails() != null
                    && "NoSuchUpload".equalsIgnoreCase(s3Ex.awsErrorDetails().errorCode());
        }
        return false;
    }

    private static Map<String, String> filterSignedHeaders(Map<String, List<String>> rawSignedHeaders) {
        Map<String, String> signedHeaders = new LinkedHashMap<>();
        if (rawSignedHeaders != null) {
            rawSignedHeaders.forEach((k, v) -> {
                if (!"host".equalsIgnoreCase(k) && !"content-length".equalsIgnoreCase(k)) {
                    signedHeaders.put(k, String.join(",", v));
                }
            });
        }
        return signedHeaders;
    }

    private void requireConfigured() {
        if (!properties.isConfigured() || s3Client == null || s3Presigner == null) {
            throw new AppException(ErrorCode.STORAGE_NOT_CONFIGURED);
        }
    }
}
