package com.ChattingSystem.chatroom.service.impl;

import com.ChattingSystem.chatroom.dto.response.MyRoomResponse;
import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.RoomMemberService;
import com.ChattingSystem.realtime.event.MemberEventData;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ChattingSystem.chatroom.entity.MemberStatus;

import java.time.Instant;

@Service
public class RoomMemberServiceImpl implements RoomMemberService {
        private final RoomMemberRepository roomMemberRepository;
        private final ChatRoomRepository chatRoomRepository;
        private final UserRepository userRepository;
        private final ApplicationEventPublisher events;

        public RoomMemberServiceImpl(RoomMemberRepository roomMemberRepository,
                        ChatRoomRepository chatRoomRepository,
                        UserRepository userRepository,
                        ApplicationEventPublisher events) {
                this.roomMemberRepository = roomMemberRepository;
                this.chatRoomRepository = chatRoomRepository;
                this.userRepository = userRepository;
                this.events = events;
        }

        @Override
        public Page<MyRoomResponse> getMyActiveRooms(Long userId, Pageable pageable) {
                return roomMemberRepository.findActiveRoomsByUserId(
                                userId,
                                Instant.now(),
                                RoomStatus.ACTIVE,
                                pageable);
        }

        @Override
        @Transactional
        public void leaveRoom(Long userId, Long roomId) {

                ChatRoom room = chatRoomRepository.findById(roomId)
                                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

                if (room.getStatus() != RoomStatus.ACTIVE
                                || (room.getExpiresAt() != null && !room.getExpiresAt().isAfter(Instant.now()))) {
                        throw new AppException(ErrorCode.ROOM_EXPIRED);
                }

                RoomMember member = roomMemberRepository
                                .findByRoomIdAndUserIdAndStatus(roomId, userId, MemberStatus.ACTIVE)
                                .orElseThrow(() -> new AppException(
                                                ErrorCode.ROOM_MEMBER_NOT_FOUND));

                if (member.getRole() != MemberRole.MEMBER) {
                        throw new AppException(
                                        ErrorCode.OWNER_CANNOT_LEAVE_ROOM);
                }

                roomMemberRepository.delete(member);

                String fullName = userRepository.findById(userId).map(User::getFullName).orElse(null);
                events.publishEvent(new RoomEvent(roomId, RoomEventType.MEMBER_LEFT,
                                new MemberEventData(roomId, userId, fullName)));
        }

        public void acceptJoinRequest(Long userId, Long roomId, Long targetUserId, String status) {
                ChatRoom room = chatRoomRepository.findById(roomId)
                                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

                if (room.getStatus() != RoomStatus.ACTIVE
                                || (room.getExpiresAt() != null && !room.getExpiresAt().isAfter(Instant.now()))) {
                        throw new AppException(ErrorCode.ROOM_EXPIRED);
                }

                RoomMember member = roomMemberRepository
                                .findByRoomIdAndUserId(roomId, userId)
                                .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));

                if (member.getRole() != MemberRole.OWNER) {
                        throw new AppException(ErrorCode.NOT_ROOM_OWNER);
                }

                RoomMember targetMember = roomMemberRepository
                                .findByRoomIdAndUserIdAndStatus(roomId, targetUserId, MemberStatus.PENDING)
                                .orElseThrow(() -> new AppException(ErrorCode.ROOM_MEMBER_NOT_FOUND));

                if ("ACCEPT".equalsIgnoreCase(status)) {
                        targetMember.setStatus(MemberStatus.ACTIVE);
                } else if ("REJECT".equalsIgnoreCase(status)) {
                        targetMember.setStatus(MemberStatus.REJECT);
                } else {
                        throw new AppException(ErrorCode.INVALID_STATUS);
                }

                roomMemberRepository.save(targetMember);

                String fullName = userRepository.findById(targetUserId).map(User::getFullName).orElse(null);
                events.publishEvent(new RoomEvent(roomId, RoomEventType.MEMBER_JOINED,
                                new MemberEventData(roomId, targetUserId, fullName)));
        }
}
