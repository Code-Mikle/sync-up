package com.mikle.syncup.ai.service.impl;

import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.vo.TagResolutionCandidate;
import com.mikle.syncup.ai.model.vo.TagResolutionItem;
import com.mikle.syncup.ai.model.vo.TagResolutionResult;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.TagEmbeddingTextBuilder;
import com.mikle.syncup.ai.service.TagResolutionService;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.ai.service.VectorSimilarity;
import com.mikle.syncup.mapper.TagCategoryMapper;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.TagCategory;
import com.mikle.syncup.service.TagService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class TagResolutionServiceImpl implements TagResolutionService {

    private static final int TOP_K = 5;

    @Value("${sync-up.ai.tag-resolution.high-confidence-score:0.72}")
    private double highConfidenceScore;

    @Value("${sync-up.ai.tag-resolution.min-candidate-score:0.60}")
    private double minCandidateScore;

    @Value("${sync-up.ai.tag-resolution.min-top-score-margin:0.05}")
    private double minTopScoreMargin;

    @Resource
    private TagService tagService;

    @Resource
    private TagCategoryMapper tagCategoryMapper;

    @Resource
    private ProfileEmbeddingGenerator embeddingGenerator;

    @Resource
    private ProfileEmbeddingCodec embeddingCodec;

    @Resource
    private VectorSimilarity vectorSimilarity;

    @Resource
    private TagEmbeddingTextBuilder textBuilder;

    @Resource
    private TextHashService textHashService;

    @Override
    public TagResolutionResult resolve(List<String> tagQueries) {
        TagResolutionResult result = new TagResolutionResult();
        if (tagQueries == null || tagQueries.isEmpty()) {
            return result;
        }
        List<String> queries = tagQueries.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(5)
                .toList();
        if (queries.isEmpty()) {
            return result;
        }
        List<Tag> tags = tagService.listEnabledTags();
        Map<Long, TagCategory> categories = tagCategoryMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<TagCategory>()
                        .eq("status", 1)).stream()
                .collect(java.util.stream.Collectors.toMap(TagCategory::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        for (String query : queries) {
            result.getItems().add(resolveOne(query, tags, categories));
        }
        return result;
    }

    private TagResolutionItem resolveOne(String query, List<Tag> tags, Map<Long, TagCategory> categories) {
        TagResolutionItem item = new TagResolutionItem();
        item.setQuery(query);
        List<Tag> directMatches = directMatches(query, tags);
        if (directMatches.size() == 1) {
            item.setResolvedTag(toCandidate(directMatches.getFirst(), 1D));
            item.setStatus("RESOLVED");
            return item;
        }
        if (!embeddingGenerator.isAvailable()) {
            item.setStatus("UNRESOLVED");
            return item;
        }
        GeneratedEmbedding generated;
        try {
            generated = embeddingGenerator.generate(query);
        } catch (RuntimeException e) {
            log.warn("generate tag query embedding failed, query={}", query, e);
            item.setStatus("UNRESOLVED");
            return item;
        }
        float[] queryVector = embeddingCodec.normalize(generated.vector());
        List<TagResolutionCandidate> candidates = new ArrayList<>();
        for (Tag tag : tags) {
            if (!isCurrentVector(tag, categories.get(tag.getCategoryId()), generated.model(), queryVector.length)) {
                continue;
            }
            try {
                double score = normalizeCosine(vectorSimilarity.cosine(queryVector, embeddingCodec.deserialize(tag.getVectorJson())));
                candidates.add(toCandidate(tag, score));
            } catch (RuntimeException e) {
                log.warn("skip invalid tag embedding, tagId={}", tag.getId(), e);
            }
        }
        candidates.sort(Comparator.comparing(TagResolutionCandidate::getScore).reversed()
                .thenComparing(TagResolutionCandidate::getTagId));
        List<TagResolutionCandidate> topCandidates = candidates.stream().limit(TOP_K).toList();
        if (topCandidates.isEmpty() || topCandidates.getFirst().getScore() < minCandidateScore) {
            item.setStatus("UNRESOLVED");
            return item;
        }
        item.setCandidates(new ArrayList<>(topCandidates));
        double secondScore = topCandidates.size() > 1 ? topCandidates.get(1).getScore() : 0D;
        if (topCandidates.getFirst().getScore() >= highConfidenceScore
                && topCandidates.getFirst().getScore() - secondScore >= minTopScoreMargin) {
            item.setResolvedTag(topCandidates.getFirst());
            item.setStatus("RESOLVED");
        } else {
            item.setStatus("NEEDS_JUDGMENT");
        }
        return item;
    }

    private List<Tag> directMatches(String query, List<Tag> tags) {
        String normalizedQuery = normalize(query);
        List<Tag> exact = tags.stream().filter(tag -> normalize(tag.getName()).equals(normalizedQuery)).toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return tags.stream()
                .filter(tag -> normalizedQuery.contains(normalize(tag.getName())))
                .toList();
    }

    private boolean isCurrentVector(Tag tag, TagCategory category, String model, int dimensions) {
        return category != null && StringUtils.isNotBlank(tag.getVectorJson())
                && Objects.equals(tag.getEmbeddingModel(), model)
                && Objects.equals(tag.getEmbeddingDimensions(), dimensions)
                && Objects.equals(tag.getEmbeddingTextHash(), textHashService.sha256(textBuilder.build(category, tag)));
    }

    private TagResolutionCandidate toCandidate(Tag tag, double score) {
        return new TagResolutionCandidate(tag.getId(), tag.getName(), tag.getDescription(), score);
    }

    private String normalize(String value) {
        return StringUtils.defaultString(value).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private double normalizeCosine(double cosine) {
        return Math.max(0D, Math.min(1D, (cosine + 1D) / 2D));
    }
}
