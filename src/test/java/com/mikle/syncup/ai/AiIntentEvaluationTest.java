package com.mikle.syncup.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.impl.MockTeamIntentParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class AiIntentEvaluationTest {

    private static final double MIN_SLOT_ACCURACY = 0.90;

    private static final double MIN_TOOL_ACCURACY = 0.95;

    private static final double MIN_MISSING_FIELD_ACCURACY = 0.90;

    private final MockTeamIntentParser teamIntentParser = new MockTeamIntentParser();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void intentEvaluationV1_shouldReachStageOneBaseline() throws Exception {
        List<EvaluationCase> cases = loadCases();
        Assertions.assertTrue(cases.size() >= 30, "stage 1 evaluation set should contain at least 30 cases");

        Metric slotMetric = new Metric();
        Metric toolMetric = new Metric();
        Metric missingFieldMetric = new Metric();

        for (EvaluationCase evaluationCase : cases) {
            TeamIntent intent = teamIntentParser.parse(evaluationCase.message);
            compareNullableSlot(slotMetric, evaluationCase.expectedActivityType, intent.getActivityType());
            compareNullableSlot(slotMetric, evaluationCase.expectedCity, intent.getCity());
            compareNullableSlot(slotMetric, evaluationCase.expectedMemberCount, intent.getMemberCount());
            compareNullableDecimalSlot(slotMetric, evaluationCase.expectedBudgetMax, intent.getBudgetMax());
            compareNullableSlot(slotMetric, evaluationCase.expectedSkillLevel, intent.getSkillLevel());

            toolMetric.add(expectedTools(evaluationCase).equals(selectedTools(intent)));

            List<String> expectedMissingFields = evaluationCase.expectedMissingFields == null
                    ? Collections.emptyList()
                    : evaluationCase.expectedMissingFields;
            missingFieldMetric.add(new LinkedHashSet<>(expectedMissingFields)
                    .equals(new LinkedHashSet<>(intent.getMissingFields())));
        }

        System.out.printf(
                "AI intent evaluation v1: cases=%d, slot=%s, tool=%s, missingField=%s%n",
                cases.size(),
                slotMetric.summary(),
                toolMetric.summary(),
                missingFieldMetric.summary()
        );

        Assertions.assertTrue(slotMetric.accuracy() >= MIN_SLOT_ACCURACY,
                "slot accuracy should be >= " + MIN_SLOT_ACCURACY + ", actual=" + slotMetric.summary());
        Assertions.assertTrue(toolMetric.accuracy() >= MIN_TOOL_ACCURACY,
                "tool accuracy should be >= " + MIN_TOOL_ACCURACY + ", actual=" + toolMetric.summary());
        Assertions.assertTrue(missingFieldMetric.accuracy() >= MIN_MISSING_FIELD_ACCURACY,
                "missing field accuracy should be >= " + MIN_MISSING_FIELD_ACCURACY + ", actual=" + missingFieldMetric.summary());
    }

    private List<EvaluationCase> loadCases() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("ai/intent-evaluation-v1.json")) {
            Assertions.assertNotNull(inputStream, "evaluation data should exist");
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    private Set<String> expectedTools(EvaluationCase evaluationCase) {
        if (!evaluationCase.expectedTeamRelated
                || evaluationCase.expectedMissingFields != null && !evaluationCase.expectedMissingFields.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> tools = new LinkedHashSet<>();
        tools.add("searchTeams");
        tools.add("recommendUsers");
        if (evaluationCase.expectedCreateTeamRequested) {
            tools.add("createTeamDraft");
        }
        return tools;
    }

    private Set<String> selectedTools(TeamIntent intent) {
        if (!intent.isTeamRelated()
                || intent.getActivityType() == null
                || intent.getCity() == null
                || intent.isCreateTeamRequested() && intent.getMemberCount() == null) {
            return Collections.emptySet();
        }
        Set<String> tools = new LinkedHashSet<>();
        tools.add("searchTeams");
        tools.add("recommendUsers");
        if (intent.isCreateTeamRequested()) {
            tools.add("createTeamDraft");
        }
        return tools;
    }

    private void compareNullableSlot(Metric metric, Object expected, Object actual) {
        metric.add(java.util.Objects.equals(expected, actual));
    }

    private void compareNullableDecimalSlot(Metric metric, String expected, BigDecimal actual) {
        metric.add(expected == null ? actual == null : actual != null && new BigDecimal(expected).compareTo(actual) == 0);
    }

    private static class Metric {
        private int total;
        private int correct;

        void add(boolean matched) {
            total++;
            if (matched) {
                correct++;
            }
        }

        double accuracy() {
            return total == 0 ? 1.0 : (double) correct / total;
        }

        String summary() {
            return correct + "/" + total + " (" + String.format("%.2f", accuracy() * 100) + "%)";
        }
    }

    private static class EvaluationCase {
        public String id;
        public String message;
        public String expectedActivityType;
        public String expectedCity;
        public Integer expectedMemberCount;
        public String expectedBudgetMax;
        public String expectedSkillLevel;
        public boolean expectedTeamRelated;
        public boolean expectedCreateTeamRequested;
        public List<String> expectedMissingFields;
    }
}
