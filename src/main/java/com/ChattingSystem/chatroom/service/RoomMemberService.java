package com.ChattingSystem.chatroom.service;

import com.ChattingSystem.chatroom.dto.response.MyRoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoomMemberService {
    Page<MyRoomResponse> getMyActiveRooms(Long userId,Pageable pageable);

    void leaveRoom(Long userId, Long roomId);
    void acceptJoinRequest(Long userId, Long roomId, Long targetUserId, String status);
}
