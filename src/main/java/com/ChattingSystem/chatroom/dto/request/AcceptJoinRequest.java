package com.ChattingSystem.chatroom.dto.request;

public class AcceptJoinRequest {
    private Long roomId;
    private Long userId;
    private String status;

    public Long getRoomId() {
        return roomId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }
}
