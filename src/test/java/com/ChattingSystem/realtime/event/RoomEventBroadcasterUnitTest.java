package com.ChattingSystem.realtime.event;

import com.ChattingSystem.media.dto.response.MediaStatusEventDto;
import com.ChattingSystem.media.entity.MediaKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoomEventBroadcasterUnitTest {

    @Test
    @DisplayName("on(RoomEvent): chuyển đổi và gửi đúng topic và payload cho MEDIA_STATUS")
    void on_mediaStatusEvent_shouldBroadcastToRoomTopic() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        RoomEventBroadcaster broadcaster = new RoomEventBroadcaster(messagingTemplate);

        MediaStatusEventDto dto = MediaStatusEventDto.available(
                100L, 5L, 200L, MediaKind.IMAGE, 1920, 1080, null
        );
        RoomEvent event = new RoomEvent(5L, RoomEventType.MEDIA_STATUS, dto);

        broadcaster.on(event);

        ArgumentCaptor<RoomEvent.Payload> payloadCaptor = ArgumentCaptor.forClass(RoomEvent.Payload.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/rooms.5"), payloadCaptor.capture());

        RoomEvent.Payload payload = payloadCaptor.getValue();
        assertThat(payload.type()).isEqualTo(RoomEventType.MEDIA_STATUS);
        assertThat(payload.data()).isEqualTo(dto);
    }

    @Test
    @DisplayName("topicOf: trả về đúng tiền tố topic STOMP")
    void topicOf_shouldReturnCorrectDestination() {
        assertThat(RoomEventBroadcaster.topicOf(123L)).isEqualTo("/topic/rooms.123");
    }
}
