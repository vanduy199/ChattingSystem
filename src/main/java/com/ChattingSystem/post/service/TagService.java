package com.ChattingSystem.post.service;

import com.ChattingSystem.post.dto.response.TagResponse;
import com.ChattingSystem.post.entity.Tag;

import java.util.List;
import java.util.Set;

public interface TagService {

    /** Finds tags by slugified name, creating the missing ones. Keeps the input order. */
    Set<Tag> upsertByNames(List<String> names);

    List<TagResponse> getPublishedTags();
}
