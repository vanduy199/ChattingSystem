package com.ChattingSystem.message.service;

import com.ChattingSystem.message.dto.request.SendMessageRequest;
import com.ChattingSystem.message.dto.response.MessageResponse;
import com.ChattingSystem.message.dto.response.MessageSliceResponse;

public interface MessageService {

    /**
     * Lưu tin và phát {@code MESSAGE} lên topic của phòng sau khi commit.
     * Gửi lại cùng {@code clientMessageId} trả về tin đã lưu, không tạo bản trùng.
     */
    MessageResponse send(Long userId, Long roomId, SendMessageRequest req);

    MessageSliceResponse history(Long userId, Long roomId, Long before, int size);
}
