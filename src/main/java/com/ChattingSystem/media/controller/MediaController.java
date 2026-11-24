package com.ChattingSystem.media.controller;

import com.ChattingSystem.media.dto.request.BatchAccessUrlRequest;
import com.ChattingSystem.media.dto.request.CompleteUploadRequest;
import com.ChattingSystem.media.dto.request.InitUploadRequest;
import com.ChattingSystem.media.dto.response.BatchAccessUrlResponse;
import com.ChattingSystem.media.dto.response.CompleteUploadResponse;
import com.ChattingSystem.media.dto.response.InitUploadResponse;
import com.ChattingSystem.media.dto.response.UploadStatusResponse;
import com.ChattingSystem.media.service.MediaAccessService;
import com.ChattingSystem.media.service.MediaUploadService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý các tác vụ liên quan đến tệp phương tiện (Media Assets):
 * - Khởi tạo phiên tải lên (Single PUT / Multipart Upload)
 * - Tra cứu trạng thái và ký lại URL hỗ trợ resume khi mất mạng
 * - Hoàn tất ghép mảnh và kiểm chứng tính hợp lệ của tệp
 * - Hủy phiên tải lên đang dở dang
 * - Cấp liên kết Presigned GET URLs xem tệp theo lô cho thành viên phòng chat
 */
@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaUploadService mediaUploadService;
    private final MediaAccessService mediaAccessService;

    public MediaController(MediaUploadService mediaUploadService, MediaAccessService mediaAccessService) {
        this.mediaUploadService = mediaUploadService;
        this.mediaAccessService = mediaAccessService;
    }

    /**
     * Trích xuất userId từ claim của JWT token một cách an toàn (hỗ trợ cả kiểu Number lẫn String).
     */
    private static Long userIdOf(Jwt jwt) {
        if (jwt == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number num) {
            return num.longValue();
        }
        if (claim instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new AppException(ErrorCode.UNAUTHENTICATED, "Claim userId trong token không hợp lệ");
            }
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED, "Token thiếu claim userId");
    }

    /**
     * 1. Khởi tạo phiên tải lên tệp phương tiện (Single PUT hoặc Multipart Upload).
     */
    @PostMapping("/uploads")
    public ResponseEntity<ApiResponse<InitUploadResponse>> initUpload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InitUploadRequest request
    ) {
        Long userId = userIdOf(jwt);
        InitUploadResponse response = mediaUploadService.initUpload(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Khởi tạo tải lên thành công", response));
    }

    /**
     * 2. Tra cứu trạng thái phiên tải lên và ký lại URL cho các mảnh còn thiếu (hỗ trợ Resume khi mất mạng).
     */
    @GetMapping("/uploads/{mediaAssetId}")
    public ResponseEntity<ApiResponse<UploadStatusResponse>> getUploadStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mediaAssetId
    ) {
        Long userId = userIdOf(jwt);
        UploadStatusResponse response = mediaUploadService.getUploadStatus(userId, mediaAssetId);
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái tải lên thành công", response));
    }

    /**
     * 3. Hoàn tất ghép mảnh, kiểm chứng dung lượng và chữ ký định dạng Magic Bytes.
     */
    @PostMapping("/uploads/{mediaAssetId}/complete")
    public ResponseEntity<ApiResponse<CompleteUploadResponse>> completeUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mediaAssetId,
            @Valid @RequestBody CompleteUploadRequest request
    ) {
        Long userId = userIdOf(jwt);
        CompleteUploadResponse response = mediaUploadService.completeUpload(userId, mediaAssetId, request);
        return ResponseEntity.ok(ApiResponse.success("Hoàn tất tải lên thành công", response));
    }

    /**
     * 4. Hủy phiên tải lên đang dở dang và giải phóng tài nguyên trên storage.
     */
    @DeleteMapping("/uploads/{mediaAssetId}")
    public ResponseEntity<ApiResponse<Void>> abortUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mediaAssetId
    ) {
        Long userId = userIdOf(jwt);
        mediaUploadService.abortUpload(userId, mediaAssetId);
        return ResponseEntity.ok(ApiResponse.success("Hủy phiên tải lên thành công", null));
    }

    /**
     * 5. Cấp liên kết truy cập xem tệp phương tiện (Presigned GET URLs) theo lô cho thành viên phòng chat.
     */
    @PostMapping("/access-urls")
    public ResponseEntity<ApiResponse<BatchAccessUrlResponse>> getAccessUrls(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BatchAccessUrlRequest request
    ) {
        Long userId = userIdOf(jwt);
        BatchAccessUrlResponse response = mediaAccessService.getAccessUrls(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Cấp liên kết truy cập thành công", response));
    }

    /**
     * 6. Xóa tệp phương tiện theo yêu cầu (chủ sở hữu hoặc OWNER của phòng chat).
     */
    @DeleteMapping("/{mediaAssetId}")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long mediaAssetId
    ) {
        Long userId = userIdOf(jwt);
        mediaUploadService.deleteAsset(userId, mediaAssetId);
        return ResponseEntity.ok(ApiResponse.success("Xóa tệp phương tiện thành công", null));
    }
}
