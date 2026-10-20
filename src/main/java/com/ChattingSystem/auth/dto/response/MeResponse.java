package com.ChattingSystem.auth.dto.response;

import com.ChattingSystem.user.entity.Role;
import com.ChattingSystem.user.entity.User;

public record MeResponse(
        Long id,
        String email,
        String fullName,
        String avatarObject,
        Role role
) {
    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarObject(),
                user.getRole()
        );
    }
}
