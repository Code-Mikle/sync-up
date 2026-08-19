package com.mikle.syncup.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;
import com.mikle.syncup.ai.service.UserProfileTextAssembler;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

class AiProfileQualityEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UserProfileTextAssembler assembler = new UserProfileTextAssembler();

    @Test
    void profileQualityEvaluationV1_shouldProvideRepeatableOfflineBaseline() throws Exception {
        List<EvaluationCase> cases = loadCases();
        Assertions.assertTrue(cases.size() >= 10,
                "profile quality evaluation set should contain at least 10 cases");

        Metric sectionMetric = new Metric();
        Metric factMetric = new Metric();
        Metric safetyMetric = new Metric();
        Metric boundaryMetric = new Metric();

        for (EvaluationCase evaluationCase : cases) {
            Assertions.assertTrue(StringUtils.isNotBlank(evaluationCase.id));
            Assertions.assertTrue(StringUtils.isNotBlank(evaluationCase.sourceText));

            GeneratedUserProfile profile = evaluationCase.referenceProfile;
            String fullText = assembler.renderFull(profile);
            String matchText = assembler.renderMatch(profile);
            String interactionText = assembler.renderInteraction(profile);

            sectionMetric.add(hasAllSections(fullText));
            for (String expectedFact : safeList(evaluationCase.expectedFacts)) {
                factMetric.add(fullText.contains(expectedFact));
            }
            for (String forbiddenFact : safeList(evaluationCase.forbiddenFacts)) {
                safetyMetric.add(!fullText.contains(forbiddenFact));
            }
            for (String hardField : safeList(evaluationCase.hardFields)) {
                safetyMetric.add(!fullText.contains(hardField));
            }
            boundaryMetric.add(!matchText.contains(UserProfileTextAssembler.INTERACTION_HEADER)
                    && !matchText.contains(profile.getAiInteractionPreference()));
            boundaryMetric.add(interactionText.contains(profile.getAiInteractionPreference())
                    && !interactionText.contains(UserProfileTextAssembler.INTEREST_HEADER));
        }

        System.out.printf(
                "AI profile quality evaluation v1 (reference baseline): cases=%d, sections=%s, facts=%s, safety=%s, boundary=%s%n",
                cases.size(), sectionMetric.summary(), factMetric.summary(), safetyMetric.summary(), boundaryMetric.summary());

        Assertions.assertEquals(1D, sectionMetric.accuracy(), "all five sections should be complete");
        Assertions.assertEquals(1D, factMetric.accuracy(), "reference profiles should retain stated facts");
        Assertions.assertEquals(1D, safetyMetric.accuracy(),
                "reference profiles should exclude unsupported, sensitive and hard-filter facts");
        Assertions.assertEquals(1D, boundaryMetric.accuracy(),
                "matching and interaction profile text should remain isolated");
    }

    private boolean hasAllSections(String fullText) {
        return fullText.contains(UserProfileTextAssembler.INTEREST_HEADER)
                && fullText.contains(UserProfileTextAssembler.SOCIAL_HEADER)
                && fullText.contains(UserProfileTextAssembler.PARTNER_HEADER)
                && fullText.contains(UserProfileTextAssembler.CONSTRAINT_HEADER)
                && fullText.contains(UserProfileTextAssembler.INTERACTION_HEADER);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private List<EvaluationCase> loadCases() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("ai/profile-quality-evaluation-v1.json")) {
            Assertions.assertNotNull(inputStream, "profile quality evaluation data should exist");
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
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
            return total == 0 ? 1D : (double) correct / total;
        }

        String summary() {
            return correct + "/" + total + " (" + String.format("%.2f", accuracy() * 100) + "%)";
        }
    }

    private static class EvaluationCase {
        public String id;
        public String sourceText;
        public GeneratedUserProfile referenceProfile;
        public List<String> expectedFacts;
        public List<String> forbiddenFacts;
        public List<String> hardFields;
    }
}
