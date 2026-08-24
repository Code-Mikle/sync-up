package com.mikle.syncup.ai;

import com.mikle.syncup.ai.model.vo.TagResolutionItem;
import com.mikle.syncup.ai.model.vo.TagResolutionResult;
import com.mikle.syncup.ai.service.impl.TagResolutionServiceImpl;
import com.mikle.syncup.mapper.TagCategoryMapper;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.service.TagService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

class TagResolutionServiceTest {

    @Test
    void resolve_shouldUseExactControlledTagWithoutEmbeddingCall() {
        TagService tagService = Mockito.mock(TagService.class);
        TagCategoryMapper tagCategoryMapper = Mockito.mock(TagCategoryMapper.class);
        Tag tag = new Tag();
        tag.setId(107L);
        tag.setName("羽毛球");
        tag.setDescription("羽毛球约球、双打、单打和日常练习等活动");
        Mockito.when(tagService.listEnabledTags()).thenReturn(List.of(tag));
        Mockito.when(tagCategoryMapper.selectList(Mockito.any())).thenReturn(List.of());

        TagResolutionServiceImpl service = new TagResolutionServiceImpl();
        ReflectionTestUtils.setField(service, "tagService", tagService);
        ReflectionTestUtils.setField(service, "tagCategoryMapper", tagCategoryMapper);

        TagResolutionResult result = service.resolve(List.of("羽毛球"));

        Assertions.assertEquals(1, result.getItems().size());
        TagResolutionItem item = result.getItems().getFirst();
        Assertions.assertEquals("RESOLVED", item.getStatus());
        Assertions.assertNotNull(item.getResolvedTag());
        Assertions.assertEquals(107L, item.getResolvedTag().getTagId());
    }
}
