package com.ChattingSystem.chatroom.service.impl;

import com.ChattingSystem.chatroom.dto.response.MyRoomResponse;
import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomMemberService;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RoomMemberServiceImpl implements RoomMemberService {
    private final RoomMemberRepository roomMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    @Override
    public Page<MyRoomResponse> getMyActiveRooms(Long userId, Pageable pageable){
        return roomMemberRepository.findActiveRoomsByUserId(
                userId,
                Instant.now(),
                RoomStatus.ACTIVE,
                pageable
        );
    }

    @Override
    @Transactional
    public void leaveRoom(Long userId, Long roomId) {

        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.ROOM_NOT_FOUND)
                );

        if (room.getStatus() != RoomStatus.ACTIVE
                || !room.getExpiresAt().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.ROOM_EXPIRED);
        }

        RoomMember member = roomMemberRepository
                .findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() ->
                        new AppException(
                                ErrorCode.ROOM_MEMBER_NOT_FOUND
                        )
                );

        if (member.getRole() != MemberRole.MEMBER) {
            throw new AppException(
                    ErrorCode.OWNER_CANNOT_LEAVE_ROOM
            );
        }

        roomMemberRepository.delete(member);
    }
}
