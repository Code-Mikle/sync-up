package com.mikle.syncup.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.mapper.TagCategoryMapper;
import com.mikle.syncup.mapper.TagMapper;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.TagCategory;
import com.mikle.syncup.model.vo.TagCategoryVO;
import com.mikle.syncup.model.vo.TagVO;
import com.mikle.syncup.service.TagService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TagServiceImpl implements TagService {

    private static final int ENABLED = 1;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private TagCategoryMapper tagCategoryMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public List<TagCategoryVO> listEnabledCategories() {
        List<TagCategory> categories = tagCategoryMapper.selectList(new QueryWrapper<TagCategory>()
                .eq("status", ENABLED)
                .orderByAsc("sortOrder")
                .orderByAsc("id"));
        if (categories.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<Tag>> tagsByCategory = listEnabledTags().stream()
                .collect(Collectors.groupingBy(Tag::getCategoryId, LinkedHashMap::new, Collectors.toList()));
        List<TagCategoryVO> result = new ArrayList<>();
        for (TagCategory category : categories) {
            TagCategoryVO categoryVO = new TagCategoryVO();
            BeanUtils.copyProperties(category, categoryVO);
            for (Tag tag : tagsByCategory.getOrDefault(category.getId(), Collections.emptyList())) {
                TagVO tagVO = new TagVO();
                BeanUtils.copyProperties(tag, tagVO);
                categoryVO.getTags().add(tagVO);
            }
            result.add(categoryVO);
        }
        return result;
    }

    @Override
    public List<Tag> listEnabledTags() {
        return tagMapper.selectList(new QueryWrapper<Tag>()
                .eq("status", ENABLED)
                .orderByAsc("categoryId")
                .orderByAsc("sortOrder")
                .orderByAsc("id"));
    }

    @Override
    public List<Tag> validateEnabledTagIds(Collection<Long> tagIds) {
        if (CollectionUtils.isEmpty(tagIds)) {
            return Collections.emptyList();
        }
        Set<Long> normalized = normalizeTagIds(tagIds);
        List<Tag> tags = tagMapper.selectList(new QueryWrapper<Tag>()
                .in("id", normalized)
                .eq("status", ENABLED));
        if (tags.size() != normalized.size()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签不存在或已禁用");
        }
        return tags;
    }

    @Override
    public String serializeAndValidateTagIds(Collection<Long> tagIds) {
        Set<Long> normalized = normalizeTagIds(tagIds);
        validateEnabledTagIds(normalized);
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "标签序列化失败");
        }
    }

    @Override
    public List<Long> parseTagIds(String tagIdsJson) {
        if (tagIdsJson == null || tagIdsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Long> values = objectMapper.readValue(tagIdsJson, new TypeReference<List<Long>>() { });
            return new ArrayList<>(normalizeTagIds(values));
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    public Map<Long, String> getEnabledTagNameMap(Collection<Long> tagIds) {
        if (CollectionUtils.isEmpty(tagIds)) {
            return Collections.emptyMap();
        }
        return tagMapper.selectList(new QueryWrapper<Tag>()
                        .in("id", tagIds)
                        .eq("status", ENABLED))
                .stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (left, right) -> left, LinkedHashMap::new));
    }

    @Override
    public List<String> toDisplayTagNames(String tagIdsJson) {
        List<Long> tagIds = parseTagIds(tagIdsJson);
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> names = getEnabledTagNameMap(tagIds);
        List<String> displayNames = tagIds.stream().map(names::get).filter(java.util.Objects::nonNull).toList();
        return displayNames;
    }

    private Set<Long> normalizeTagIds(Collection<Long> tagIds) {
        if (CollectionUtils.isEmpty(tagIds)) {
            return new LinkedHashSet<>();
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long tagId : tagIds) {
            if (tagId == null || tagId <= 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签 id 非法");
            }
            normalized.add(tagId);
        }
        if (normalized.size() > MAX_USER_TAGS) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多选择 " + MAX_USER_TAGS + " 个标签");
        }
        return normalized;
    }
}
