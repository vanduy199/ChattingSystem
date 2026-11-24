package com.ChattingSystem.media.service;

import com.ChattingSystem.media.dto.request.BatchAccessUrlRequest;
import com.ChattingSystem.media.dto.response.BatchAccessUrlResponse;

public interface MediaAccessService {

    /**
     * Cấp liên kết truy cập xem tệp phương tiện (Presigned GET URLs) theo lô cho thành viên phòng chat.
     *
     * @param userId  ID của người dùng đang yêu cầu
     * @param request Yêu cầu lấy URL theo lô (gồm roomId và danh sách mediaAssetIds)
     * @return Danh sách URL xem tệp hoặc thông tin trạng thái / lỗi tương ứng
     */
    BatchAccessUrlResponse getAccessUrls(Long userId, BatchAccessUrlRequest request);
}
