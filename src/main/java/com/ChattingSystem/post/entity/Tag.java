package com.ChattingSystem.post.entity;

import com.ChattingSystem.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseEntity {

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    private Tag(String slug, String name) {
        this.slug = Objects.requireNonNull(slug);
        this.name = Objects.requireNonNull(name);
    }

    public static Tag create(String slug, String name) {
        return new Tag(slug, name);
    }
}
