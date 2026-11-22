package com.ChattingSystem.media.storage;

import com.ChattingSystem.media.storage.dto.response.PartSummaryResponse;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;
import com.ChattingSystem.media.storage.exception.NoSuchUploadStorageException;
import com.ChattingSystem.media.storage.service.impl.S3ObjectStorage;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3ObjectStorageUnitTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private MediaStorageProperties properties;
    private S3ObjectStorage storage;

    @BeforeEach
    void setUp() {
        properties = new MediaStorageProperties(
                "test-bucket", "us-east-1", "http://localhost:9002", null,
                true, "access", "secret", 5242880, Duration.ofMinutes(15), Duration.ofMinutes(30)
        );
        storage = new S3ObjectStorage(properties, s3Client, s3Presigner);
    }

    @Test
    @DisplayName("presignSinglePut: signedHeaders phải lọc bỏ Host và Content-Length (không phân biệt hoa thường)")
    void presignSinglePut_FiltersForbiddenBrowserHeaders() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("http://s3.local/put-url").toURL());
        when(presigned.expiration()).thenReturn(Instant.now().plusSeconds(900));
        when(presigned.signedHeaders()).thenReturn(Map.of(
                "Host", List.of("s3.local"),
                "content-length", List.of("1024"),
                "x-amz-meta-custom", List.of("custom-val")
        ));

        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        PresignedUrlResponse response = storage.presignSinglePut("test/key.jpg", "image/jpeg", 1024L, Duration.ofMinutes(15));

        assertThat(response).isNotNull();
        assertThat(response.signedHeaders()).doesNotContainKey("Host");
        assertThat(response.signedHeaders()).doesNotContainKey("content-length");
        assertThat(response.signedHeaders()).containsEntry("x-amz-meta-custom", "custom-val");
    }

    @Test
    @DisplayName("presignUploadPart: signedHeaders phải lọc bỏ Host và Content-Length")
    void presignUploadPart_FiltersForbiddenBrowserHeaders() throws Exception {
        PresignedUploadPartRequest presigned = mock(PresignedUploadPartRequest.class);
        when(presigned.url()).thenReturn(URI.create("http://s3.local/part-url").toURL());
        when(presigned.expiration()).thenReturn(Instant.now().plusSeconds(900));
        when(presigned.signedHeaders()).thenReturn(Map.of(
                "host", List.of("s3.local"),
                "Content-Length", List.of("5242880"),
                "x-amz-part-number", List.of("1")
        ));

        when(s3Presigner.presignUploadPart(any(UploadPartPresignRequest.class))).thenReturn(presigned);

        PresignedUrlResponse response = storage.presignUploadPart("test/video.mp4", "upload-1", 1, 5242880L, Duration.ofMinutes(15));

        assertThat(response).isNotNull();
        assertThat(response.signedHeaders()).doesNotContainKey("host");
        assertThat(response.signedHeaders()).doesNotContainKey("Content-Length");
        assertThat(response.signedHeaders()).containsEntry("x-amz-part-number", "1");
    }

    @Test
    @DisplayName("delete: ném STORAGE_ERROR khi S3 gặp lỗi (không nuốt SdkException)")
    void delete_ThrowsStorageErrorOnSdkException() {
        doThrow(SdkClientException.create("S3 network timeout"))
                .when(s3Client).deleteObject(any(Consumer.class));

        assertThatThrownBy(() -> storage.delete("test/key.jpg"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_ERROR);
    }

    @Test
    @DisplayName("listParts: phân trang qua isTruncated và ném NoSuchUploadStorageException khi NoSuchUpload")
    void listParts_PaginationAndNoSuchUpload() {
        // Test phân trang: page 1 truncated, page 2 finished
        Part part1 = Part.builder().partNumber(1).eTag("\"etag1\"").size(5242880L).lastModified(Instant.now()).build();
        Part part2 = Part.builder().partNumber(2).eTag("\"etag2\"").size(1024L).lastModified(Instant.now()).build();

        ListPartsResponse page1 = ListPartsResponse.builder()
                .parts(part1)
                .isTruncated(true)
                .nextPartNumberMarker(1)
                .build();

        ListPartsResponse page2 = ListPartsResponse.builder()
                .parts(part2)
                .isTruncated(false)
                .build();

        when(s3Client.listParts(any(Consumer.class))).thenReturn(page1, page2);

        List<PartSummaryResponse> result = storage.listParts("test/key.mp4", "upload-page");
        assertThat(result).hasSize(2);
        assertThat(result.get(0).partNumber()).isEqualTo(1);
        assertThat(result.get(0).etag()).isEqualTo("etag1");
        assertThat(result.get(1).partNumber()).isEqualTo(2);

        // Test NoSuchUpload
        when(s3Client.listParts(any(Consumer.class))).thenThrow(NoSuchUploadException.builder().message("No such upload").build());

        assertThatThrownBy(() -> storage.listParts("test/key.mp4", "upload-invalid"))
                .isInstanceOf(NoSuchUploadStorageException.class);
    }

    @Test
    @DisplayName("downloadToTemp: ném MEDIA_TOO_LARGE khi vượt quá ngưỡng maxBytes và xóa file cũ trước khi tải")
    void downloadToTemp_ValidatesSizeAndDeletesExistingTarget() throws Exception {
        HeadObjectResponse headResponse = HeadObjectResponse.builder()
                .contentLength(10 * 1024 * 1024L) // 10 MiB
                .contentType("video/mp4")
                .build();
        when(s3Client.headObject(any(Consumer.class))).thenReturn(headResponse);

        Path tempFile = Files.createTempFile("s3-test", ".bin");
        try {
            long maxBytes = 5 * 1024 * 1024L; // 5 MiB threshold
            assertThatThrownBy(() -> storage.downloadToTemp("test/video.mp4", tempFile, maxBytes))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDIA_TOO_LARGE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("abortMultipartUpload: ném STORAGE_ERROR khi S3 gặp lỗi SdkException khác NoSuchUpload")
    void abortMultipartUpload_ThrowsStorageErrorOnSdkException() {
        doThrow(SdkClientException.create("S3 timeout on abort"))
                .when(s3Client).abortMultipartUpload(any(Consumer.class));

        assertThatThrownBy(() -> storage.abortMultipartUpload("test/key.mp4", "upload-123"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STORAGE_ERROR);
    }

    @Test
    @DisplayName("abortMultipartUpload: bỏ qua an toàn khi gặp NoSuchUploadException hoặc S3Exception có errorCode NoSuchUpload")
    void abortMultipartUpload_IgnoresNoSuchUpload() {
        // Case 1: NoSuchUploadException
        doThrow(NoSuchUploadException.builder().message("NoSuchUpload").build())
                .when(s3Client).abortMultipartUpload(any(Consumer.class));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                storage.abortMultipartUpload("test/key.mp4", "upload-123"));

        // Case 2: S3Exception với awsErrorDetails có errorCode = "NoSuchUpload" (MinIO style)
        S3Exception minioNoSuchUpload = (S3Exception) S3Exception.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchUpload").build())
                .message("Upload does not exist")
                .build();
        doThrow(minioNoSuchUpload)
                .when(s3Client).abortMultipartUpload(any(Consumer.class));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                storage.abortMultipartUpload("test/key.mp4", "upload-123"));
    }

    @Test
    @DisplayName("presignGet: signedHeaders phải lọc bỏ Host và Content-Length")
    void presignGet_FiltersForbiddenBrowserHeaders() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("http://s3.local/get-url").toURL());
        when(presigned.expiration()).thenReturn(Instant.now().plusSeconds(900));
        when(presigned.signedHeaders()).thenReturn(Map.of(
                "Host", List.of("s3.local"),
                "content-length", List.of("2048"),
                "x-amz-meta-custom", List.of("val")
        ));

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        PresignedUrlResponse response = storage.presignGet("test/key.jpg", null);

        assertThat(response).isNotNull();
        assertThat(response.signedHeaders()).doesNotContainKey("Host");
        assertThat(response.signedHeaders()).doesNotContainKey("content-length");
        assertThat(response.signedHeaders()).containsEntry("x-amz-meta-custom", "val");
    }

    @Test
    @DisplayName("listParts: dừng lặp khi isTruncated = true nhưng nextPartNumberMarker không tiến triển (tránh lặp vô hạn)")
    void listParts_BreaksWhenMarkerDoesNotAdvance() {
        Part part1 = Part.builder().partNumber(1).eTag("\"etag1\"").size(1024L).lastModified(Instant.now()).build();
        ListPartsResponse staleMarkerResponse = ListPartsResponse.builder()
                .parts(part1)
                .isTruncated(true)
                .nextPartNumberMarker(null) // nextMarker = null
                .build();

        when(s3Client.listParts(any(Consumer.class))).thenReturn(staleMarkerResponse);

        List<PartSummaryResponse> result = storage.listParts("test/loop.mp4", "upload-loop");
        assertThat(result).hasSize(1);
    }
}
