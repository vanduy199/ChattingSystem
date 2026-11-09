package com.ChattingSystem.chatroom.service.impl;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.entity.MemberStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RoomAccessServiceImpl implements RoomAccessService {

    private final ChatRoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;

    public RoomAccessServiceImpl(ChatRoomRepository roomRepo, RoomMemberRepository memberRepo) {
        this.roomRepo = roomRepo;
        this.memberRepo = memberRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoom requireActiveRoom(Long roomId) {
        ChatRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        if (room.getStatus() != RoomStatus.ACTIVE
                || (room.getExpiresAt() != null && !room.getExpiresAt().isAfter(Instant.now()))) {
            throw new AppException(ErrorCode.ROOM_EXPIRED);
        }
        return room;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomMember requireActiveMember(Long userId, Long roomId) {
        requireActiveRoom(roomId);
        return memberRepo.findByRoomIdAndUserIdAndStatus(roomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
    }
}
