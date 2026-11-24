package com.ChattingSystem.media.service;

import com.ChattingSystem.media.dto.request.CompleteUploadRequest;
import com.ChattingSystem.media.dto.request.InitUploadRequest;
import com.ChattingSystem.media.dto.response.CompleteUploadResponse;
import com.ChattingSystem.media.dto.response.InitUploadResponse;
import com.ChattingSystem.media.dto.response.UploadStatusResponse;

public interface MediaUploadService {

    /**
     * Khởi tạo phiên tải lên tệp phương tiện (Single PUT hoặc Multipart Upload).
     */
    InitUploadResponse initUpload(Long userId, InitUploadRequest request);

    /**
     * Tra cứu trạng thái phiên tải lên và ký lại URL cho các mảnh còn thiếu (hỗ trợ Resume khi mất mạng).
     */
    UploadStatusResponse getUploadStatus(Long userId, Long mediaAssetId);

    /**
     * Hoàn tất ghép mảnh, kiểm chứng dung lượng và chữ ký định dạng Magic Bytes.
     */
    CompleteUploadResponse completeUpload(Long userId, Long mediaAssetId, CompleteUploadRequest request);

    /**
     * Hủy phiên tải lên đang dở dang và giải phóng tài nguyên trên storage.
     */
    void abortUpload(Long userId, Long mediaAssetId);

    /**
     * Xóa tệp phương tiện theo yêu cầu người dùng (chủ sở hữu hoặc OWNER của phòng chat).
     */
    void deleteAsset(Long userId, Long mediaAssetId);
}
