package com.ChattingSystem.media.storage;

import com.ChattingSystem.media.storage.dto.request.CompletedPartRequest;
import com.ChattingSystem.media.storage.dto.request.PresignGetRequest;
import com.ChattingSystem.media.storage.dto.response.ObjectMetadataResponse;
import com.ChattingSystem.media.storage.dto.response.PartSummaryResponse;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;
import com.ChattingSystem.media.storage.service.impl.S3ObjectStorage;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.properties.MediaStorageProperties;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestMethodOrder(MethodOrderer.MethodName.class)
public class StorageContractIT {

    private static final Logger log = LoggerFactory.getLogger(StorageContractIT.class);

    private static String endpoint;
    private static String bucket;
    private static String accessKey;
    private static String secretKey;
    private static S3ObjectStorage storage;
    private static HttpClient httpClient;
    private static boolean minioReachable = false;

    private final List<String> createdKeys = new ArrayList<>();

    @BeforeAll
    static void setUpAll() {
        endpoint = System.getenv().getOrDefault("MEDIA_STORAGE_ENDPOINT", "http://localhost:9002");
        bucket = System.getenv().getOrDefault("MEDIA_STORAGE_BUCKET", "chat-media");
        accessKey = System.getenv().getOrDefault("MEDIA_STORAGE_ACCESS_KEY", "admin");
        secretKey = System.getenv().getOrDefault("MEDIA_STORAGE_SECRET_KEY", "admin123");

        URI uri = URI.create(endpoint);
        int port = uri.getPort() != -1 ? uri.getPort() : 80;
        String host = uri.getHost();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            minioReachable = true;
            log.info("MinIO is reachable at {}:{}", host, port);
        } catch (IOException e) {
            log.warn("MinIO is NOT reachable at {}:{}. Contract tests will be skipped unless MinIO is running.", host, port);
            minioReachable = false;
        }

        MediaStorageProperties properties = new MediaStorageProperties(
                bucket,
                "us-east-1",
                endpoint,
                endpoint,
                true,
                accessKey,
                secretKey,
                5242880,
                Duration.ofMinutes(15),
                Duration.ofMinutes(30)
        );

        storage = new S3ObjectStorage(properties);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @AfterAll
    static void tearDownAll() {
        if (storage != null) {
            storage.close();
        }
    }

    @BeforeEach
    void checkReachability() {
        assumeTrue(minioReachable, "Bỏ qua test vì container MinIO chưa chạy tại " + endpoint);
    }

    @AfterEach
    void cleanUpObjects() {
        for (String key : createdKeys) {
            try {
                storage.delete(key);
            } catch (Exception ignored) {
            }
        }
        createdKeys.clear();
    }

    @Test
    @DisplayName("(1) Preflight CORS: OPTIONS request từ Frontend origin (http://localhost:3000)")
    void test01_CorsPreflight() throws Exception {
        String key = "contract-test/cors-" + UUID.randomUUID() + ".txt";
        createdKeys.add(key);

        PresignedUrlResponse presigned = storage.presignSinglePut(key, "text/plain", 10, Duration.ofMinutes(5));

        HttpRequest preflightRequest = HttpRequest.newBuilder()
                .uri(URI.create(presigned.url()))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "content-type,content-length")
                .build();

        HttpResponse<Void> response = httpClient.send(preflightRequest, HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isIn(200, 204);

        Optional<String> allowOrigin = response.headers().firstValue("Access-Control-Allow-Origin");
        assertThat(allowOrigin).isPresent();
        assertThat(allowOrigin.get()).satisfiesAnyOf(
                origin -> assertThat(origin).isEqualTo("http://localhost:3000"),
                origin -> assertThat(origin).isEqualTo("*")
        );
    }

    @Test
    @DisplayName("(2) PUT part trả về header ETag và hỗ trợ expose ETag cho client")
    void test02_PutReturnsEtag() throws Exception {
        String key = "contract-test/etag-" + UUID.randomUUID() + ".bin";
        createdKeys.add(key);

        byte[] data = "Hello S3 Storage Contract Test".getBytes(StandardCharsets.UTF_8);
        PresignedUrlResponse presigned = storage.presignSinglePut(key, "application/octet-stream", data.length, Duration.ofMinutes(5));

        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(presigned.url()))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Length", String.valueOf(data.length))
                .header("Origin", "http://localhost:3000")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();

        HttpResponse<String> response = httpClient.send(putRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);

