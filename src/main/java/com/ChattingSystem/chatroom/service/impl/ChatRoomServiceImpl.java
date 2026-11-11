package com.ChattingSystem.chatroom.service.impl;

import com.ChattingSystem.chatroom.dto.request.CreateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.UpdateRoomRequest;
import com.ChattingSystem.chatroom.dto.request.LocationRequest;
import com.ChattingSystem.chatroom.dto.response.JoinRoomResponse;
import com.ChattingSystem.chatroom.dto.response.NearbyRoomResponse;
import com.ChattingSystem.chatroom.dto.response.RoomDetailResponse;
import com.ChattingSystem.chatroom.dto.response.RoomMemberResponse;
import com.ChattingSystem.chatroom.dto.response.RoomResponse;
import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.MemberRole;
import com.ChattingSystem.chatroom.entity.MemberStatus;
import com.ChattingSystem.chatroom.entity.RoomMember;
import com.ChattingSystem.chatroom.entity.RoomStatus;
import com.ChattingSystem.chatroom.repository.ChatRoomRepository;
import com.ChattingSystem.chatroom.repository.RoomMemberRepository;
import com.ChattingSystem.chatroom.service.ChatRoomService;
import com.ChattingSystem.chatroom.service.RoomAccessService;
import com.ChattingSystem.chatroom.util.DistanceLabel;
import com.ChattingSystem.chatroom.util.LocationFuzzer;
import com.ChattingSystem.realtime.event.MemberEventData;
import com.ChattingSystem.realtime.event.RoomEvent;
import com.ChattingSystem.realtime.event.RoomEventType;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ChatRoomServiceImpl implements ChatRoomService {
    private static final int DEFAULT_RADIUS_M = 3000;
    private static final int MAX_ACTIVE_ROOMS_PER_USER = 3;

    private final ChatRoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;
    private final UserRepository userRepo;
    private final RoomAccessService roomAccess;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public ChatRoomServiceImpl(ChatRoomRepository roomRepo,
            RoomMemberRepository memberRepo,
            UserRepository userRepo,
            RoomAccessService roomAccess,
            ApplicationEventPublisher events) {
        this.roomRepo = roomRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.roomAccess = roomAccess;
        this.events = events;
    }

    @Override
    @Transactional
    public RoomResponse createRoom(Long userId, CreateRoomRequest req) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.isGuest()) {
            throw new AppException(ErrorCode.LOGIN_REQUIRED_TO_CREATE_ROOM);
        }

        long active = roomRepo.countByCreatedByAndStatusAndExpiresAtAfter(userId, RoomStatus.ACTIVE, Instant.now());
        if (active >= MAX_ACTIVE_ROOMS_PER_USER) {
            throw new AppException(ErrorCode.ROOM_LIMIT_REACHED);
        }

        int ttl = req.ttlMinutes() != null ? req.ttlMinutes() : 0;
        int radius = req.radiusM() != null ? req.radiusM() : DEFAULT_RADIUS_M;

        // flush để row tồn tại trước khi UPDATE center
        ChatRoom room = roomRepo.saveAndFlush(
                ChatRoom.create(req.title(), req.description(), userId, ttl, radius, req.roomType()));

        LocationFuzzer.Point display = LocationFuzzer.fuzz(req.lat(), req.lng(), random);
        roomRepo.updateCenter(room.getId(), req.lat(), req.lng(), display.lat(), display.lng());
        memberRepo.save(RoomMember.create(room.getId(), userId, MemberRole.OWNER, MemberStatus.ACTIVE));

        return new RoomResponse(
                room.getId(), room.getTitle(), room.getDescription(), room.getRadiusM(),
                room.getStatus(), room.getCreatedAt(), room.getExpiresAt());
    }

    @Override
    @Transactional
    public RoomResponse updateRoom(Long userId, UpdateRoomRequest req) {
        ChatRoom room = roomRepo.findById(req.id())
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.getCreatedBy().equals(userId)) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND_OR_NOT_OWNER);
        }

        room.updateInfo(req.title(), req.description(), req.roomType());

        if (req.ttlMinutes() != null) {
            room.extendExpiration(req.ttlMinutes());
        }

        return new RoomResponse(
                room.getId(), room.getTitle(), room.getDescription(), room.getRadiusM(),
                room.getStatus(), room.getCreatedAt(), room.getExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NearbyRoomResponse> findNearby(Long userId, double lat, double lng, int radiusM) {
        return roomRepo.findNearby(lat, lng, radiusM, userId).stream()
                .map(row -> new NearbyRoomResponse(
                        row.getId(), row.getTitle(), row.getDescription(), row.getRadiusM(),
                        row.getMemberCount(), DistanceLabel.of(row.getDistanceM()),
                        row.getJoined(), row.getExpiresAt(), row.getRoomType()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDetailResponse getRoom(Long userId, Long roomId) {
        ChatRoom room = roomRepo.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        RoomMember me = memberRepo.findByRoomIdAndUserIdAndStatus(roomId, userId, MemberStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_ROOM_MEMBER));
        return new RoomDetailResponse(
                room.getId(), room.getTitle(), room.getDescription(), room.getRadiusM(),
                room.getStatus(), room.getCreatedBy(), memberRepo.countByRoomId(roomId),
                me.getRole(), room.getCreatedAt(), room.getExpiresAt(), room.getRoomType());
    }

    @Override
    @Transactional
    public JoinRoomResponse joinRoom(Long userId, Long roomId, LocationRequest location) {
        RoomMember member;
        ChatRoom room = roomAccess.requireActiveRoom(roomId);

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (room != null && "private".equalsIgnoreCase(room.getRoomType()) && user.isGuest()) {
            throw new AppException(ErrorCode.LOGIN_REQUIRED_FOR_PRIVATE_ROOM);
        }

        Optional<RoomMember> existingOpt = memberRepo.findByRoomIdAndUserId(roomId, userId);
        if (existingOpt.isPresent()) {
            member = existingOpt.get();
            if (member.getStatus() == MemberStatus.ACTIVE) {
                return new JoinRoomResponse(roomId, userId, member.getRole(), member.getJoinedAt());
            } else if (member.getStatus() == MemberStatus.PENDING) {
                throw new AppException(ErrorCode.ROOM_MEMBER_PENDING);
            }
        }
        // So với tâm đã làm mờ, khớp với những gì /rooms/nearby cho người dùng thấy
        if (!roomRepo.isWithinRoomRadius(roomId, location.lat(), location.lng())) {
            throw new AppException(ErrorCode.OUT_OF_RANGE);
        }

        if (room != null && "private".equalsIgnoreCase(room.getRoomType())) {
            member = memberRepo.save(RoomMember.create(roomId, userId, MemberRole.MEMBER, MemberStatus.PENDING));
        } else {
            member = memberRepo.save(RoomMember.create(roomId, userId, MemberRole.MEMBER, MemberStatus.ACTIVE));
        }

        if (member.getStatus() == MemberStatus.ACTIVE) {
            String fullName = user.getFullName();
            events.publishEvent(new RoomEvent(roomId, RoomEventType.MEMBER_JOINED,
                    new MemberEventData(roomId, userId, fullName)));
        }

        return new JoinRoomResponse(roomId, userId, member.getRole(), member.getJoinedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomMemberResponse> getMembers(Long userId, Long roomId) {
        if (!memberRepo.existsByRoomIdAndUserIdAndStatus(roomId, userId, MemberStatus.ACTIVE)) {
            throw new AppException(ErrorCode.NOT_ROOM_MEMBER);
        }
        return memberRepo.findMembers(roomId);
    }
}
