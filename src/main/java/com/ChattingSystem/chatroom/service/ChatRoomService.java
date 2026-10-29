package com.ChattingSystem.chatroom.service;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.LocationRequest;
import com.ChattingSystem.chatroom.dto.response.JoinRoomResponse;
import com.ChattingSystem.chatroom.dto.response.NearbyRoomResponse;
import com.ChattingSystem.chatroom.dto.response.RoomDetailResponse;
import com.ChattingSystem.chatroom.dto.response.RoomMemberResponse;
import com.ChattingSystem.chatroom.dto.response.RoomResponse;

import java.util.List;

public interface ChatRoomService {
    RoomResponse createRoom(Long userId, CreateRoomRequest req);

    List<NearbyRoomResponse> findNearby(Long userId, double lat, double lng, int radiusM);

    RoomDetailResponse getRoom(Long userId, Long roomId);

    JoinRoomResponse joinRoom(Long userId, Long roomId, LocationRequest location);

    List<RoomMemberResponse> getMembers(Long userId, Long roomId);
}
