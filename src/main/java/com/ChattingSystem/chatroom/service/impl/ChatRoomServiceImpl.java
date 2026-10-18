package com.ChattingSystem.chatroom.service.impl;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.response.JoinRoomResponse;
import com.ChattingSystem.chatroom.dto.response.RoomResponse;
import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.ChatRoomService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.google.api.gax.rpc.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    private static final int DEFAULT_TTL_MIN = 60;
    private static final int DEFAULT_RADIUS_M = 3000;

    private final ChatRoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;

    @Override
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

    @Override
    @Transactional
    public JoinRoomResponse  joinRoom(Long userId, Long roomId) {
        ChatRoom room = roomRepo.findById(roomId).orElseThrow(()->new AppException(ErrorCode.ROOM_NOT_FOUND));
        Instant now = Instant.now();
        Instant expiresAt = room.getExpiresAt();
        if (room.getStatus() != RoomStatus.ACTIVE || !expiresAt.isAfter(now)) {
            throw new AppException(ErrorCode.ROOM_EXPIRED);
        }
        if(memberRepo.existsByRoomIdAndUserId(roomId, userId)) {
            throw new AppException(ErrorCode.ALREADY_JOINED_ROOM);
        }
        memberRepo.save(RoomMember.create(roomId, userId, MemberRole.MEMBER));
        return new JoinRoomResponse(
          roomId, userId, MemberRole.MEMBER,Instant.now()
        );
    }


}