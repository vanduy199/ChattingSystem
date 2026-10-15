package com.ChattingSystem.waitlist.dto.response;

import com.ChattingSystem.waitlist.entity.WaitlistEntry;

import java.time.Instant;

public record WaitlistEntryResponse(
        Long id,
        String email,
        String source,
        Instant createdAt
) {
    public static WaitlistEntryResponse from(WaitlistEntry entry) {
        return new WaitlistEntryResponse(
                entry.getId(),
                entry.getEmail(),
                entry.getSource(),
                entry.getCreatedAt()
        );
    }
}
