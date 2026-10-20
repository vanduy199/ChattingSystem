package com.ChattingSystem.post.service.impl;

import com.ChattingSystem.post.dto.request.PostUpsertRequest;
import com.ChattingSystem.post.dto.response.PostDetailResponse;
import com.ChattingSystem.post.dto.response.PostSlugResponse;
import com.ChattingSystem.post.dto.response.PostSummaryResponse;
import com.ChattingSystem.post.entity.Post;
import com.ChattingSystem.post.entity.PostStatus;
import com.ChattingSystem.post.entity.Tag;
import com.ChattingSystem.post.repository.PostRepository;
import com.ChattingSystem.post.service.PostService;
import com.ChattingSystem.post.service.TagService;
import com.ChattingSystem.post.util.HtmlSanitizer;
import com.ChattingSystem.post.util.ReadingTimeCalculator;
import com.ChattingSystem.shared.exeption.AppException;
import com.ChattingSystem.shared.exeption.ErrorCode;
import com.ChattingSystem.shared.response.PageResponse;
import com.ChattingSystem.shared.util.SlugUtils;
import com.ChattingSystem.user.entity.User;
import com.ChattingSystem.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class PostServiceImpl implements PostService {

    private static final int SLUG_MAX_LENGTH = 200;
    private static final String FALLBACK_SLUG = "bai-viet";
    private static final String SLUG_UNIQUE_CONSTRAINT = "uk_posts_slug";
    /** Would be shadowed by {@code GET /posts/slugs}. */
    private static final Set<String> RESERVED_SLUGS = Set.of("slugs");
    private static final String CONTENT_HTML_FIELD = "contentHtml";

    private static final Sort PUBLISHED_ORDER = Sort.by(
            Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private static final Sort ADMIN_ORDER = Sort.by(
            Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final TagService tagService;
    private final UserRepository userRepository;

    public PostServiceImpl(PostRepository postRepository,
                           TagService tagService,
                           UserRepository userRepository) {
        this.postRepository = postRepository;
        this.tagService = tagService;
        this.userRepository = userRepository;
    }

    // =========================================================
    // Public
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostSummaryResponse> getPublishedPosts(int page, int size, String tagSlug) {
        PageRequest pageable = PageRequest.of(page, size, PUBLISHED_ORDER);
        Page<Post> posts = (tagSlug == null || tagSlug.isBlank())
                ? postRepository.findByStatus(PostStatus.PUBLISHED, pageable)
                : postRepository.findByStatusAndTagSlug(PostStatus.PUBLISHED, tagSlug.trim(), pageable);
        return PageResponse.of(posts.map(PostSummaryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPublishedPost(String slug) {
        return postRepository.findBySlugAndStatus(slug, PostStatus.PUBLISHED)
                .map(PostDetailResponse::from)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostSlugResponse> getPublishedSlugs() {
        return postRepository.findSlugsByStatus(PostStatus.PUBLISHED);
    }

    // =========================================================
    // Admin
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostSummaryResponse> getPosts(int page, int size, PostStatus status) {
        PageRequest pageable = PageRequest.of(page, size, ADMIN_ORDER);
        Page<Post> posts = status == null
                ? postRepository.findAll(pageable)
                : postRepository.findByStatus(status, pageable);
        return PageResponse.of(posts.map(PostSummaryResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getPost(Long id) {
        return PostDetailResponse.from(findPost(id));
    }

    @Override
    @Transactional
    public PostDetailResponse createPost(String authorFirebaseUid, PostUpsertRequest request) {
        User author = userRepository.findByFirebaseUid(authorFirebaseUid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String contentHtml = sanitizeContent(request.contentHtml());

        String slug = resolveSlug(request.slug(), request.title(), null, null);
        Set<Tag> tags = tagService.upsertByNames(request.tags());

        Post post = Post.create(
                author,
                slug,
                request.title().trim(),
                request.excerpt().trim(),
                contentHtml,
                trimToNull(request.coverImageUrl()),
                trimToNull(request.metaTitle()),
                trimToNull(request.metaDescription()),
                ReadingTimeCalculator.readingMinutes(contentHtml),
                request.status(),
                tags
        );

        return PostDetailResponse.from(saveAndFlush(post));
    }

    @Override
    @Transactional
    public PostDetailResponse updatePost(Long id, PostUpsertRequest request) {
        Post post = findPost(id);
        String contentHtml = sanitizeContent(request.contentHtml());

        String slug = resolveSlug(request.slug(), request.title(), post.getId(), post.getSlug());
        Set<Tag> tags = tagService.upsertByNames(request.tags());

        post.updateContent(
                slug,
                request.title().trim(),
                request.excerpt().trim(),
                contentHtml,
                trimToNull(request.coverImageUrl()),
                trimToNull(request.metaTitle()),
                trimToNull(request.metaDescription()),
                ReadingTimeCalculator.readingMinutes(contentHtml)
        );
        post.changeStatus(request.status());
        post.replaceTags(tags);

        // flush so the audited updatedAt is populated in the response
        return PostDetailResponse.from(saveAndFlush(post));
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
        postRepository.delete(post);
    }

    // =========================================================
    // Helpers
    // =========================================================

    /** Sanitized HTML that is stored; rejects bodies left with no text and no image. */
    private String sanitizeContent(String contentHtml) {
        String sanitized = HtmlSanitizer.sanitize(contentHtml);
        if (!HtmlSanitizer.hasContent(sanitized)) {
            throw AppException.fieldError(CONTENT_HTML_FIELD, "Nội dung không có chữ hoặc hình ảnh hợp lệ");
        }
        return sanitized;
    }

    private Post findPost(Long id) {
        return postRepository.findWithAuthorAndTagsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
    }

    private Post saveAndFlush(Post post) {
        try {
            return postRepository.saveAndFlush(post);
        } catch (DataIntegrityViolationException exception) {
            // concurrent insert of the same slug between the existence check and the flush
            if (String.valueOf(exception.getMostSpecificCause().getMessage()).contains(SLUG_UNIQUE_CONSTRAINT)) {
                throw new AppException(ErrorCode.POST_SLUG_EXISTS);
            }
            throw exception;
        }
    }

    /**
     * An explicit slug must be free (409 otherwise). A blank slug keeps the current one on update,
     * and on create is generated from the title, suffixed with -2, -3, ... until unique.
     */
    private String resolveSlug(String requestedSlug, String title, Long currentId, String currentSlug) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            String slug = requestedSlug.trim();
            boolean taken = RESERVED_SLUGS.contains(slug) || (currentId == null
                    ? postRepository.existsBySlug(slug)
                    : postRepository.existsBySlugAndIdNot(slug, currentId));
            if (taken) {
                throw new AppException(ErrorCode.POST_SLUG_EXISTS);
            }
            return slug;
        }

        if (currentSlug != null) {
            return currentSlug;
        }

        String base = SlugUtils.slugify(title, SLUG_MAX_LENGTH);
        if (base.isEmpty()) {
            base = FALLBACK_SLUG;
        }

        String candidate = base;
        for (int suffix = 2; isTaken(candidate); suffix++) {
            String tail = "-" + suffix;
            String head = base.length() + tail.length() > SLUG_MAX_LENGTH
                    ? SlugUtils.slugify(base.substring(0, SLUG_MAX_LENGTH - tail.length()))
                    : base;
            candidate = head + tail;
        }
        return candidate;
    }

    private boolean isTaken(String slug) {
        return RESERVED_SLUGS.contains(slug) || postRepository.existsBySlug(slug);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