        Optional<String> etag = response.headers().firstValue("ETag");
        assertThat(etag).isPresent();
        assertThat(etag.get()).isNotBlank();
    }

    @Test
    @DisplayName("(3) Multipart upload: create -> PUT 2 parts (5 MiB + 10 KiB) -> listParts -> complete -> head")
    void test03_MultipartUploadFullFlow() throws Exception {
        String key = "contract-test/multipart-" + UUID.randomUUID() + ".mp4";
        createdKeys.add(key);
        String contentType = "video/mp4";

        // 1. Create multipart
        String uploadId = storage.createMultipartUpload(key, contentType);
        assertThat(uploadId).isNotBlank();

        // Part 1: 5 MiB (5242880 bytes - ngưỡng tối thiểu của S3 cho các part)
        int part1Size = 5 * 1024 * 1024;
        byte[] part1Data = new byte[part1Size];
        Arrays.fill(part1Data, (byte) 'A');

        // Part 2: 10 KiB (10240 bytes)
        int part2Size = 10 * 1024;
        byte[] part2Data = new byte[part2Size];
        Arrays.fill(part2Data, (byte) 'B');

        // 2. Ký và PUT Part 1
        PresignedUrlResponse part1Presigned = storage.presignUploadPart(key, uploadId, 1, part1Size, Duration.ofMinutes(15));
        HttpRequest putPart1 = HttpRequest.newBuilder()
                .uri(URI.create(part1Presigned.url()))
                .header("Content-Length", String.valueOf(part1Size))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(part1Data))
                .build();
        HttpResponse<Void> resp1 = httpClient.send(putPart1, HttpResponse.BodyHandlers.discarding());
        assertThat(resp1.statusCode()).isEqualTo(200);
        String etag1 = resp1.headers().firstValue("ETag").orElseThrow().replace("\"", "");

        // 3. Ký và PUT Part 2
        PresignedUrlResponse part2Presigned = storage.presignUploadPart(key, uploadId, 2, part2Size, Duration.ofMinutes(15));
        HttpRequest putPart2 = HttpRequest.newBuilder()
                .uri(URI.create(part2Presigned.url()))
                .header("Content-Length", String.valueOf(part2Size))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(part2Data))
                .build();
        HttpResponse<Void> resp2 = httpClient.send(putPart2, HttpResponse.BodyHandlers.discarding());
        assertThat(resp2.statusCode()).isEqualTo(200);
        String etag2 = resp2.headers().firstValue("ETag").orElseThrow().replace("\"", "");

        // 4. List parts và đối chiếu kích thước
        List<PartSummaryResponse> parts = storage.listParts(key, uploadId);
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).partNumber()).isEqualTo(1);
        assertThat(parts.get(0).sizeBytes()).isEqualTo(part1Size);
        assertThat(parts.get(0).etag()).isEqualTo(etag1);
        assertThat(parts.get(1).partNumber()).isEqualTo(2);
        assertThat(parts.get(1).sizeBytes()).isEqualTo(part2Size);
        assertThat(parts.get(1).etag()).isEqualTo(etag2);

        // 5. Complete multipart
        List<CompletedPartRequest> completedParts = List.of(
                new CompletedPartRequest(1, etag1),
                new CompletedPartRequest(2, etag2)
        );
        storage.completeMultipartUpload(key, uploadId, completedParts);

        // 6. HeadObject xác minh file hoàn chỉnh
        Optional<ObjectMetadataResponse> meta = storage.head(key);
        assertThat(meta).isPresent();
        assertThat(meta.get().contentLength()).isEqualTo((long) part1Size + part2Size);
        assertThat(meta.get().contentType()).isEqualTo(contentType);
    }

    @Test
    @DisplayName("(4) Complete lặp lại (idempotent) và listParts sau khi hoàn tất")
    void test04_CompleteTwiceAndListPartsAfterComplete() throws Exception {
        String key = "contract-test/repeat-complete-" + UUID.randomUUID() + ".bin";
        createdKeys.add(key);
        String contentType = "application/octet-stream";

        String uploadId = storage.createMultipartUpload(key, contentType);
        int partSize = 5 * 1024 * 1024;
        byte[] data = new byte[partSize];

        PresignedUrlResponse partPresigned = storage.presignUploadPart(key, uploadId, 1, partSize, Duration.ofMinutes(5));
        HttpRequest putPart = HttpRequest.newBuilder()
                .uri(URI.create(partPresigned.url()))
                .header("Content-Length", String.valueOf(partSize))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();
        HttpResponse<Void> resp = httpClient.send(putPart, HttpResponse.BodyHandlers.discarding());
        String etag = resp.headers().firstValue("ETag").orElseThrow().replace("\"", "");

        List<CompletedPartRequest> completedParts = List.of(new CompletedPartRequest(1, etag));

        // Lần 1: Thành công
        storage.completeMultipartUpload(key, uploadId, completedParts);
        assertThat(storage.head(key)).isPresent();

        // Lần 2: S3ObjectStorage xử lý NoSuchUploadException -> check head(key) -> an toàn không văng lỗi
        assertThatCode(() -> storage.completeMultipartUpload(key, uploadId, completedParts))
                .doesNotThrowAnyException();

        // listParts sau khi complete: Ném NoSuchUploadStorageException do uploadId đã hoàn tất
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.listParts(key, uploadId))
                .isInstanceOf(com.ChattingSystem.media.storage.exception.NoSuchUploadStorageException.class);
    }

    @Test
    @DisplayName("(5) URL do S3Presigner sinh không chứa checksum params (x-amz-checksum / x-amz-sdk-checksum-algorithm)")
    void test05_NoChecksumAlgorithmInPresignedUrls() {
        String key = "contract-test/checksum-" + UUID.randomUUID() + ".jpg";

        PresignedUrlResponse singlePut = storage.presignSinglePut(key, "image/jpeg", 2048L, Duration.ofMinutes(10));
        assertThat(singlePut.url())
                .doesNotContain("x-amz-checksum")
                .doesNotContain("x-amz-sdk-checksum-algorithm");

        PresignedUrlResponse partPut = storage.presignUploadPart(key, "dummy-upload-id", 1, 5242880L, Duration.ofMinutes(10));
        assertThat(partPut.url())
                .doesNotContain("x-amz-checksum")
                .doesNotContain("x-amz-sdk-checksum-algorithm");

        PresignedUrlResponse getUrl = storage.presignGet(key, new PresignGetRequest(Duration.ofMinutes(10), "image/jpeg", null, null));
        assertThat(getUrl.url())
                .doesNotContain("x-amz-checksum")
                .doesNotContain("x-amz-sdk-checksum-algorithm");
    }

    @Test
    @DisplayName("(6) Presigned PUT SINGLE: signedHeaders và từ chối khi PUT sai Content-Length / Content-Type")
    void test06_PresignSinglePutHeadersAndMismatchRejection() throws Exception {
        String key = "contract-test/single-put-mismatch-" + UUID.randomUUID() + ".txt";
        createdKeys.add(key);

        long declaredLength = 100L;
        String declaredType = "text/plain";

        PresignedUrlResponse presigned = storage.presignSinglePut(key, declaredType, declaredLength, Duration.ofMinutes(5));

        log.info("Single PUT signedHeaders: {}", presigned.signedHeaders());
        assertThat(presigned.signedHeaders()).containsKey("content-length");
        assertThat(presigned.signedHeaders()).containsKey("content-type");

        // 1. Thử PUT sai Content-Length (khai 100 nhưng gửi 50 bytes) -> Storage từ chối
        byte[] mismatchData = new byte[50];
        HttpRequest badLengthRequest = HttpRequest.newBuilder()
                .uri(URI.create(presigned.url()))
                .header("Content-Type", declaredType)
                .header("Content-Length", "50")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(mismatchData))
                .build();
        HttpResponse<String> badLengthResp = httpClient.send(badLengthRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(badLengthResp.statusCode()).satisfiesAnyOf(
                code -> assertThat(code).isEqualTo(403), // SignatureDoesNotMatch
                code -> assertThat(code).isEqualTo(400)  // IncompleteBody
        );

        // 2. Thử PUT sai Content-Type (khai text/plain nhưng gửi application/json) -> Storage từ chối
        byte[] exactData = new byte[(int) declaredLength];
        HttpRequest badTypeRequest = HttpRequest.newBuilder()
                .uri(URI.create(presigned.url()))
                .header("Content-Type", "application/json")
                .header("Content-Length", String.valueOf(declaredLength))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(exactData))
                .build();
        HttpResponse<String> badTypeResp = httpClient.send(badTypeRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(badTypeResp.statusCode()).satisfiesAnyOf(
                code -> assertThat(code).isEqualTo(403),
                code -> assertThat(code).isEqualTo(400)
        );

        // 3. PUT đúng kích thước và đúng Content-Type -> Thành công 200
        HttpRequest correctRequest = HttpRequest.newBuilder()
                .uri(URI.create(presigned.url()))
                .header("Content-Type", declaredType)
                .header("Content-Length", String.valueOf(declaredLength))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(exactData))
                .build();
        HttpResponse<String> correctResp = httpClient.send(correctRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(correctResp.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("(7) readRange: Magic bytes 8 KiB, file nhỏ hơn 8 KiB và biên range inclusive")
    void test07_ReadRangeMagicBytesAndSmallFile() throws Exception {
        String keyLarge = "contract-test/range-large-" + UUID.randomUUID() + ".bin";
        String keySmall = "contract-test/range-small-" + UUID.randomUUID() + ".bin";
        createdKeys.add(keyLarge);
        createdKeys.add(keySmall);

        // 1. File lớn 16 KiB
        byte[] dataLarge = new byte[16384];
        for (int i = 0; i < dataLarge.length; i++) {
            dataLarge[i] = (byte) (i % 128);
        }
        putDirectly(keyLarge, dataLarge, "application/octet-stream");

        // Đọc 8 KiB đầu (bytes=0-8191 -> 8192 bytes)
        byte[] rangeLarge = storage.readRange(keyLarge, 0, 8191);
        assertThat(rangeLarge).hasSize(8192);
        assertThat(rangeLarge[0]).isEqualTo((byte) 0);
        assertThat(rangeLarge[8191]).isEqualTo(dataLarge[8191]);

        // 2. File nhỏ 2 KiB (2048 bytes)
        byte[] dataSmall = new byte[2048];
        for (int i = 0; i < dataSmall.length; i++) {
            dataSmall[i] = (byte) 77;
        }
        putDirectly(keySmall, dataSmall, "application/octet-stream");

        // Yêu cầu 0-8191 nhưng file chỉ có 2048 bytes -> MinIO trả về đủ 2048 bytes (không lỗi 416)
        byte[] rangeSmall = storage.readRange(keySmall, 0, 8191);
        assertThat(rangeSmall).hasSize(2048);
        assertThat(rangeSmall[0]).isEqualTo((byte) 77);
    }

    @Test
    @DisplayName("(8) presignGet: Viewing URL với responseContentType, contentDisposition, responseCacheControl")
    void test08_PresignedGetWithCacheControl() throws Exception {
        String key = "contract-test/get-" + UUID.randomUUID() + ".txt";
        createdKeys.add(key);

        byte[] content = "Hello Presigned GET Content".getBytes(StandardCharsets.UTF_8);
        putDirectly(key, content, "text/plain");

        PresignGetRequest request = new PresignGetRequest(
                Duration.ofMinutes(10),
                "text/plain",
                "inline; filename=\"sample.txt\"",
                "private, max-age=1500"
        );
        PresignedUrlResponse presigned = storage.presignGet(key, request);
        assertThat(presigned.httpMethod()).isEqualTo("GET");

        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(presigned.url()))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("Hello Presigned GET Content");

        // Kiểm tra response headers được override từ presigned request
        Optional<String> cacheControl = response.headers().firstValue("Cache-Control");
        assertThat(cacheControl).isPresent();
        assertThat(cacheControl.get()).contains("private, max-age=1500");

        Optional<String> contentDisp = response.headers().firstValue("Content-Disposition");
        assertThat(contentDisp).isPresent();
        assertThat(contentDisp.get()).contains("inline; filename=\"sample.txt\"");
    }

    @Test
    @DisplayName("(9) downloadToTemp: Tải file về thư mục tạm và ném lỗi khi vượt quá maxBytes")
    void test09_DownloadToTempAndMaxBytesThreshold() throws Exception {
        String key = "contract-test/download-" + UUID.randomUUID() + ".bin";
        createdKeys.add(key);

        byte[] data = new byte[10240]; // 10 KiB
        Arrays.fill(data, (byte) 'D');
        putDirectly(key, data, "application/octet-stream");

        Path tempFile = Files.createTempFile("media-test-", ".tmp");
        try {
            // Tải thành công khi maxBytes >= kích thước file
            Path downloaded = storage.downloadToTemp(key, tempFile, 20480L);
            assertThat(downloaded).exists();
            assertThat(Files.size(downloaded)).isEqualTo(10240L);

            // Ném ngoại lệ khi maxBytes < kích thước file (ví dụ max 5 KiB)
            Path anotherTemp = Files.createTempFile("media-test-exceed-", ".tmp");
            try {
                assertThatThrownBy(() -> storage.downloadToTemp(key, anotherTemp, 5120L))
                        .isInstanceOf(AppException.class)
                        .hasMessageContaining("vượt ngưỡng");
            } finally {
                Files.deleteIfExists(anotherTemp);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("(10) delete là idempotent và head key không tồn tại trả về Optional.empty()")
    void test10_DeleteIdempotentAndHeadNonExistent() throws Exception {
        String key = "contract-test/delete-" + UUID.randomUUID() + ".txt";
        putDirectly(key, "dummy data".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(storage.head(key)).isPresent();

        // Xóa lần 1
        storage.delete(key);
        assertThat(storage.head(key)).isEmpty();

        // Xóa lần 2 (không lỗi)
        assertThatCode(() -> storage.delete(key)).doesNotThrowAnyException();
        assertThat(storage.head(key)).isEmpty();

        // Head key hoàn toàn ngẫu nhiên
        assertThat(storage.head("contract-test/non-existent-" + UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("(11) downloadToTemp: Tệp không tồn tại trên storage ném lỗi STORAGE_ERROR")
    void test11_DownloadToTemp_WhenFileNotFound_ThrowsStorageError() throws Exception {
        String nonExistentKey = "contract-test/non-existent-download-" + UUID.randomUUID() + ".bin";
        Path tempFile = Files.createTempFile("media-test-notfound-", ".tmp");
        try {
            assertThatThrownBy(() -> storage.downloadToTemp(nonExistentKey, tempFile, 10240L))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không tồn tại");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("(12) abortMultipartUpload: Hủy upload dở dang và hủy lặp lại (idempotent)")
    void test12_AbortMultipartUpload_IsIdempotent() {
        String key = "contract-test/abort-" + UUID.randomUUID() + ".mp4";
        createdKeys.add(key);

        String uploadId = storage.createMultipartUpload(key, "video/mp4");
        assertThat(uploadId).isNotBlank();

        // Hủy upload đang dở dang
        storage.abortMultipartUpload(key, uploadId);

        // Sau khi hủy, listParts ném NoSuchUploadStorageException vì uploadId không còn tồn tại
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.listParts(key, uploadId))
                .isInstanceOf(com.ChattingSystem.media.storage.exception.NoSuchUploadStorageException.class);

        // Hủy lần 2 (hoặc hủy với uploadId ảo) không được ném ngoại lệ
        assertThatCode(() -> storage.abortMultipartUpload(key, uploadId))
                .doesNotThrowAnyException();
        assertThatCode(() -> storage.abortMultipartUpload(key, "non-existent-upload-id"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("(13) completeMultipartUpload: Gửi ETag sai hoặc part giả mạo bị từ chối")
    void test13_CompleteMultipart_WithInvalidEtag_ThrowsStorageError() {
        String key = "contract-test/invalid-etag-" + UUID.randomUUID() + ".bin";
        createdKeys.add(key);

        String uploadId = storage.createMultipartUpload(key, "application/octet-stream");

        // Gọi complete với ETag hoàn toàn giả mạo (chưa từng upload part)
        List<CompletedPartRequest> fakeParts = List.of(new CompletedPartRequest(1, "fakeetag1234567890"));

        assertThatThrownBy(() -> storage.completeMultipartUpload(key, uploadId, fakeParts))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("(14) readRange: startByte vượt quá dung lượng tệp (HTTP 416) trả về mảng rỗng")
    void test14_ReadRange_WhenStartByteExceedsFileSize_ReturnsEmptyArray() throws Exception {
        String key = "contract-test/range-out-of-bounds-" + UUID.randomUUID() + ".bin";
        createdKeys.add(key);

        byte[] smallData = "Hello".getBytes(StandardCharsets.UTF_8); // 5 bytes
        putDirectly(key, smallData, "text/plain");

        // Yêu cầu đọc từ byte 100 đến 200 (vượt xa kích thước 5 bytes -> S3 trả 416 InvalidRange)
        byte[] result = storage.readRange(key, 100, 200);
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("(15) Storage chưa cấu hình (chưa có credentials) ném STORAGE_NOT_CONFIGURED")
    void test15_StorageNotConfigured_ThrowsStorageNotConfiguredException() {
        MediaStorageProperties unconfiguredProps = new MediaStorageProperties(
                null, null, null, null, true, null, null, 5242880, null, null
        );
        S3ObjectStorage unconfiguredStorage = new S3ObjectStorage(unconfiguredProps);

        assertThatThrownBy(() -> unconfiguredStorage.createMultipartUpload("dummy-key", "text/plain"))
                .isInstanceOf(AppException.class);

        assertThatThrownBy(() -> unconfiguredStorage.head("dummy-key"))
                .isInstanceOf(AppException.class);

        assertThatThrownBy(() -> unconfiguredStorage.readRange("dummy-key", 0, 10))
                .isInstanceOf(AppException.class);
    }

    private void putDirectly(String key, byte[] data, String contentType) throws IOException {
        Path tmp = Files.createTempFile("put-direct-", ".tmp");
        try {
            Files.write(tmp, data);
            storage.putFile(key, tmp, contentType);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
