package com.ChattingSystem.chatroom.service;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.response.JoinRoomResponse;
import com.ChattingSystem.chatroom.dto.response.RoomResponse;

public interface ChatRoomService {
    RoomResponse createRoom(Long userId, CreateRoomRequest req);

    JoinRoomResponse joinRoom(Long userId, Long roomId);
}
