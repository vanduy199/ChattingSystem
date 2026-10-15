package com.ChattingSystem.waitlist.service.impl;

import com.ChattingSystem.shared.response.PageResponse;
import com.ChattingSystem.waitlist.dto.request.WaitlistRequest;
import com.ChattingSystem.waitlist.dto.response.WaitlistEntryResponse;
import com.ChattingSystem.waitlist.entity.WaitlistEntry;
import com.ChattingSystem.waitlist.repository.WaitlistEntryRepository;
import com.ChattingSystem.waitlist.service.WaitlistService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class WaitlistServiceImpl implements WaitlistService {

    private final WaitlistEntryRepository waitlistEntryRepository;

    public WaitlistServiceImpl(WaitlistEntryRepository waitlistEntryRepository) {
        this.waitlistEntryRepository = waitlistEntryRepository;
    }

    /**
     * Deliberately not {@code @Transactional}: the insert runs in the repository's own transaction,
     * so a unique-constraint race can be swallowed without leaving an outer transaction rollback-only.
     */
    @Override
    public void join(WaitlistRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String source = request.source() == null || request.source().isBlank()
                ? null
                : request.source().trim();

        if (waitlistEntryRepository.existsByEmail(email)) {
            return;
        }
        try {
            waitlistEntryRepository.save(WaitlistEntry.create(email, source));
        } catch (DataIntegrityViolationException exception) {
            // registered concurrently: same outcome as a duplicate
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WaitlistEntryResponse> getEntries(int page, int size) {
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(waitlistEntryRepository.findAll(pageable).map(WaitlistEntryResponse::from));
    }
}
