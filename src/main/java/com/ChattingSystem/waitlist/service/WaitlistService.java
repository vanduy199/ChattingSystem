package com.ChattingSystem.waitlist.service;

import com.ChattingSystem.shared.response.PageResponse;
import com.ChattingSystem.waitlist.dto.request.WaitlistRequest;
import com.ChattingSystem.waitlist.dto.response.WaitlistEntryResponse;

public interface WaitlistService {

    /** Idempotent: an already registered email is a silent success (no enumeration). */
    void join(WaitlistRequest request);

    PageResponse<WaitlistEntryResponse> getEntries(int page, int size);
}
