package com.ChattingSystem.chatroom.service;

import com.ChattingSystem.chatroom.dto.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.RoomResponse;
import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private static final int DEFAULT_TTL_MIN = 60;
    private static final int DEFAULT_RADIUS_M = 3000;

    private final ChatRoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;

    @Transactional
    public RoomResponse createRoom(Long userId, CreateRoomRequest req) {
        int ttl = req.ttlMinutes() != null ? req.ttlMinutes() : DEFAULT_TTL_MIN;
        int radius = req.radiusM() != null ? req.radiusM() : DEFAULT_RADIUS_M;

        // flush để row tồn tại trước khi UPDATE center
        ChatRoom room = roomRepo.saveAndFlush(
                ChatRoom.create(req.title(), req.description(), userId, ttl, radius));

        roomRepo.updateCenter(room.getId(), req.lat(), req.lng());
        memberRepo.save(RoomMember.create(room.getId(), userId, MemberRole.OWNER));

        return new RoomResponse(
                room.getId(), room.getTitle(), room.getDescription(),
                req.lat(), req.lng(), room.getRadiusM(),
                room.getStatus(), room.getCreatedAt(), room.getExpiresAt());
    }
}