package com.ChattingSystem.post.dto.response;

import com.ChattingSystem.post.entity.Tag;
import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code postCount} is only present on {@code GET /tags}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TagResponse(
        String slug,
        String name,
        Long postCount
) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getSlug(), tag.getName(), null);
    }
}
