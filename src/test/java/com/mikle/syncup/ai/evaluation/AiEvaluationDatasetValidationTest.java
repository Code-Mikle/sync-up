package com.mikle.syncup.ai.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.mikle.syncup.model.enums.TeamActivityCategoryEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEvaluationDatasetValidationTest {

    private static final Pattern CONTROLLED_TAG_PATTERN = Pattern.compile(
            "\\(\\d+,\\s*\\d+,\\s*'[^']+',\\s*'([^']+)'");
    private static final Pattern TEAM_REFERENCE_PATTERN = Pattern.compile(
            "team-(?:xian|chengdu|beijing|shanghai|hangzhou)-[a-z0-9-]+");
    private static final Pattern USER_REFERENCE_PATTERN = Pattern.compile(
            "eval_(?:runner|user)_[a-z0-9_]+");
    private static final Set<String> PROFILE_TYPES = Set.of(
            "ACTIVITY_PREFERENCE", "SOCIAL_PERSONALITY", "PARTNER_PREFERENCE",
            "ACTIVITY_CONSTRAINT_HABIT", "AI_INTERACTION_PREFERENCE");
    private static final Set<String> KNOWN_TOOLS = Set.of(
            "search_teams", "resolve_tags", "search_users", "create_team_draft",
            "delete_team_confirmation", "list_my_created_teams", "get_team_details",
            "list_my_joined_teams", "get_my_profile", "show_my_profile_card",
            "delete_team", "join_team", "quit_team");
    private static final Map<String, Integer> CATEGORY_BY_ACTIVITY_TYPE = Map.of(
            "羽毛球", 1, "健身", 1, "徒步", 2, "滑雪", 2, "棋牌", 4,
            "咖啡馆打卡", 6, "英语角", 7, "读书会", 7, "城市漫步", 8);

    private final AiEvaluationDatasetLoader loader = new AiEvaluationDatasetLoader();

    @Test
    void datasets_shouldParseWithExpectedVersionsAndSizes() {
        assertEquals("users-v1", loader.users().datasetVersion());
        assertEquals("user-profiles-v1", loader.profiles().datasetVersion());
        assertEquals("teams-v1", loader.teams().datasetVersion());
        assertEquals("agent-cases-v1", loader.tree("agent-cases-v1.json").path("datasetVersion").asText());
        assertEquals("memory-cases-v1", loader.tree("memory-cases-v1.json").path("datasetVersion").asText());
        assertEquals("recommendation-cases-v1",
                loader.tree("recommendation-cases-v1.json").path("datasetVersion").asText());

        assertEquals(16, loader.users().users().size());
        assertEquals(16, loader.profiles().profiles().size());
        assertEquals(33, loader.teams().teams().size());
        assertEquals(20, loader.tree("agent-cases-v1.json").path("cases").size());
        assertEquals(10, loader.tree("memory-cases-v1.json").path("cases").size());
        assertEquals(14, loader.tree("recommendation-cases-v1.json").path("cases").size());
    }

    @Test
    void usersAndProfiles_shouldUseUniqueAccountsAndControlledTags() {
        var users = loader.users().users();
        var profiles = loader.profiles().profiles();
        Set<String> userAccounts = new LinkedHashSet<>();
        Set<String> profileAccounts = new LinkedHashSet<>();
        Set<String> controlledTags = controlledTags();

        for (var user : users) {
            assertTrue(userAccounts.add(user.userAccount()), "duplicate userAccount: " + user.userAccount());
            assertTrue(user.userAccount().startsWith("eval_"), "evaluation account must start with eval_");
            assertTrue(user.gender() >= 0 && user.gender() <= 2, "invalid gender: " + user.userAccount());
            assertFalse(user.city().isBlank(), "city is required: " + user.userAccount());
            assertFalse(user.tags().isEmpty(), "at least one tag is required: " + user.userAccount());
            for (String tag : user.tags()) {
                assertTrue(controlledTags.contains(tag), "uncontrolled tag " + tag + " in " + user.userAccount());
            }
        }

        for (var profile : profiles) {
            assertTrue(profileAccounts.add(profile.userAccount()),
                    "duplicate profile account: " + profile.userAccount());
            assertNotBlank(profile.activityPreferenceText(), profile.userAccount(), "activityPreferenceText");
            assertNotBlank(profile.socialPersonalityText(), profile.userAccount(), "socialPersonalityText");
            assertNotBlank(profile.partnerPreferenceText(), profile.userAccount(), "partnerPreferenceText");
            assertNotBlank(profile.activityConstraintHabitText(), profile.userAccount(),
                    "activityConstraintHabitText");
            assertNotBlank(profile.aiInteractionPreferenceText(), profile.userAccount(),
                    "aiInteractionPreferenceText");
        }
        assertEquals(userAccounts, profileAccounts, "every evaluation user must have exactly one profile");
    }

    @Test
    void teams_shouldHaveValidOwnersMembersAndHardFilterFixtures() {
        Set<String> userAccounts = new HashSet<>();
        loader.users().users().forEach(user -> userAccounts.add(user.userAccount()));
        Set<String> codes = new HashSet<>();

        for (var team : loader.teams().teams()) {
            assertTrue(codes.add(team.code()), "duplicate team code: " + team.code());
            assertTrue(team.name().startsWith("[AI-EVAL]"), "missing evaluation prefix: " + team.code());
            assertTrue(userAccounts.contains(team.ownerAccount()), "unknown owner: " + team.code());
            assertTrue(team.memberAccounts().contains(team.ownerAccount()), "owner must be a member: " + team.code());
            assertEquals(team.memberAccounts().size(), new HashSet<>(team.memberAccounts()).size(),
                    "duplicate team member: " + team.code());
            assertTrue(team.memberAccounts().size() <= team.maxNum(), "team is over capacity: " + team.code());
            assertTrue(userAccounts.containsAll(team.memberAccounts()), "unknown team member: " + team.code());
            assertTrue(TeamActivityCategoryEnum.isValidCode(team.activityCategory()),
                    "invalid activity category: " + team.code());
            assertEquals(CATEGORY_BY_ACTIVITY_TYPE.get(team.activityType()), team.activityCategory(),
                    "activity type/category mismatch: " + team.code());
        }

        assertFull("team-xian-badminton-full");
        assertFull("team-xian-hiking-full");
        assertFull("team-chengdu-boardgame-full");
        var oneSlot = team("team-xian-badminton-one-slot");
        assertEquals(1, oneSlot.maxNum() - oneSlot.memberAccounts().size());
        assertTrue(team("team-xian-badminton-expired").expireAfterDays() < 0);
        assertTrue(team("team-xian-hiking-expired").expireAfterDays() < 0);
        assertTrue(team("team-chengdu-boardgame-expired").expireAfterDays() < 0);
        assertEquals(1, team("team-xian-badminton-private").status());

        Set<String> xianOwned = new HashSet<>();
        loader.teams().teams().stream()
                .filter(team -> "eval_runner_xian".equals(team.ownerAccount()))
                .forEach(team -> xianOwned.add(team.code()));
        assertTrue(xianOwned.containsAll(Set.of(
                "team-xian-badminton-beginner", "team-xian-badminton-casual")));
        var joinedTeam = team("team-xian-hiking-light");
        assertFalse("eval_runner_xian".equals(joinedTeam.ownerAccount()));
        assertTrue(joinedTeam.memberAccounts().contains("eval_runner_xian"));
    }

    @Test
    void cases_shouldUseUniqueIdsKnownToolsAndExistingBusinessReferences() {
        JsonNode agent = loader.tree("agent-cases-v1.json");
        JsonNode memory = loader.tree("memory-cases-v1.json");
        JsonNode recommendation = loader.tree("recommendation-cases-v1.json");
        assertUniqueCaseIds(agent, memory, recommendation);

        List<String> tools = new ArrayList<>();
        collectValuesForField(agent, tools, Set.of(
                "expectedToolSequence", "forbiddenTools", "expectedToolsByTurn"));
        assertTrue(KNOWN_TOOLS.containsAll(tools), "unknown tool in agent cases: " + tools);

        Set<String> teamCodes = new HashSet<>();
        loader.teams().teams().forEach(team -> teamCodes.add(team.code()));
        Set<String> userAccounts = new HashSet<>();
        loader.users().users().forEach(user -> userAccounts.add(user.userAccount()));
        assertTrue(teamCodes.containsAll(regexValues(
                loader.text("data/ai-evaluation/agent-cases-v1.json")
                        + loader.text("data/ai-evaluation/recommendation-cases-v1.json"),
                TEAM_REFERENCE_PATTERN)), "a case references a missing team");
        assertTrue(userAccounts.containsAll(regexValues(
                loader.text("data/ai-evaluation/agent-cases-v1.json")
                        + loader.text("data/ai-evaluation/recommendation-cases-v1.json"),
                USER_REFERENCE_PATTERN)), "a case references a missing user");
    }

    @Test
    void memoryAndRecommendationCases_shouldBeInternallyConsistent() {
        JsonNode memory = loader.tree("memory-cases-v1.json");
        for (JsonNode testCase : memory.path("cases")) {
            Set<String> messageKeys = textSet(testCase.path("messages"), "key");
            JsonNode episode = testCase.path("episodeExpectation");
            assertTrue(messageKeys.containsAll(textSet(episode.path("mustReferenceMessageKeys"))),
                    "unknown required message key in " + testCase.path("id").asText());
            assertTrue(messageKeys.containsAll(textSet(episode.path("mustReferenceAnyMessageKeys"))),
                    "unknown optional message key in " + testCase.path("id").asText());
            assertTrue(messageKeys.containsAll(textSet(episode.path("mustNotReferenceMessageKeys"))),
                    "unknown forbidden message key in " + testCase.path("id").asText());
            assertTrue(PROFILE_TYPES.containsAll(textSet(episode.path("requiredProfileTypes"))),
                    "unknown profile type in " + testCase.path("id").asText());
            assertTrue(episode.path("minCount").asInt() <= episode.path("maxCount").asInt(),
                    "invalid episode count range in " + testCase.path("id").asText());
        }

        JsonNode recommendation = loader.tree("recommendation-cases-v1.json");
        for (JsonNode testCase : recommendation.path("cases")) {
            Set<String> expected = new HashSet<>();
            addTexts(expected, testCase.path("expectedTopCodes"));
            addTexts(expected, testCase.path("expectedTopCodesAnyOrder"));
            addTexts(expected, testCase.path("expectedTopAccounts"));
            addTexts(expected, testCase.path("expectedTopAccountsAnyOrder"));
            Set<String> forbidden = new HashSet<>();
            addTexts(forbidden, testCase.path("forbiddenCodes"));
            addTexts(forbidden, testCase.path("forbiddenAccounts"));
            Set<String> conflict = new HashSet<>(expected);
            conflict.retainAll(forbidden);
            assertTrue(conflict.isEmpty(), "expected and forbidden candidates overlap in "
                    + testCase.path("id").asText() + ": " + conflict);
        }
    }

    private Set<String> controlledTags() {
        Matcher matcher = CONTROLLED_TAG_PATTERN.matcher(loader.text("sql/controlled_tag_seed.sql"));
        Set<String> result = new HashSet<>();
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private void assertUniqueCaseIds(JsonNode... documents) {
        Set<String> ids = new HashSet<>();
        for (JsonNode document : documents) {
            for (JsonNode testCase : document.path("cases")) {
                String id = testCase.path("id").asText();
                assertFalse(id.isBlank(), "case id is required");
                assertTrue(ids.add(id), "duplicate case id: " + id);
            }
        }
    }

    private void collectValuesForField(JsonNode node, List<String> values, Set<String> fieldNames) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (fieldNames.contains(field.getKey())) {
                    collectTextLeaves(field.getValue(), values);
                } else {
                    collectValuesForField(field.getValue(), values, fieldNames);
                }
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectValuesForField(child, values, fieldNames));
        }
    }

    private void collectTextLeaves(JsonNode node, List<String> values) {
        if (node.isTextual()) {
            values.add(node.asText());
        } else if (node.isContainerNode()) {
            node.forEach(child -> collectTextLeaves(child, values));
        }
    }

    private Set<String> regexValues(String value, Pattern pattern) {
        Matcher matcher = pattern.matcher(value);
        Set<String> result = new HashSet<>();
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        addTexts(values, array);
        return values;
    }

    private Set<String> textSet(JsonNode array, String field) {
        Set<String> values = new HashSet<>();
        if (array.isArray()) {
            array.forEach(item -> values.add(item.path(field).asText()));
        }
        return values;
    }

    private void addTexts(Set<String> target, JsonNode array) {
        if (array.isArray()) {
            array.forEach(item -> target.add(item.asText()));
        }
    }

    private void assertFull(String code) {
        var team = team(code);
        assertEquals(team.maxNum(), team.memberAccounts().size(), code + " must be full");
    }

    private AiEvaluationDatasetLoader.TeamSeed team(String code) {
        return loader.teams().teams().stream()
                .filter(team -> code.equals(team.code()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing team: " + code));
    }

    private void assertNotBlank(String value, String account, String field) {
        assertTrue(value != null && !value.isBlank(), field + " is required: " + account);
    }
}
