package com.mikle.syncup.ai.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.enums.EpisodePriority;
import com.mikle.syncup.ai.model.enums.EpisodeSignalType;
import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.model.schema.GeneratedEpisode;
import com.mikle.syncup.ai.model.schema.GeneratedEpisodeExtraction;
import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;
import com.mikle.syncup.ai.service.EpisodeExtractor;
import com.mikle.syncup.ai.service.ProfileDimensionGenerator;
import com.mikle.syncup.ai.service.SessionSummaryGenerator;
import com.mikle.syncup.ai.service.UserProfileTextAssembler;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实调用生产 Episode、Summary 和分维度画像生成器的记忆质量评测。
 * 默认不运行，必须显式指定 {@code -Dai.eval.live.memory=true}。
 */
@SpringBootTest(properties = {
        "sync-up.ai.agent.log-requests=false",
        "sync-up.ai.agent.log-responses=false"
})
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "ai.eval.live.memory", matches = "true")
class AiLiveMemoryEvaluationTest {

    private static final double MIN_EPISODE_FACT_COVERAGE = 0.90D;
    private static final double MIN_SUMMARY_FACT_COVERAGE = 0.85D;
    private static final double MIN_PROFILE_FACT_COVERAGE = 0.90D;
    private static final String UNKNOWN = "暂未观察到明确偏好";
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?is).*(1[3-9]\\d{9}|[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}|(?:token|api[_-]?key|password|密码)\\s*[:：=]).*");

    private final AiEvaluationDatasetLoader loader = new AiEvaluationDatasetLoader();

    @Resource private AiAgentProperties agentProperties;
    @Resource private EpisodeExtractor episodeExtractor;
    @Resource private SessionSummaryGenerator summaryGenerator;
    @Resource private ProfileDimensionGenerator dimensionGenerator;
    @Resource private UserProfileTextAssembler profileAssembler;

    @Test
    void evaluateMemoryDataset() {
        assertTrue(agentProperties.available(), "真实生成模型未启用，请检查模型名和 API Key");
        assertTrue(episodeExtractor.isAvailable(), "EpisodeExtractor unavailable");
        assertTrue(summaryGenerator.isAvailable(), "SessionSummaryGenerator unavailable");
        assertTrue(dimensionGenerator.isAvailable(), "ProfileDimensionGenerator unavailable");
        System.out.printf("AI live memory config: dataset=memory-cases-v1, provider=%s, model=%s, "
                        + "episodePrompt=%s, summaryPrompt=%s, profilePrompt=%s%n",
                agentProperties.getProvider(), agentProperties.getModel(), episodeExtractor.promptVersion(),
                summaryGenerator.promptVersion(), dimensionGenerator.promptVersion());

        Set<String> selectedIds = selectedCaseIds();
        List<CaseResult> results = new ArrayList<>();
        int caseIndex = 0;
        for (JsonNode testCase : loader.tree("memory-cases-v1.json").path("cases")) {
            caseIndex++;
            if (!selectedIds.isEmpty() && !selectedIds.contains(testCase.path("id").asText())) continue;
            CaseResult result = evaluateCase(testCase, caseIndex);
            results.add(result);
            System.out.printf("%s episodes=%d count=%s structure=%s source=%s episodeFacts=%d/%d "
                            + "summaryFacts=%d/%d profileFacts=%d/%d safety=%s issues=%s%n",
                    result.id(), result.episodeCount(), result.countPass(), result.structurePass(),
                    result.sourcePass(), result.episodeFactsMatched(), result.episodeFactsTotal(),
                    result.summaryFactsMatched(), result.summaryFactsTotal(),
                    result.profileFactsMatched(), result.profileFactsTotal(),
                    result.safetyPass(), result.issues());
            if (!result.issues().isEmpty()) {
                System.out.printf("%s episodeText=%s%n", result.id(), abbreviate(result.episodeText()));
                if (!result.summaryText().isBlank()) {
                    System.out.printf("%s summary=%s%n", result.id(), abbreviate(result.summaryText()));
                }
                if (!result.profileText().isBlank()) {
                    System.out.printf("%s profile=%s%n", result.id(), abbreviate(result.profileText()));
                }
            }
        }
        assertTrue(!results.isEmpty(), "没有找到 ai.eval.memory.cases 指定的用例");

        long countPassed = results.stream().filter(CaseResult::countPass).count();
        long structurePassed = results.stream().filter(CaseResult::structurePass).count();
        long sourcePassed = results.stream().filter(CaseResult::sourcePass).count();
        long profileTypePassed = results.stream().filter(CaseResult::profileTypePass).count();
        long safetyViolations = results.stream().filter(result -> !result.safetyPass()).count();
        long correctionCases = results.stream().filter(CaseResult::correctionApplicable).count();
        long correctionPassed = results.stream().filter(CaseResult::correctionApplicable)
                .filter(CaseResult::correctionPass).count();
        double episodeCoverage = ratio(sum(results, CaseResult::episodeFactsMatched),
                sum(results, CaseResult::episodeFactsTotal));
        double summaryCoverage = ratio(sum(results, CaseResult::summaryFactsMatched),
                sum(results, CaseResult::summaryFactsTotal));
        double profileCoverage = ratio(sum(results, CaseResult::profileFactsMatched),
                sum(results, CaseResult::profileFactsTotal));

        System.out.printf(Locale.ROOT,
                "AI live memory v1: cases=%d, episodeCount=%.2f%%, structure=%.2f%%, source=%.2f%%, "
                        + "profileTypes=%.2f%%, episodeFacts=%.2f%%, summaryFacts=%.2f%%, "
                        + "profileFacts=%.2f%%, correction=%.2f%%, safetyViolations=%d%n",
                results.size(), ratio(countPassed, results.size()) * 100D,
                ratio(structurePassed, results.size()) * 100D,
                ratio(sourcePassed, results.size()) * 100D,
                ratio(profileTypePassed, results.size()) * 100D,
                episodeCoverage * 100D, summaryCoverage * 100D, profileCoverage * 100D,
                ratio(correctionPassed, correctionCases) * 100D, safetyViolations);

        assertEquals(results.size(), countPassed,
                () -> "Episode 数量边界不正确: " + failedIds(results, result -> !result.countPass()));
        assertEquals(results.size(), structurePassed,
                () -> "Episode 结构合法率必须为 100%: " + failedIds(results, result -> !result.structurePass()));
        assertEquals(results.size(), sourcePassed,
                () -> "Episode 来源引用正确率必须为 100%: " + failedIds(results, result -> !result.sourcePass()));
        assertEquals(results.size(), profileTypePassed,
                () -> "画像更新维度必须与证据一致: " + failedIds(results, result -> !result.profileTypePass()));
        assertEquals(0L, safetyViolations,
                () -> "记忆敏感信息或禁止事实违规必须为 0: " + failedIds(results, result -> !result.safetyPass()));
        assertEquals(correctionCases, correctionPassed,
                () -> "明确纠正必须正确关联旧证据: " + failedIds(results,
                        result -> result.correctionApplicable() && !result.correctionPass()));
        if (selectedIds.isEmpty()) {
            assertTrue(episodeCoverage >= MIN_EPISODE_FACT_COVERAGE,
                    () -> "Episode 必要事实覆盖率必须至少 90%: " + factFailures(results, FactStage.EPISODE));
            assertTrue(summaryCoverage >= MIN_SUMMARY_FACT_COVERAGE,
                    () -> "Summary 必要事实覆盖率必须至少 85%: " + factFailures(results, FactStage.SUMMARY));
            assertTrue(profileCoverage >= MIN_PROFILE_FACT_COVERAGE,
                    () -> "画像必要事实覆盖率必须至少 90%: " + factFailures(results, FactStage.PROFILE));
        }
    }

    private CaseResult evaluateCase(JsonNode testCase, int caseIndex) {
        String id = testCase.path("id").asText();
        List<MessageFixture> messages = messages(testCase.path("messages"), caseIndex);
        ExistingEpisodes existing = existingEpisodes(testCase.path("existingEpisodes"), caseIndex);

        GeneratedEpisodeExtraction extraction = episodeExtractor.extract(
                extractionInput(testCase, messages, existing.episodes()));
        List<GeneratedEpisode> episodes = extraction == null || extraction.getEpisodes() == null
                ? List.of() : extraction.getEpisodes();
        String episodeText = episodes.stream().map(GeneratedEpisode::getContent)
                .filter(java.util.Objects::nonNull).reduce("", (left, right) -> left + "；" + right);
        JsonNode episodeExpectation = testCase.path("episodeExpectation");
        List<String> issues = new ArrayList<>();

        int minCount = episodeExpectation.path("minCount").asInt();
        int maxCount = episodeExpectation.path("maxCount").asInt(Integer.MAX_VALUE);
        boolean countPass = episodes.size() >= minCount && episodes.size() <= maxCount;
        if (!countPass) issues.add("episode count");
        boolean structurePass = episodes.stream().allMatch(this::validEpisodeStructure);
        if (!structurePass) issues.add("episode structure");
        boolean sourcePass = sourceMatches(
                testCase.path("sourceType").asText(), episodeExpectation, episodes, messages);
        if (!sourcePass) issues.add("episode source");

        Set<String> actualTypes = values(episodes, GeneratedEpisode::getProfileType);
        Set<String> requiredTypes = texts(episodeExpectation.path("requiredProfileTypes"));
        boolean episodeTypesPass = actualTypes.containsAll(requiredTypes);
        Set<String> allowedSignals = texts(episodeExpectation.path("allowedSignalTypes"));
        Set<String> requiredSignals = texts(episodeExpectation.path("requiredSignalTypes"));
        Set<String> actualSignals = values(episodes, GeneratedEpisode::getSignalType);
        boolean signalsPass = (allowedSignals.isEmpty() || allowedSignals.containsAll(actualSignals))
                && actualSignals.containsAll(requiredSignals);
        if (!episodeTypesPass) issues.add("episode profile types");
        if (!signalsPass) issues.add("episode signal types");

        FactScore episodeFacts = factScore(episodeText, episodeExpectation.path("requiredFacts"));
        addMissingIssues(issues, "episode fact", episodeFacts.missing());
        Set<String> episodeForbidden = matchingFacts(episodeText, episodeExpectation.path("forbiddenFacts"));
        boolean episodeSensitive = containsSensitive(episodeText);
        if (!episodeForbidden.isEmpty()) issues.add("episode forbidden " + episodeForbidden);
        if (episodeSensitive) issues.add("episode sensitive data");

        boolean correctionApplicable = episodeExpectation.path("mustSupersedeEpisodeKeys").isArray();
        boolean correctionPass = correctionMatches(
                episodeExpectation.path("mustSupersedeEpisodeKeys"), episodes, existing.idsByKey());
        if (correctionApplicable && !correctionPass) issues.add("correction supersession");

        SummaryResult summary = summary(testCase, messages);
        addMissingIssues(issues, "summary fact", summary.facts().missing());
        if (!summary.forbidden().isEmpty()) issues.add("summary forbidden " + summary.forbidden());
        if (summary.sensitive()) issues.add("summary sensitive data");

        ProfileResult profile = profile(testCase, episodes, existing.episodes());
        addMissingIssues(issues, "profile fact", profile.facts().missing());
        if (!profile.forbidden().isEmpty()) issues.add("profile forbidden " + profile.forbidden());
        if (profile.sensitive()) issues.add("profile sensitive data");
        if (!profile.typesPass()) issues.add("profile updated types");
        if (!profile.boundaryPass()) issues.add("interaction/matching boundary");

        boolean safetyPass = episodeForbidden.isEmpty() && !episodeSensitive
                && summary.forbidden().isEmpty() && !summary.sensitive()
                && profile.forbidden().isEmpty() && !profile.sensitive() && profile.boundaryPass();
        return new CaseResult(id, episodes.size(), countPass, structurePass && signalsPass,
                sourcePass, profile.typesPass(), correctionApplicable, correctionPass, safetyPass,
                episodeFacts.matched(), episodeFacts.total(), summary.facts().matched(),
                summary.facts().total(), profile.facts().matched(), profile.facts().total(),
                episodeText, summary.text(), profile.text(), issues);
    }

    private SummaryResult summary(JsonNode testCase, List<MessageFixture> messages) {
        JsonNode expectation = testCase.path("summaryExpectation");
        if (expectation.path("notApplicable").asBoolean(false)) {
            return new SummaryResult("", new FactScore(0, 0, List.of()), Set.of(), false);
        }
        StringBuilder input = new StringBuilder("旧摘要：\n暂无\n\n新增原始消息：");
        for (MessageFixture message : messages) {
            input.append('\n').append('[')
                    .append("user".equals(message.role()) ? "用户" : "assistant".equals(message.role()) ? "助手" : "消息")
                    .append("] ").append(message.content());
        }
        String generated = summaryGenerator.summarize(input.toString());
        String text = generated == null ? "" : generated.trim();
        return new SummaryResult(text, factScore(text, expectation.path("requiredFacts")),
                matchingFacts(text, expectation.path("forbiddenFacts")), containsSensitive(text));
    }

    private ProfileResult profile(JsonNode testCase,
                                  List<GeneratedEpisode> generatedEpisodes,
                                  List<AiUserEpisode> existingEpisodes) {
        JsonNode expectation = testCase.path("profileExpectation");
        Set<String> expectedTypes = texts(expectation.path("updatedProfileTypes"));
        Set<String> allowedTypes = texts(expectation.path("allowedUpdatedProfileTypes"));
        if (allowedTypes.isEmpty()) allowedTypes = expectedTypes;
        Set<String> actualTypes = values(generatedEpisodes, GeneratedEpisode::getProfileType);
        boolean typesPass = actualTypes.containsAll(expectedTypes) && allowedTypes.containsAll(actualTypes);
        Map<ProfileType, String> dimensions = defaultDimensions();
        for (String typeName : actualTypes) {
            ProfileType type;
            try {
                type = ProfileType.valueOf(typeName);
            } catch (RuntimeException ignored) {
                continue;
            }
            List<AiUserEpisode> evidence = new ArrayList<>();
            existingEpisodes.stream().filter(episode -> typeName.equals(episode.getProfileType()))
                    .forEach(evidence::add);
            generatedEpisodes.stream().filter(episode -> typeName.equals(episode.getProfileType()))
                    .map(this::toEpisode).forEach(evidence::add);
            dimensions.put(type, dimensionGenerator.generate(type, dimensions.get(type), evidence));
        }
        GeneratedUserProfile generated = new GeneratedUserProfile(
                dimensions.get(ProfileType.ACTIVITY_PREFERENCE),
                dimensions.get(ProfileType.SOCIAL_PERSONALITY),
                dimensions.get(ProfileType.PARTNER_PREFERENCE),
                dimensions.get(ProfileType.ACTIVITY_CONSTRAINT_HABIT),
                dimensions.get(ProfileType.AI_INTERACTION_PREFERENCE));
        String fullText = profileAssembler.renderFull(generated);
        boolean boundaryPass = true;
        if (expectation.path("mustNotAffectMatchingText").asBoolean(false)) {
            String interaction = dimensions.get(ProfileType.AI_INTERACTION_PREFERENCE);
            boundaryPass = !profileAssembler.renderMatch(generated).contains(interaction)
                    && profileAssembler.renderInteraction(generated).contains(interaction);
        }
        return new ProfileResult(fullText, factScore(fullText, expectation.path("requiredFacts")),
                matchingFacts(fullText, expectation.path("forbiddenFacts")),
                containsSensitive(fullText), typesPass, boundaryPass);
    }

    private AiUserEpisode toEpisode(GeneratedEpisode generated) {
        AiUserEpisode episode = new AiUserEpisode();
        episode.setProfileType(generated.getProfileType());
        episode.setContent(generated.getContent());
        episode.setSignalType(generated.getSignalType());
        episode.setPriority(generated.getPriority());
        return episode;
    }

    private Map<ProfileType, String> defaultDimensions() {
        Map<ProfileType, String> result = new EnumMap<>(ProfileType.class);
        for (ProfileType type : ProfileType.values()) result.put(type, UNKNOWN);
        return result;
    }

    private boolean validEpisodeStructure(GeneratedEpisode episode) {
        if (episode == null || episode.getContent() == null || episode.getContent().isBlank()
                || episode.getContent().trim().length() > 160) return false;
        try {
            ProfileType.valueOf(episode.getProfileType());
            EpisodeSignalType.valueOf(episode.getSignalType());
            EpisodePriority.valueOf(episode.getPriority());
        } catch (RuntimeException ignored) {
            return false;
        }
        return episode.getSourceMessageIds() != null && episode.getSupersededEpisodeIds() != null;
    }

    private boolean sourceMatches(String sourceType,
                                  JsonNode expectation,
                                  List<GeneratedEpisode> episodes,
                                  List<MessageFixture> messages) {
        Map<String, Long> messageIds = new HashMap<>();
        messages.forEach(message -> messageIds.put(message.key(), message.id()));
        Set<Long> referenced = new HashSet<>();
        episodes.forEach(episode -> {
            if (episode.getSourceMessageIds() != null) referenced.addAll(episode.getSourceMessageIds());
        });
        Set<Long> userIds = messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(MessageFixture::id)
                .collect(java.util.stream.Collectors.toSet());
        if ("SELF_INTRODUCTION".equals(sourceType)) return referenced.isEmpty();
        for (JsonNode message : expectation.path("mustReferenceMessageKeys")) {
            Long id = messageIds.get(message.asText());
            if (id == null || !referenced.contains(id)) return false;
        }
        boolean anyRequired = expectation.path("mustReferenceAnyMessageKeys").isArray();
        boolean anyMatched = !anyRequired;
        for (JsonNode message : expectation.path("mustReferenceAnyMessageKeys")) {
            Long id = messageIds.get(message.asText());
            if (id != null && referenced.contains(id)) anyMatched = true;
        }
        for (JsonNode message : expectation.path("mustNotReferenceMessageKeys")) {
            Long id = messageIds.get(message.asText());
            if (id != null && referenced.contains(id)) return false;
        }
        return anyMatched && referenced.stream().allMatch(userIds::contains);
    }

    private boolean correctionMatches(JsonNode expectedKeys,
                                      List<GeneratedEpisode> episodes,
                                      Map<String, Long> idsByKey) {
        if (!expectedKeys.isArray()) return true;
        Set<Long> superseded = new HashSet<>();
        episodes.stream().filter(episode -> EpisodeSignalType.CORRECTION.name().equals(episode.getSignalType()))
                .forEach(episode -> {
                    if (episode.getSupersededEpisodeIds() != null) superseded.addAll(episode.getSupersededEpisodeIds());
                });
        for (JsonNode key : expectedKeys) {
            Long id = idsByKey.get(key.asText());
            if (id == null || !superseded.contains(id)) return false;
        }
        return true;
    }

    private String extractionInput(JsonNode testCase,
                                   List<MessageFixture> messages,
                                   List<AiUserEpisode> existingEpisodes) {
        StringBuilder builder = new StringBuilder();
        if ("SELF_INTRODUCTION".equals(testCase.path("sourceType").asText())) {
            builder.append("来源：用户自我介绍\n").append(testCase.path("sourceText").asText());
        } else {
            builder.append("来源：聊天记录\n");
            for (MessageFixture message : messages) {
                builder.append("[id=").append(message.id()).append("][role=")
                        .append(message.role()).append("] ").append(message.content()).append('\n');
            }
        }
        if (!existingEpisodes.isEmpty()) {
            builder.append("\n可纠正的历史证据（仅供明确纠正时引用）：");
            for (AiUserEpisode episode : existingEpisodes) {
                builder.append("\n[episodeId=").append(episode.getId()).append("][profileType=")
                        .append(episode.getProfileType()).append("] ").append(episode.getContent());
            }
        }
        return builder.toString();
    }

    private List<MessageFixture> messages(JsonNode nodes, int caseIndex) {
        List<MessageFixture> result = new ArrayList<>();
        int messageIndex = 0;
        for (JsonNode node : nodes) {
            messageIndex++;
            result.add(new MessageFixture(node.path("key").asText(),
                    caseIndex * 100L + messageIndex, node.path("role").asText(), node.path("content").asText()));
        }
        return result;
    }

    private ExistingEpisodes existingEpisodes(JsonNode nodes, int caseIndex) {
        List<AiUserEpisode> episodes = new ArrayList<>();
        Map<String, Long> ids = new HashMap<>();
        int index = 0;
        for (JsonNode node : nodes) {
            index++;
            long id = 900_000L + caseIndex * 100L + index;
            AiUserEpisode episode = new AiUserEpisode();
            episode.setId(id);
            episode.setProfileType(node.path("profileType").asText());
            episode.setContent(node.path("content").asText());
            episode.setSignalType(node.path("signalType").asText());
            episode.setStatus(node.path("status").asText());
            episode.setPriority(EpisodePriority.NORMAL.name());
            episodes.add(episode);
            ids.put(node.path("key").asText(), id);
        }
        return new ExistingEpisodes(episodes, ids);
    }

    private FactScore factScore(String actual, JsonNode expectedFacts) {
        int matched = 0;
        List<String> missing = new ArrayList<>();
        for (JsonNode expected : expectedFacts) {
            if (matchesFact(actual, expected.asText())) matched++;
            else missing.add(expected.asText());
        }
        return new FactScore(matched, expectedFacts.isArray() ? expectedFacts.size() : 0, missing);
    }

    private Set<String> matchingFacts(String actual, JsonNode forbiddenFacts) {
        Set<String> matched = new LinkedHashSet<>();
        for (JsonNode forbidden : forbiddenFacts) {
            if (containsWithCompatiblePolarity(normalize(actual), normalize(forbidden.asText()))) {
                matched.add(forbidden.asText());
            }
        }
        return matched;
    }

    /**
     * 只做保守的中文概念归一化。先尝试整句包含，再检查数据集中的核心概念组合；
     * 不调用第二个模型裁判，未覆盖的改写留给人工复核。
     */
    private boolean matchesFact(String actual, String expected) {
        String text = normalize(actual);
        String fact = normalize(expected);
        if (containsWithCompatiblePolarity(text, fact)) return true;
        List<Set<String>> anchors = new ArrayList<>();
        addAnchor(fact, anchors, "羽毛球", "羽毛球");
        addAnchor(fact, anchors, "徒步", "徒步", "路线");
        addAnchor(fact, anchors, "爬山", "爬山", "登山");
        addAnchor(fact, anchors, "城市漫步", "城市漫步", "城区散步", "城市散步");
        addAnchor(fact, anchors, "瑜伽", "瑜伽");
        addAnchor(fact, anchors, "读书会", "读书会", "阅读活动");
        addAnchor(fact, anchors, "周末", "周末", "每周末");
        addAnchor(fact, anchors, "周六下午", "周六下午", "星期六下午");
        addAnchor(fact, anchors, "两小时", "两小时", "2小时", "120分钟");
        addAnchor(fact, anchors, "80元", "80元", "八十元");
        addAnchor(fact, anchors, "三个选项", "三个选项", "3个选项", "三个最合适");
        addAnchor(fact, anchors, "三四人", "三四人", "3到4人", "3-4人", "小队");
        addAnchor(fact, anchors, "慢热", "慢热");
        addAnchor(fact, anchors, "守时", "守时", "准时");
        addAnchor(fact, anchors, "小队", "小队", "小规模");
        addAnchor(fact, anchors, "简短", "简短", "简洁");
        addAnchor(fact, anchors, "原理", "原理", "解释");
        addAnchor(fact, anchors, "竞速", "竞速", "冲速度", "追求速度");
        addAnchor(fact, anchors, "速度", "竞速", "冲速度", "追求速度", "速度");
        addAnchor(fact, anchors, "高强度", "高强度", "高强度路线");
        addAnchor(fact, anchors, "专业运动员", "专业运动员", "专业选手");
        addAnchor(fact, anchors, "大型社交", "大型社交", "大群", "十几个人");
        addAnchor(fact, anchors, "外向", "外向");
        addAnchor(fact, anchors, "工作日", "工作日");
        addAnchor(fact, anchors, "预算不限", "预算不限", "不限制预算");
        int domainAnchorCount = anchors.size();
        if (containsAny(fact, "不", "只", "以内", "不能超过", "尚未", "少讲")) {
            anchors.add(Set.of("不", "不会", "只", "仅", "以内", "最多", "上限", "尚未", "未决定",
                    "避免", "排除", "排斥", "拒绝", "少"));
        }
        if (containsAny(fact, "喜欢", "偏好", "愿意", "经常")) {
            anchors.add(Set.of("喜欢", "偏好", "愿意", "经常", "常", "想", "优先"));
        }
        if (fact.contains("纠正")) anchors.add(Set.of("纠正", "说错", "改为", "更新"));
        return domainAnchorCount > 0
                && anchors.stream().allMatch(group -> group.stream().anyMatch(text::contains));
    }

    private boolean containsWithCompatiblePolarity(String text, String fact) {
        int fromIndex = 0;
        while (fromIndex <= text.length() - fact.length()) {
            int index = text.indexOf(fact, fromIndex);
            if (index < 0) return false;
            if (expectsNegativeOrRestrictiveMeaning(fact)) return true;
            String prefix = text.substring(Math.max(0, index - 3), index);
            if (!containsAny(prefix, "不", "未", "无", "避免", "拒绝")) return true;
            fromIndex = index + Math.max(1, fact.length());
        }
        return false;
    }

    private boolean expectsNegativeOrRestrictiveMeaning(String fact) {
        return containsAny(fact, "不", "未", "无", "只", "仅", "以内", "不能超过", "少讲", "尚未");
    }

    private void addAnchor(String fact, List<Set<String>> anchors, String marker, String... alternatives) {
        if (fact.contains(marker)) anchors.add(Set.of(alternatives));
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replace("用户", "").replaceAll("[\\s，。；：、,.!！?？\"'（）()【】*#]", "");
    }

    private boolean containsSensitive(String text) {
        return text != null && !text.isBlank() && SENSITIVE.matcher(text).matches();
    }

    private <T> Set<String> values(List<T> values, java.util.function.Function<T, String> mapper) {
        Set<String> result = new LinkedHashSet<>();
        for (T value : values) {
            String mapped = mapper.apply(value);
            if (mapped != null) result.add(mapped);
        }
        return result;
    }

    private Set<String> texts(JsonNode array) {
        Set<String> result = new LinkedHashSet<>();
        if (array.isArray()) array.forEach(node -> result.add(node.asText()));
        return result;
    }

    private Set<String> selectedCaseIds() {
        String configured = System.getProperty("ai.eval.memory.cases", "").trim();
        if (configured.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (String value : configured.split(",")) if (!value.isBlank()) result.add(value.trim());
        return result;
    }

    private void addMissingIssues(List<String> issues, String prefix, List<String> missing) {
        if (!missing.isEmpty()) issues.add(prefix + " " + missing);
    }

    private long sum(List<CaseResult> results, java.util.function.ToIntFunction<CaseResult> mapper) {
        return results.stream().mapToInt(mapper).sum();
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 1D : (double) numerator / denominator;
    }

    private List<String> failedIds(List<CaseResult> results,
                                   java.util.function.Predicate<CaseResult> predicate) {
        return results.stream().filter(predicate).map(CaseResult::id).toList();
    }

    private Map<String, List<String>> factFailures(List<CaseResult> results, FactStage stage) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (CaseResult item : results) {
            List<String> issues = item.issues().stream()
                    .filter(issue -> issue.startsWith(stage.prefix)).toList();
            if (!issues.isEmpty()) result.put(item.id(), issues);
        }
        return result;
    }

    private String abbreviate(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private enum FactStage {
        EPISODE("episode fact"), SUMMARY("summary fact"), PROFILE("profile fact");
        private final String prefix;
        FactStage(String prefix) { this.prefix = prefix; }
    }

    private record MessageFixture(String key, long id, String role, String content) {}
    private record ExistingEpisodes(List<AiUserEpisode> episodes, Map<String, Long> idsByKey) {}
    private record FactScore(int matched, int total, List<String> missing) {}
    private record SummaryResult(String text, FactScore facts, Set<String> forbidden, boolean sensitive) {}
    private record ProfileResult(String text, FactScore facts, Set<String> forbidden,
                                 boolean sensitive, boolean typesPass, boolean boundaryPass) {}
    private record CaseResult(String id, int episodeCount, boolean countPass, boolean structurePass,
                              boolean sourcePass, boolean profileTypePass,
                              boolean correctionApplicable, boolean correctionPass, boolean safetyPass,
                              int episodeFactsMatched, int episodeFactsTotal,
                              int summaryFactsMatched, int summaryFactsTotal,
                              int profileFactsMatched, int profileFactsTotal,
                              String episodeText, String summaryText, String profileText,
                              List<String> issues) {}
}
