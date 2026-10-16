package com.ChattingSystem.post.controller;

import com.ChattingSystem.post.dto.response.TagResponse;
import com.ChattingSystem.post.service.TagService;
import com.ChattingSystem.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> getTags() {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách thẻ thành công",
                tagService.getPublishedTags()));
    }
}
