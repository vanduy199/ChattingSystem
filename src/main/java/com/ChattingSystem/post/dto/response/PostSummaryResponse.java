package com.ChattingSystem.post.dto.response;

import com.ChattingSystem.post.entity.Post;
import com.ChattingSystem.post.entity.PostStatus;

import java.time.Instant;
import java.util.List;

public record PostSummaryResponse(
        Long id,
        String slug,
        String title,
        String excerpt,
        String coverImageUrl,
        List<TagResponse> tags,
        PostStatus status,
        Instant publishedAt,
        Instant updatedAt,
        int readingMinutes,
        AuthorResponse author
) {
    public static PostSummaryResponse from(Post post) {
        return new PostSummaryResponse(
                post.getId(),
                post.getSlug(),
                post.getTitle(),
                post.getExcerpt(),
                post.getCoverImageUrl(),
                post.getTags().stream().map(TagResponse::from).toList(),
                post.getStatus(),
                post.getPublishedAt(),
                post.getUpdatedAt(),
                post.getReadingMinutes(),
                AuthorResponse.from(post.getAuthor())
        );
    }
}
