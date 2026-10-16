package com.ChattingSystem.waitlist.repository;

import com.ChattingSystem.waitlist.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {
    boolean existsByEmail(String email);
}
