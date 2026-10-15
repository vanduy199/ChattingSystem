package com.ChattingSystem.post.repository;

import com.ChattingSystem.post.dto.response.TagResponse;
import com.ChattingSystem.post.entity.PostStatus;
import com.ChattingSystem.post.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findBySlugIn(Collection<String> slugs);

    /** Tags used by at least one post with the given status, with that post count. */
    @Query("""
            select new com.ChattingSystem.post.dto.response.TagResponse(t.slug, t.name, count(p.id))
            from Post p join p.tags t
            where p.status = :status
            group by t.id, t.slug, t.name
            order by count(p.id) desc, t.name asc
            """)
    List<TagResponse> findWithPostCountByPostStatus(@Param("status") PostStatus status);
}
