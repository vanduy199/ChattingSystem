package com.ChattingSystem.post.repository;

import com.ChattingSystem.post.dto.response.PostSlugResponse;
import com.ChattingSystem.post.entity.Post;
import com.ChattingSystem.post.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * List queries fetch-join the author; tags are loaded in batches via {@code @BatchSize} on
 * {@code Post.tags} (a collection fetch join cannot be paginated in SQL). Joining on tag slug cannot
 * duplicate rows because a post has each tag at most once.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = "author")
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query(
            value = """
                    select p from Post p join p.tags t
                    where p.status = :status and t.slug = :tagSlug
                    """,
            countQuery = """
                    select count(p) from Post p join p.tags t
                    where p.status = :status and t.slug = :tagSlug
                    """
    )
    Page<Post> findByStatusAndTagSlug(@Param("status") PostStatus status,
                                      @Param("tagSlug") String tagSlug,
                                      Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "author")
    Page<Post> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "tags"})
    Optional<Post> findBySlugAndStatus(String slug, PostStatus status);

    @EntityGraph(attributePaths = {"author", "tags"})
    Optional<Post> findWithAuthorAndTagsById(Long id);

    @Query("""
            select new com.ChattingSystem.post.dto.response.PostSlugResponse(p.slug, p.updatedAt)
            from Post p
            where p.status = :status
            order by p.publishedAt desc, p.id desc
            """)
    List<PostSlugResponse> findSlugsByStatus(@Param("status") PostStatus status);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
