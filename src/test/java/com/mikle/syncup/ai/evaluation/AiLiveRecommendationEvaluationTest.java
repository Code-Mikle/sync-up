package com.mikle.syncup.ai.evaluation;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.agent.UserIntent;
import com.mikle.syncup.ai.model.vo.AiUserRecommendation;
import com.mikle.syncup.ai.model.vo.HybridRecommendationResult;
import com.mikle.syncup.ai.service.recommendation.HybridRecommendationService;
import com.mikle.syncup.mapper.TagMapper;
import com.mikle.syncup.mapper.TeamMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.model.domain.Tag;
import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.TeamUserVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实查询 Embedding 和当前混合排序实现执行推荐评测。
 * 默认测试不会运行，必须显式指定 {@code -Dai.eval.live=true}。
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "ai.eval.live", matches = "true")
class AiLiveRecommendationEvaluationTest {

    private static final String TEST_DATABASE = "sync_up_test";
    private static final int TOP_K = 3;
    private static final double MIN_HIT_AT_3 = 0.80D;
    private static final ZoneId EVALUATION_ZONE = ZoneId.of("Asia/Shanghai");

    private final AiEvaluationDatasetLoader loader = new AiEvaluationDatasetLoader();

    @Resource private DataSource dataSource;
    @Resource private HybridRecommendationService recommendationService;
    @Resource private UserMapper userMapper;
    @Resource private TeamMapper teamMapper;
    @Resource private TagMapper tagMapper;

    @Test
    void evaluateRecommendationDataset() throws Exception {
        assertTestDatabase();
        JsonNode cases = loader.tree("recommendation-cases-v1.json").path("cases");
        Map<String, User> usersByAccount = evaluationUsersByAccount();
        Map<Long, String> accountsByUserId = reverseUserAccounts(usersByAccount);
        Map<Long, String> codesByTeamId = evaluationTeamCodesById();
        Map<String, Long> tagIdsByName = enabledTagIdsByName();

        List<CaseResult> results = new ArrayList<>();
        for (JsonNode testCase : cases) {
            User loginUser = usersByAccount.get(testCase.path("loginUserAccount").asText());
            if (loginUser == null) {
                throw new IllegalStateException("missing seeded login user for " + testCase.path("id").asText());
            }
            CaseResult result = "TEAM".equals(testCase.path("targetType").asText())
                    ? evaluateTeamCase(testCase, loginUser, codesByTeamId)
                    : evaluateUserCase(testCase, loginUser, accountsByUserId, tagIdsByName);
            results.add(result);
            System.out.printf("%s target=%s results=%s hit@3=%s ndcg@3=%.4f hardViolations=%d degraded=%s durationMs=%d%n",
                    result.id(), result.targetType(), result.returnedKeys(), result.hitAt3(), result.ndcgAt3(),
                    result.hardViolations(), result.degraded(), result.durationMs());
        }

        long rankedCases = results.stream().filter(result -> !result.expectEmpty()).count();
        long hits = results.stream().filter(result -> !result.expectEmpty() && result.hitAt3()).count();
        long top1Hits = results.stream().filter(result -> !result.expectEmpty() && result.top1Hit()).count();
        long hardViolations = results.stream().mapToLong(CaseResult::hardViolations).sum();
        long emptyFailures = results.stream().filter(result -> result.expectEmpty() && !result.returnedKeys().isEmpty()).count();
        double hitAt3 = rankedCases == 0 ? 1D : (double) hits / rankedCases;
        double top1 = rankedCases == 0 ? 1D : (double) top1Hits / rankedCases;
        double meanNdcg = results.stream().filter(result -> !result.expectEmpty())
                .mapToDouble(CaseResult::ndcgAt3).average().orElse(1D);

        System.out.printf("AI live recommendation v1: cases=%d, Top1=%.2f%%, Hit@3=%.2f%%, "
                        + "meanNDCG@3=%.4f, hardViolations=%d, emptyFailures=%d%n",
                results.size(), top1 * 100D, hitAt3 * 100D, meanNdcg, hardViolations, emptyFailures);

        assertEquals(0L, hardViolations, "hard-filter violations must be zero");
        assertEquals(0L, emptyFailures, "explicit no-result cases must remain empty");
        assertTrue(hitAt3 >= MIN_HIT_AT_3,
                () -> "Hit@3 must be at least 80%, missed cases: " + results.stream()
                        .filter(result -> !result.expectEmpty() && !result.hitAt3())
                        .map(CaseResult::id).toList());
    }

