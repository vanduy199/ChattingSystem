package com.ChattingSystem.post.service;

import com.ChattingSystem.post.dto.request.PostUpsertRequest;
import com.ChattingSystem.post.dto.response.PostDetailResponse;
import com.ChattingSystem.post.dto.response.PostSlugResponse;
import com.ChattingSystem.post.dto.response.PostSummaryResponse;
import com.ChattingSystem.post.entity.PostStatus;
import com.ChattingSystem.shared.response.PageResponse;

import java.util.List;

public interface PostService {

    // Public (PUBLISHED only)

    PageResponse<PostSummaryResponse> getPublishedPosts(int page, int size, String tagSlug);

    PostDetailResponse getPublishedPost(String slug);

    List<PostSlugResponse> getPublishedSlugs();

    // Admin (all statuses)
    // test github

    PageResponse<PostSummaryResponse> getPosts(int page, int size, PostStatus status);

    PostDetailResponse getPost(Long id);

    PostDetailResponse createPost(String authorFirebaseUid, PostUpsertRequest request);

    PostDetailResponse updatePost(Long id, PostUpsertRequest request);

    void deletePost(Long id);
}
