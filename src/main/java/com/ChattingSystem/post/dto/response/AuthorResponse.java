package com.ChattingSystem.post.dto.response;

import com.ChattingSystem.user.entity.User;

public record AuthorResponse(String fullName) {
    public static AuthorResponse from(User user) {
        return new AuthorResponse(user.getFullName());
    }
}
