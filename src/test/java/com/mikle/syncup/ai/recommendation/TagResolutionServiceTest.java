package com.mikle.syncup.ai.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.vo.TagResolutionItem;
import com.mikle.syncup.ai.model.vo.TagResolutionResult;
import com.mikle.syncup.ai.service.embedding.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.embedding.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.tag.TagEmbeddingTextBuilder;
import com.mikle.syncup.ai.service.embedding.TextHashService;
import com.mikle.syncup.ai.service.embedding.VectorSimilarity;
import com.mikle.syncup.ai.service.tag.impl.TagResolutionServiceImpl;
import com.mikle.syncup.mapper.TagCategoryMapper;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.TagCategory;
import com.mikle.syncup.service.TagService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.stream.LongStream;

class TagResolutionServiceTest {

    private TagService tagService;
    private TagCategoryMapper tagCategoryMapper;
    private ProfileEmbeddingGenerator embeddingGenerator;
    private ProfileEmbeddingCodec embeddingCodec;
    private TagEmbeddingTextBuilder textBuilder;
    private TextHashService textHashService;
    private TagResolutionServiceImpl service;
    private TagCategory category;

    @BeforeEach
    void setUp() {
        tagService = Mockito.mock(TagService.class);
        tagCategoryMapper = Mockito.mock(TagCategoryMapper.class);
        embeddingGenerator = Mockito.mock(ProfileEmbeddingGenerator.class);
        embeddingCodec = new ProfileEmbeddingCodec();
        ReflectionTestUtils.setField(embeddingCodec, "objectMapper", new ObjectMapper());
        textBuilder = new TagEmbeddingTextBuilder();
        textHashService = new TextHashService();

        service = new TagResolutionServiceImpl();
        ReflectionTestUtils.setField(service, "tagService", tagService);
        ReflectionTestUtils.setField(service, "tagCategoryMapper", tagCategoryMapper);
        ReflectionTestUtils.setField(service, "embeddingGenerator", embeddingGenerator);
        ReflectionTestUtils.setField(service, "embeddingCodec", embeddingCodec);
        ReflectionTestUtils.setField(service, "vectorSimilarity", new VectorSimilarity());
        ReflectionTestUtils.setField(service, "textBuilder", textBuilder);
        ReflectionTestUtils.setField(service, "textHashService", textHashService);
        ReflectionTestUtils.setField(service, "highConfidenceScore", 0.72D);
        ReflectionTestUtils.setField(service, "minCandidateScore", 0.60D);
        ReflectionTestUtils.setField(service, "minTopScoreMargin", 0.05D);

        category = new TagCategory();
        category.setId(1L);
        category.setName("运动健身");
        category.setStatus(1);
        Mockito.when(tagCategoryMapper.selectList(Mockito.any())).thenReturn(List.of(category));
    }

    @Test
    void resolve_exactControlledTag_shouldResolveWithoutEmbeddingCall() {
        Tag tag = directTag(107L, "羽毛球");
        Mockito.when(tagService.listEnabledTags()).thenReturn(List.of(tag));

        TagResolutionResult result = service.resolve(List.of("羽毛球"));

        Assertions.assertEquals(1, result.getItems().size());
        TagResolutionItem item = result.getItems().getFirst();
        Assertions.assertEquals("RESOLVED", item.getStatus());
        Assertions.assertEquals(107L, item.getResolvedTag().getTagId());
        Mockito.verify(embeddingGenerator, Mockito.never()).generate(Mockito.anyString());
    }

    @Test
    void resolve_queriesContainBlankDuplicatesAndMoreThanFive_shouldCleanDeduplicateAndLimit() {
        List<Tag> tags = LongStream.rangeClosed(1, 6)
                .mapToObj(id -> directTag(id, "标签" + id))
                .toList();
        Mockito.when(tagService.listEnabledTags()).thenReturn(tags);

        TagResolutionResult result = service.resolve(List.of(
                " ", "标签1", " 标签1 ", "标签2", "标签3", "标签4", "标签5", "标签6"));

        Assertions.assertEquals(List.of("标签1", "标签2", "标签3", "标签4", "标签5"),
                result.getItems().stream().map(TagResolutionItem::getQuery).toList());
        Mockito.verify(embeddingGenerator, Mockito.never()).generate(Mockito.anyString());
    }

    @Test
    void resolve_queryContainsMultipleDirectTags_shouldNotChooseArbitrarily() {
        Mockito.when(tagService.listEnabledTags()).thenReturn(List.of(
                directTag(107L, "羽毛球"), directTag(108L, "网球")));
        Mockito.when(embeddingGenerator.isAvailable()).thenReturn(false);

        TagResolutionItem item = service.resolve(List.of("羽毛球和网球都可以")).getItems().getFirst();

        Assertions.assertEquals("UNRESOLVED", item.getStatus());
        Assertions.assertNull(item.getResolvedTag());
    }

