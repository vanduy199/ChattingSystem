package com.ChattingSystem.waitlist.entity;

import com.ChattingSystem.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "waitlist_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitlistEntry extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "source", length = 50)
    private String source;

    private WaitlistEntry(String email, String source) {
        this.email = Objects.requireNonNull(email);
        this.source = source;
    }

    public static WaitlistEntry create(String email, String source) {
        return new WaitlistEntry(email, source);
    }
}
