package com.mikle.syncup.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.vo.TagCategoryVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface TagService extends IService<Tag> {

    int MAX_USER_TAGS = 10;

    List<TagCategoryVO> listEnabledCategories();

    List<Tag> listEnabledTags();

    List<Tag> validateEnabledTagIds(Collection<Long> tagIds);

    String serializeAndValidateTagIds(Collection<Long> tagIds);

    List<Long> parseTagIds(String tagIdsJson);

    Map<Long, String> getEnabledTagNameMap(Collection<Long> tagIds);

    List<String> toDisplayTagNames(String tagIdsJson);
}
