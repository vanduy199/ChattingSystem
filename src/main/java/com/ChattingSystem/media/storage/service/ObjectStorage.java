package com.ChattingSystem.media.storage.service;

import com.ChattingSystem.media.storage.dto.request.CompletedPartRequest;
import com.ChattingSystem.media.storage.dto.request.PresignGetRequest;
import com.ChattingSystem.media.storage.dto.response.ObjectMetadataResponse;
import com.ChattingSystem.media.storage.dto.response.PartSummaryResponse;
import com.ChattingSystem.media.storage.dto.response.PresignedUrlResponse;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface ObjectStorage {

    /**
     * Khởi tạo phiên tải lên nhiều phần (Multipart Upload) cho tệp lớn (>5MB).
     *
     * @param key         Đường dẫn lưu trên storage (ví dụ: raw/2026/11/19/uuid.mp4)
     * @param contentType MIME type của tệp
     * @return uploadId định danh phiên multipart
     */
    String createMultipartUpload(String key, String contentType);

    /**
     * Ký URL PUT cho một mảnh cụ thể trong phiên Multipart upload.
     *
     * @param key           Đường dẫn lưu trên storage
     * @param uploadId      ID phiên multipart
     * @param partNumber    Số thứ tự mảnh (1, 2, 3...)
     * @param contentLength Kích thước chính xác của mảnh này (bytes) để ký vào header
     * @param ttl           Thời hạn của Presigned URL
     * @return PresignedUrlResponse chứa URL và headers
     */
    PresignedUrlResponse presignUploadPart(String key, String uploadId, int partNumber, long contentLength, Duration ttl);

    /**
     * Lấy danh sách các mảnh đã tải lên thành công trên storage.
     *
     * @param key      Đường dẫn lưu trên storage
     * @param uploadId ID phiên multipart
     * @return Danh sách các mảnh đã hoàn thành
     */
    List<PartSummaryResponse> listParts(String key, String uploadId);

    /**
     * Hoàn tất ghép các mảnh thành tệp hoàn chỉnh trên storage.
     *
     * @param key      Đường dẫn lưu trên storage
     * @param uploadId ID phiên multipart
     * @param parts    Danh sách các mảnh và mã ETag tương ứng
     */
    void completeMultipartUpload(String key, String uploadId, List<CompletedPartRequest> parts);

    /**
     * Hủy phiên multipart và dọn dẹp các mảnh tải dở trên storage.
     *
     * @param key      Đường dẫn lưu trên storage
     * @param uploadId ID phiên multipart
     */
    void abortMultipartUpload(String key, String uploadId);

    /**
     * Ký URL PUT cho một tệp nhỏ (≤5MB) tải lên 1 lần (SINGLE mode).
     *
     * @param key           Đường dẫn lưu trên storage
     * @param contentType   MIME type
     * @param contentLength Kích thước chính xác của tệp (bytes) để ký vào header
     * @param ttl           Thời hạn của Presigned URL
     * @return PresignedUrlResponse chứa URL và headers
     */
    PresignedUrlResponse presignSinglePut(String key, String contentType, long contentLength, Duration ttl);

    /**
     * Kiểm tra metadata của tệp trên storage (kích thước thật, contentType, etag).
     *
     * @param key Đường dẫn tệp
     * @return Optional chứa metadata nếu tệp tồn tại, hoặc Optional.empty() nếu không tìm thấy (404)
     */
    Optional<ObjectMetadataResponse> head(String key);

    /**
     * Đọc một dải byte cụ thể của tệp trên storage để kiểm tra chữ ký magic bytes.
     *
     * @param key       Đường dẫn tệp
     * @param startByte Byte bắt đầu, tính từ 0 (INCLUSIVE)
     * @param endByte   Byte kết thúc (INCLUSIVE)
     * @return Mảng byte đọc được (tự động cắt ngắn nếu tệp nhỏ hơn range)
     */
    byte[] readRange(String key, long startByte, long endByte);

    /**
     * Ký URL GET để người dùng xem trực tiếp tệp từ trình duyệt.
     *
     * @param key     Đường dẫn tệp (bản display hoặc thumb)
     * @param request Tùy chọn ký (ttl, contentDisposition=inline, responseCacheControl)
     * @return PresignedUrlResponse
     */
    PresignedUrlResponse presignGet(String key, PresignGetRequest request);

    /**
     * Xóa tệp vật lý trên storage (Idempotent - không báo lỗi nếu tệp không tồn tại).
     *
     * @param key Đường dẫn tệp
     */
    void delete(String key);

    /**
     * Tải tệp từ storage về máy chủ để Worker xử lý, có giới hạn trần dung lượng chống tràn đĩa.
     *
     * @param key        Đường dẫn tệp
     * @param targetPath Đường dẫn file đích trên ổ cứng tạm máy chủ
     * @param maxBytes   Dung lượng tối đa cho phép tải về
     * @return targetPath
     */
    Path downloadToTemp(String key, Path targetPath, long maxBytes);

    /**
     * Tải một file từ máy chủ lên storage (dành cho Worker đẩy bản display/thumb).
     *
     * @param key         Đường dẫn lưu trên storage
     * @param filePath    Đường dẫn file trên máy chủ
     * @param contentType MIME type
     */
    void putFile(String key, Path filePath, String contentType);
}