    @Test
    void resolve_semanticTopCandidateHighAndClearlyAhead_shouldResolve() {
        Tag best = vectorTag(107L, "羽毛球", new float[]{1F, 0F});
        Tag second = vectorTag(108L, "网球", new float[]{0F, 1F});
        prepareSemanticSearch(List.of(best, second), new float[]{1F, 0F});

        TagResolutionItem item = service.resolve(List.of("周末挥拍活动")).getItems().getFirst();

        Assertions.assertEquals("RESOLVED", item.getStatus());
        Assertions.assertEquals(best.getId(), item.getResolvedTag().getTagId());
        Assertions.assertEquals(2, item.getCandidates().size());
    }

    @Test
    void resolve_semanticTopCandidatesTooClose_shouldNeedJudgment() {
        Tag first = vectorTag(107L, "羽毛球", new float[]{1F, 0F});
        Tag closeSecond = vectorTag(108L, "网球", new float[]{0.995F, 0.1F});
        prepareSemanticSearch(List.of(first, closeSecond), new float[]{1F, 0F});

        TagResolutionItem item = service.resolve(List.of("周末挥拍活动")).getItems().getFirst();

        Assertions.assertEquals("NEEDS_JUDGMENT", item.getStatus());
        Assertions.assertNull(item.getResolvedTag());
        Assertions.assertEquals(2, item.getCandidates().size());
    }

    @Test
    void resolve_allSemanticCandidatesBelowMinimum_shouldRemainUnresolved() {
        Tag weak = vectorTag(107L, "羽毛球", new float[]{-1F, 0F});
        prepareSemanticSearch(List.of(weak), new float[]{1F, 0F});

        TagResolutionItem item = service.resolve(List.of("安静阅读")).getItems().getFirst();

        Assertions.assertEquals("UNRESOLVED", item.getStatus());
        Assertions.assertNull(item.getResolvedTag());
    }

    @Test
    void resolve_embeddingGenerationFails_shouldRemainUnresolved() {
        Mockito.when(tagService.listEnabledTags()).thenReturn(List.of(directTag(107L, "羽毛球")));
        Mockito.when(embeddingGenerator.isAvailable()).thenReturn(true);
        Mockito.when(embeddingGenerator.generate("周末运动"))
                .thenThrow(new IllegalStateException("embedding timeout"));

        TagResolutionItem item = service.resolve(List.of("周末运动")).getItems().getFirst();

        Assertions.assertEquals("UNRESOLVED", item.getStatus());
        Assertions.assertNull(item.getResolvedTag());
    }

    @Test
    void resolve_staleAndCorruptVectors_shouldSkipThemAndUseValidCandidate() {
        Tag stale = vectorTag(107L, "羽毛球", new float[]{1F, 0F});
        stale.setEmbeddingTextHash("stale-hash");
        Tag corrupt = vectorTag(108L, "网球", new float[]{1F, 0F});
        corrupt.setVectorJson("not-json");
        Tag valid = vectorTag(109L, "乒乓球", new float[]{0.8F, 0.6F});
        prepareSemanticSearch(List.of(stale, corrupt, valid), new float[]{1F, 0F});

        TagResolutionItem item = service.resolve(List.of("室内挥拍活动")).getItems().getFirst();

        Assertions.assertEquals("RESOLVED", item.getStatus());
        Assertions.assertEquals(valid.getId(), item.getResolvedTag().getTagId());
        Assertions.assertEquals(1, item.getCandidates().size());
    }

    private void prepareSemanticSearch(List<Tag> tags, float[] queryVector) {
        Mockito.when(tagService.listEnabledTags()).thenReturn(tags);
        Mockito.when(embeddingGenerator.isAvailable()).thenReturn(true);
        Mockito.when(embeddingGenerator.generate(Mockito.anyString()))
                .thenReturn(new GeneratedEmbedding("test-embedding-model", queryVector));
    }

    private Tag directTag(long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setCategoryId(category.getId());
        tag.setName(name);
        tag.setDescription(name + "相关活动");
        tag.setStatus(1);
        return tag;
    }

    private Tag vectorTag(long id, String name, float[] vector) {
        Tag tag = directTag(id, name);
        tag.setEmbeddingModel("test-embedding-model");
        tag.setEmbeddingDimensions(vector.length);
        tag.setVectorJson(embeddingCodec.serialize(embeddingCodec.normalize(vector)));
        tag.setEmbeddingTextHash(textHashService.sha256(textBuilder.build(category, tag)));
        return tag;
    }
}
