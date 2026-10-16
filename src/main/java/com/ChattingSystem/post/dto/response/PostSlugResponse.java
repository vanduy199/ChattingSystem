package com.ChattingSystem.post.dto.response;

import java.time.Instant;

public record PostSlugResponse(String slug, Instant updatedAt) {
}
