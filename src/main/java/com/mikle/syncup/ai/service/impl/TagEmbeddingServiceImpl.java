package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.TagEmbeddingService;
import com.mikle.syncup.ai.service.TagEmbeddingTextBuilder;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.mapper.TagCategoryMapper;
import com.mikle.syncup.mapper.TagMapper;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.TagCategory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TagEmbeddingServiceImpl implements TagEmbeddingService {

    private static final int ENABLED = 1;
    private static final int BATCH_SIZE = 20;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private TagCategoryMapper tagCategoryMapper;

    @Resource
    private ProfileEmbeddingGenerator embeddingGenerator;

    @Resource
    private ProfileEmbeddingCodec embeddingCodec;

    @Resource
    private TagEmbeddingTextBuilder textBuilder;

    @Resource
    private TextHashService textHashService;

    @Override
    public int refreshPendingTags() {
        if (!embeddingGenerator.isAvailable()) {
            return 0;
        }
        Map<Long, TagCategory> categoryMap = tagCategoryMapper.selectList(new QueryWrapper<TagCategory>()
                        .eq("status", ENABLED))
                .stream()
                .collect(Collectors.toMap(TagCategory::getId, category -> category));
        List<Tag> tags = tagMapper.selectList(new QueryWrapper<Tag>()
                .eq("status", ENABLED)
                .orderByAsc("id"));
        int refreshed = 0;
        for (Tag tag : tags) {
            if (refreshed >= BATCH_SIZE || isCurrent(tag, categoryMap.get(tag.getCategoryId()))) {
                continue;
            }
            try {
                refresh(tag, categoryMap.get(tag.getCategoryId()));
                refreshed++;
            } catch (RuntimeException e) {
                log.warn("refresh tag embedding failed, tagId={}", tag.getId(), e);
            }
        }
        return refreshed;
    }

    private boolean isCurrent(Tag tag, TagCategory category) {
        if (category == null || tag.getVectorJson() == null || tag.getEmbeddingDimensions() == null
                || !Objects.equals(tag.getEmbeddingModel(), embeddingGenerator.modelName())) {
            return false;
        }
        return Objects.equals(tag.getEmbeddingTextHash(), textHashService.sha256(textBuilder.build(category, tag)));
    }

    private void refresh(Tag tag, TagCategory category) {
        if (category == null) {
            throw new IllegalStateException("tag category is unavailable");
        }
        String text = textBuilder.build(category, tag);
        GeneratedEmbedding generated = embeddingGenerator.generate(text);
        float[] normalized = embeddingCodec.normalize(generated.vector());
        Tag update = new Tag();
        update.setId(tag.getId());
        update.setEmbeddingTextHash(textHashService.sha256(text));
        update.setEmbeddingModel(generated.model());
        update.setEmbeddingDimensions(normalized.length);
        update.setVectorJson(embeddingCodec.serialize(normalized));
        update.setEmbeddingUpdatedAt(new Date());
        if (tagMapper.updateById(update) <= 0) {
            throw new IllegalStateException("save tag embedding failed");
        }
    }
}
