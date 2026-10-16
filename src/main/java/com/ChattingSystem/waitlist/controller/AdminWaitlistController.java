package com.ChattingSystem.waitlist.controller;

import com.ChattingSystem.shared.response.ApiResponse;
import com.ChattingSystem.shared.response.PageResponse;
import com.ChattingSystem.waitlist.dto.response.WaitlistEntryResponse;
import com.ChattingSystem.waitlist.service.WaitlistService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Requires {@code SCOPE_ADMIN} (see {@code SecurityConfig}). */
@RestController
@RequestMapping("/admin/waitlist")
public class AdminWaitlistController {

    private final WaitlistService waitlistService;

    public AdminWaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WaitlistEntryResponse>>> getEntries(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page phải >= 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size phải >= 1")
            @Max(value = 100, message = "size tối đa 100") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách chờ thành công",
                waitlistService.getEntries(page, size)));
    }
}
