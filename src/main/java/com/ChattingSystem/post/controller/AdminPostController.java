package com.ChattingSystem.post.controller;

import com.ChattingSystem.post.dto.request.PostUpsertRequest;
import com.ChattingSystem.post.dto.response.PostDetailResponse;
import com.ChattingSystem.post.dto.response.PostSummaryResponse;
import com.ChattingSystem.post.entity.PostStatus;
import com.ChattingSystem.post.service.PostService;
import com.ChattingSystem.shared.response.ApiResponse;
import com.ChattingSystem.shared.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Requires {@code SCOPE_ADMIN} (see {@code SecurityConfig}). */
@RestController
@RequestMapping("/admin/posts")
public class AdminPostController {

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PostSummaryResponse>>> getPosts(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page phải >= 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size phải >= 1")
            @Max(value = 100, message = "size tối đa 100") int size,
            @RequestParam(required = false) PostStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách bài viết thành công",
                postService.getPosts(page, size, status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPost(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy bài viết thành công",
                postService.getPost(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostDetailResponse>> createPost(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PostUpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Tạo bài viết thành công",
                postService.createPost(jwt.getSubject(), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody PostUpsertRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật bài viết thành công",
                postService.updatePost(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa bài viết thành công"));
    }
}
