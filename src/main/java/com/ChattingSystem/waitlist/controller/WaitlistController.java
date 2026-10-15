package com.ChattingSystem.waitlist.controller;

import com.ChattingSystem.shared.response.ApiResponse;
import com.ChattingSystem.waitlist.dto.request.WaitlistRequest;
import com.ChattingSystem.waitlist.service.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/waitlist")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> join(@Valid @RequestBody WaitlistRequest request) {
        waitlistService.join(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng ký danh sách chờ thành công!"));
    }
}
