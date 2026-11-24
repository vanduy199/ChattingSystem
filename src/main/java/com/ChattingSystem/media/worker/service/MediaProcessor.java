package com.ChattingSystem.media.worker.service;

/**
 * Giao diện xử lý hậu kỳ đa phương tiện (ảnh/video) nền bằng FFmpeg.
 */
public interface MediaProcessor {

    /**
     * Thực hiện kiểm chứng, tối ưu hóa (scale/remux/transcode) và cập nhật trạng thái tệp.
     *
     * @param mediaAssetId ID của bản ghi media_assets cần xử lý
     */
    void process(Long mediaAssetId);
}
