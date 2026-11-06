package com.ChattingSystem.realtime.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RoomEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public RoomEventBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public static String topicOf(Long roomId) {
        return "/topic/rooms." + roomId;
    }

    // Chỉ gửi khi dữ liệu đã commit, để client không nhận tin mà REST chưa đọc được
    @TransactionalEventListener(fallbackExecution = true)
    public void on(RoomEvent event) {
        messagingTemplate.convertAndSend(topicOf(event.roomId()), event.payload());
    }
}
