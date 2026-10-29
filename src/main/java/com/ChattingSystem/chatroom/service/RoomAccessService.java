package com.ChattingSystem.chatroom.service;

import com.ChattingSystem.chatroom.entity.ChatRoom;
import com.ChattingSystem.chatroom.entity.RoomMember;

/** Kiểm tra quyền dùng chung cho REST, STOMP SUBSCRIBE/SEND và tin nhắn. */
public interface RoomAccessService {

    /** Phòng tồn tại, ACTIVE và chưa hết hạn; nếu không: ROOM_NOT_FOUND / ROOM_EXPIRED. */
    ChatRoom requireActiveRoom(Long roomId);

    /** Như {@link #requireActiveRoom} và người dùng là thành viên; nếu không: NOT_ROOM_MEMBER. */
    RoomMember requireActiveMember(Long userId, Long roomId);
}