    private CaseResult evaluateTeamCase(JsonNode testCase, User loginUser, Map<Long, String> codesByTeamId) {
        TeamIntent intent = teamIntent(testCase);
        HybridRecommendationResult<TeamUserVO> response =
                recommendationService.recommendTeams(intent, loginUser, TOP_K);
        List<String> returned = response.items().stream()
                .map(item -> codesByTeamId.getOrDefault(item.getId(), "db-team-" + item.getId()))
                .toList();
        Set<String> forbidden = texts(testCase.path("forbiddenCodes"));
        Set<String> invalid = new HashSet<>();
        for (int index = 0; index < response.items().size(); index++) {
            TeamUserVO item = response.items().get(index);
            String key = returned.get(index);
            if (forbidden.contains(key) || violatesTeamHardConditions(item, intent)
                    || (testCase.path("expectEmpty").asBoolean(false))) {
                invalid.add(key);
            }
        }
        return result(testCase, response.degraded(), response.rankingDurationMs(), returned, invalid.size());
    }

    private CaseResult evaluateUserCase(JsonNode testCase, User loginUser,
                                        Map<Long, String> accountsByUserId,
                                        Map<String, Long> tagIdsByName) {
        UserIntent intent = userIntent(testCase, tagIdsByName);
        HybridRecommendationResult<AiUserRecommendation> response =
                recommendationService.recommendUsers(intent, loginUser, TOP_K);
        List<String> returned = response.items().stream()
                .map(item -> accountsByUserId.getOrDefault(item.getId(), "db-user-" + item.getId()))
                .toList();
        Set<String> forbidden = texts(testCase.path("forbiddenAccounts"));
        Set<String> invalid = new HashSet<>();
        for (int index = 0; index < response.items().size(); index++) {
            AiUserRecommendation item = response.items().get(index);
            String key = returned.get(index);
            if (forbidden.contains(key) || violatesUserHardConditions(item, intent, loginUser.getId())) {
                invalid.add(key);
            }
        }
        return result(testCase, response.degraded(), response.rankingDurationMs(), returned, invalid.size());
    }

    private CaseResult result(JsonNode testCase, boolean degraded, long durationMs,
                              List<String> returned, int hardViolations) {
        boolean expectEmpty = testCase.path("expectEmpty").asBoolean(false);
        Set<String> expected = new HashSet<>();
        addTexts(expected, testCase.path("expectedTopCodes"));
        addTexts(expected, testCase.path("expectedTopCodesAnyOrder"));
        addTexts(expected, testCase.path("expectedTopAccounts"));
        addTexts(expected, testCase.path("expectedTopAccountsAnyOrder"));
        boolean hitAt3 = expectEmpty ? returned.isEmpty() : returned.stream().limit(TOP_K).anyMatch(expected::contains);
        boolean top1Hit = !expectEmpty && !returned.isEmpty() && expected.contains(returned.getFirst());
        Map<String, Integer> relevance = relevance(testCase.path("relevance"));
        return new CaseResult(testCase.path("id").asText(), testCase.path("targetType").asText(),
                expectEmpty, returned, top1Hit, hitAt3, ndcgAt3(returned, relevance),
                hardViolations, degraded, durationMs);
    }

    private TeamIntent teamIntent(JsonNode testCase) {
        JsonNode value = testCase.path("intent");
        TeamIntent intent = new TeamIntent();
        intent.setSourceText(testCase.path("query").asText());
        intent.setActivityCategory(nullableInt(value, "activityCategory"));
        intent.setActivityType(nullableText(value, "activityType"));
        intent.setCity(nullableText(value, "city"));
        intent.setMemberCount(nullableInt(value, "memberCount"));
        intent.setBudgetMax(nullableDecimal(value, "budgetMax"));
        intent.setSkillLevel(nullableText(value, "skillLevel"));
        Integer startAfterDays = nullableInt(value, "startAfterDays");
        if (startAfterDays != null) {
            LocalDateTime start = LocalDate.now(EVALUATION_ZONE).plusDays(startAfterDays)
                    .atTime(LocalTime.of(15, 0));
            intent.setStartTime(Date.from(start.atZone(EVALUATION_ZONE).toInstant()));
        }
        return intent;
    }

    private UserIntent userIntent(JsonNode testCase, Map<String, Long> tagIdsByName) {
        JsonNode value = testCase.path("intent");
        UserIntent intent = new UserIntent();
        intent.setSourceText(testCase.path("query").asText());
        intent.setCity(nullableText(value, "city"));
        intent.setGender(nullableInt(value, "gender"));
        intent.setProfile(nullableText(value, "profile"));
        List<Long> tagIds = new ArrayList<>();
        for (JsonNode tagName : value.path("tagNames")) {
            Long tagId = tagIdsByName.get(tagName.asText());
            if (tagId == null) {
                throw new IllegalStateException("missing enabled tag: " + tagName.asText());
            }
            tagIds.add(tagId);
        }
        intent.setTagIds(tagIds);
        return intent;
    }

