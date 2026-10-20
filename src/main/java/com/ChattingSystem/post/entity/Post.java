package com.ChattingSystem.post.entity;

import com.ChattingSystem.shared.entity.BaseEntity;
import com.ChattingSystem.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Column(name = "slug", nullable = false, unique = true, length = 200)
    private String slug;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "excerpt", nullable = false, length = 500)
    private String excerpt;

    @Column(name = "content_html", nullable = false, columnDefinition = "TEXT")
    private String contentHtml;

    @Column(name = "cover_image_url", length = 1000)
    private String coverImageUrl;

    @Column(name = "meta_title", length = 70)
    private String metaTitle;

    @Column(name = "meta_description", length = 170)
    private String metaDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "reading_minutes", nullable = false)
    private int readingMinutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "post_tags",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @OrderBy("name ASC")
    @BatchSize(size = 50)
    private Set<Tag> tags = new LinkedHashSet<>();

    private Post(User author) {
        this.author = Objects.requireNonNull(author);
        this.status = PostStatus.DRAFT;
    }

    public static Post create(User author,
                              String slug,
                              String title,
                              String excerpt,
                              String contentHtml,
                              String coverImageUrl,
                              String metaTitle,
                              String metaDescription,
                              int readingMinutes,
                              PostStatus status,
                              Set<Tag> tags) {
        Post post = new Post(author);
        post.updateContent(slug, title, excerpt, contentHtml, coverImageUrl,
                metaTitle, metaDescription, readingMinutes);
        post.changeStatus(status);
        post.replaceTags(tags);
        return post;
    }

    public void updateContent(String slug,
                              String title,
                              String excerpt,
                              String contentHtml,
                              String coverImageUrl,
                              String metaTitle,
                              String metaDescription,
                              int readingMinutes) {
        this.slug = Objects.requireNonNull(slug);
        this.title = Objects.requireNonNull(title);
        this.excerpt = Objects.requireNonNull(excerpt);
        this.contentHtml = Objects.requireNonNull(contentHtml);
        this.coverImageUrl = coverImageUrl;
        this.metaTitle = metaTitle;
        this.metaDescription = metaDescription;
        this.readingMinutes = Math.max(1, readingMinutes);
    }

    /** {@code publishedAt} is set the first time the post becomes PUBLISHED and kept afterwards. */
    public void changeStatus(PostStatus status) {
        this.status = Objects.requireNonNull(status);
        if (status == PostStatus.PUBLISHED && this.publishedAt == null) {
            this.publishedAt = Instant.now();
        }
    }

    public void replaceTags(Set<Tag> tags) {
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
    }
}
