package com.ChattingSystem.message.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record SendMessageRequest(
        @NotNull(message = "Thiếu mã tin nhắn phía client") UUID clientMessageId,
        @Size(max = 2000, message = "Tin nhắn tối đa 2000 ký tự") String content,
        @Size(max = 10, message = "Tối đa 10 tệp đính kèm mỗi tin nhắn") List<@NotNull(message = "ID tệp đính kèm không được để trống") Long> attachmentIds
) {
    public SendMessageRequest {
        attachmentIds = (attachmentIds == null) ? null : Collections.unmodifiableList(new ArrayList<>(attachmentIds));
    }

    public SendMessageRequest(UUID clientMessageId, String content) {
        this(clientMessageId, content, null);
    }
}
