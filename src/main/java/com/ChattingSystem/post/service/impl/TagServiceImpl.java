package com.ChattingSystem.post.service.impl;

import com.ChattingSystem.post.dto.response.TagResponse;
import com.ChattingSystem.post.entity.PostStatus;
import com.ChattingSystem.post.entity.Tag;
import com.ChattingSystem.post.repository.TagRepository;
import com.ChattingSystem.post.service.TagService;
import com.ChattingSystem.shared.util.SlugUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TagServiceImpl implements TagService {

    private static final int TAG_SLUG_MAX_LENGTH = 100;

    private final TagRepository tagRepository;

    public TagServiceImpl(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Override
    @Transactional
    public Set<Tag> upsertByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new LinkedHashSet<>();
        }

        // slug -> display name; the first spelling of a slug wins
        Map<String, String> requested = new LinkedHashMap<>();
        for (String rawName : names) {
            if (rawName == null) {
                continue;
            }
            String name = rawName.trim().replaceAll("\\s+", " ");
            String slug = SlugUtils.slugify(name, TAG_SLUG_MAX_LENGTH);
            if (!slug.isEmpty()) {
                requested.putIfAbsent(slug, name);
            }
        }
        if (requested.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Map<String, Tag> existing = tagRepository.findBySlugIn(requested.keySet())
                .stream()
                .collect(Collectors.toMap(Tag::getSlug, Function.identity()));

        List<Tag> missing = new ArrayList<>();
        requested.forEach((slug, name) -> {
            if (!existing.containsKey(slug)) {
                missing.add(Tag.create(slug, name));
            }
        });
        tagRepository.saveAll(missing).forEach(tag -> existing.put(tag.getSlug(), tag));

        Set<Tag> result = new LinkedHashSet<>();
        requested.keySet().forEach(slug -> result.add(existing.get(slug)));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> getPublishedTags() {
        return tagRepository.findWithPostCountByPostStatus(PostStatus.PUBLISHED);
    }
}
