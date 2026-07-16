package com.mikle.syncup.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.ai.service.TeamIntentParser;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@SpringBootTest
class AiIntentEvaluationTest {

    private static final double MIN_SLOT_ACCURACY = 0.90;

    private static final double MIN_TOOL_ACCURACY = 0.95;

    private static final double MIN_MISSING_FIELD_ACCURACY = 0.90;

    @Resource
    private TeamIntentParser teamIntentParser;

    @Resource
    private ObjectMapper objectMapper;

    @Test
    void intentEvaluationV1_shouldReachStageOneBaseline() throws Exception {
        List<EvaluationCase> cases = loadCases();
        Assertions.assertTrue(cases.size() >= 30, "stage 1 evaluation set should contain at least 30 cases");

        Metric slotMetric = new Metric();
        Metric toolMetric = new Metric();
        Metric missingFieldMetric = new Metric();

        for (EvaluationCase evaluationCase : cases) {
            TeamIntent intent = teamIntentParser.parse(evaluationCase.message);
            compareSlot(slotMetric, evaluationCase.expectedActivityType, intent.getActivityType());
            compareSlot(slotMetric, evaluationCase.expectedCity, intent.getCity());
            compareSlot(slotMetric, evaluationCase.expectedMemberCount, intent.getMemberCount());
            compareSlot(slotMetric, evaluationCase.expectedBudgetMax, intent.getBudgetMax());
            compareSlot(slotMetric, evaluationCase.expectedSkillLevel, intent.getSkillLevel());

            toolMetric.add(evaluationCase.expectedTeamRelated == intent.isTeamRelated());
            toolMetric.add(evaluationCase.expectedCreateTeamRequested == intent.isCreateTeamRequested());

            List<String> expectedMissingFields = evaluationCase.expectedMissingFields == null
                    ? Collections.emptyList()
                    : evaluationCase.expectedMissingFields;
            for (String missingField : expectedMissingFields) {
                missingFieldMetric.add(intent.getMissingFields().contains(missingField));
            }
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

    private void compareSlot(Metric metric, String expected, String actual) {
        if (expected == null) {
            return;
        }
        metric.add(expected.equals(actual));
    }

    private void compareSlot(Metric metric, Integer expected, Integer actual) {
        if (expected == null) {
            return;
        }
        metric.add(expected.equals(actual));
    }

    private void compareSlot(Metric metric, String expected, BigDecimal actual) {
        if (expected == null) {
            return;
        }
        metric.add(actual != null && new BigDecimal(expected).compareTo(actual) == 0);
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
