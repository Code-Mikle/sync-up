package com.mikle.syncup.ai.service;

import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.TagCategory;
import org.springframework.stereotype.Component;

/** 构造标准标签的稳定 Embedding 文本。 */
@Component
public class TagEmbeddingTextBuilder {

    public String build(TagCategory category, Tag tag) {
        String categoryName = category == null ? "未分类" : category.getName();
        return String.join(" | ", categoryName, tag.getName(), tag.getDescription());
    }
}
