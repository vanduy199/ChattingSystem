package com.ChattingSystem.post.controller;

import com.ChattingSystem.post.dto.response.PostDetailResponse;
import com.ChattingSystem.post.dto.response.PostSlugResponse;
import com.ChattingSystem.post.dto.response.PostSummaryResponse;
import com.ChattingSystem.post.service.PostService;
import com.ChattingSystem.shared.response.ApiResponse;
import com.ChattingSystem.shared.response.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public blog endpoints: only PUBLISHED posts are exposed. */
@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PostSummaryResponse>>> getPosts(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page phải >= 0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size phải >= 1")
            @Max(value = 50, message = "size tối đa 50") int size,
            @RequestParam(required = false) String tag
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách bài viết thành công",
                postService.getPublishedPosts(page, size, tag)));
    }

    @GetMapping("/slugs")
    public ResponseEntity<ApiResponse<List<PostSlugResponse>>> getSlugs() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách slug thành công",
                postService.getPublishedSlugs()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy bài viết thành công",
                postService.getPublishedPost(slug)));
    }
}