    private boolean violatesTeamHardConditions(TeamUserVO item, TeamIntent intent) {
        if (item.getStatus() == null || item.getStatus() != 0) return true;
        if (item.getExpireTime() != null && item.getExpireTime().before(new Date())) return true;
        if (intent.getActivityCategory() != null
                && !intent.getActivityCategory().equals(item.getActivityCategory())) return true;
        if (intent.getActivityType() != null && !intent.getActivityType().equals(item.getActivityType())) return true;
        if (intent.getCity() != null && !intent.getCity().equals(item.getCity())) return true;
        if (intent.getBudgetMax() != null && item.getBudgetPerPerson() != null
                && item.getBudgetPerPerson().compareTo(intent.getBudgetMax()) > 0) return true;
        if (intent.getSkillLevel() != null && !intent.getSkillLevel().equals(item.getSkillLevel())) return true;
        int requiredSlots = intent.getMemberCount() == null ? 1 : Math.max(1, intent.getMemberCount());
        if (item.getMaxNum() == null || item.getHasJoinNum() == null
                || item.getMaxNum() - item.getHasJoinNum() < requiredSlots) return true;
        if (intent.getStartTime() != null && (item.getStartTime() == null
                || Math.abs(item.getStartTime().getTime() - intent.getStartTime().getTime()) > 3 * 60 * 60 * 1000L)) {
            return true;
        }
        return false;
    }

    private boolean violatesUserHardConditions(AiUserRecommendation item, UserIntent intent, long loginUserId) {
        if (item.getId() == null || item.getId() == loginUserId) return true;
        if (intent.getCity() != null && !intent.getCity().equals(item.getCity())) return true;
        if (intent.getGender() != null && !intent.getGender().equals(item.getGender())) return true;
        if (!intent.getTagIds().isEmpty()) {
            Set<String> requiredNames = new HashSet<>();
            List<Tag> requiredTags = tagMapper.selectBatchIds(intent.getTagIds());
            requiredTags.forEach(tag -> requiredNames.add(tag.getName()));
            if (item.getTagNames() == null || item.getTagNames().stream().noneMatch(requiredNames::contains)) return true;
        }
        return false;
    }

    private Map<String, User> evaluationUsersByAccount() {
        List<String> accounts = loader.users().users().stream().map(AiEvaluationDatasetLoader.UserSeed::userAccount).toList();
        List<User> users = userMapper.selectList(new QueryWrapper<User>().in("userAccount", accounts));
        Map<String, User> result = new LinkedHashMap<>();
        users.forEach(user -> result.put(user.getUserAccount(), user));
        assertEquals(accounts.size(), result.size(), "evaluation users must be initialized before live evaluation");
        return result;
    }

    private Map<Long, String> reverseUserAccounts(Map<String, User> usersByAccount) {
        Map<Long, String> result = new HashMap<>();
        usersByAccount.forEach((account, user) -> result.put(user.getId(), account));
        return result;
    }

    private Map<Long, String> evaluationTeamCodesById() {
        Map<String, String> codeByName = new HashMap<>();
        loader.teams().teams().forEach(seed -> codeByName.put(seed.name(), seed.code()));
        List<Team> teams = teamMapper.selectList(new QueryWrapper<Team>().in("name", codeByName.keySet()));
        Map<Long, String> result = new HashMap<>();
        teams.forEach(team -> result.put(team.getId(), codeByName.get(team.getName())));
        assertEquals(codeByName.size(), result.size(), "evaluation teams must be initialized before live evaluation");
        return result;
    }

    private Map<String, Long> enabledTagIdsByName() {
        List<Tag> tags = tagMapper.selectList(new QueryWrapper<Tag>().eq("status", 1));
        Map<String, Long> result = new HashMap<>();
        tags.forEach(tag -> result.put(tag.getName(), tag.getId()));
        return result;
    }

    private double ndcgAt3(List<String> returned, Map<String, Integer> relevance) {
        double dcg = 0D;
        for (int index = 0; index < Math.min(TOP_K, returned.size()); index++) {
            int grade = relevance.getOrDefault(returned.get(index), 0);
            dcg += (Math.pow(2D, grade) - 1D) / log2(index + 2D);
        }
        List<Integer> ideal = relevance.values().stream().sorted(java.util.Comparator.reverseOrder()).limit(TOP_K).toList();
        double idealDcg = 0D;
        for (int index = 0; index < ideal.size(); index++) {
            idealDcg += (Math.pow(2D, ideal.get(index)) - 1D) / log2(index + 2D);
        }
        return idealDcg == 0D ? 1D : dcg / idealDcg;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }

    private Map<String, Integer> relevance(JsonNode node) {
        Map<String, Integer> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asInt()));
        return result;
    }

    private Set<String> texts(JsonNode array) {
        Set<String> result = new HashSet<>();
        addTexts(result, array);
        return result;
    }

    private void addTexts(Set<String> target, JsonNode array) {
        if (array.isArray()) array.forEach(value -> target.add(value.asText()));
    }

    private String nullableText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private Integer nullableInt(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private BigDecimal nullableDecimal(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private void assertTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(TEST_DATABASE, connection.getCatalog(),
                    "当前连接的不是 sync_up_test，已停止真实推荐评测");
        }
    }

    private record CaseResult(String id, String targetType, boolean expectEmpty,
                              List<String> returnedKeys, boolean top1Hit, boolean hitAt3,
                              double ndcgAt3, int hardViolations, boolean degraded, long durationMs) {
    }
}
