package com.ChattingSystem.message.dto.response;

import java.util.List;

/**
 * Một trang lịch sử tin nhắn theo con trỏ: {@code items} mới nhất trước,
 * {@code nextCursor} là {@code before} cho trang cũ hơn (null khi hết).
 */
public record MessageSliceResponse(
        List<MessageResponse> items,
        Long nextCursor
) {}
