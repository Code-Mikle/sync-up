package com.mikle.syncup.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.VectorSimilarity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

class AiRecommendationEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProfileEmbeddingCodec codec = new ProfileEmbeddingCodec();
    private final VectorSimilarity vectorSimilarity = new VectorSimilarity();

    @Test
    void hybridRanking_shouldBeatStructuredOnlyReferenceBaseline() throws Exception {
        List<EvaluationCase> cases = loadCases();
        Assertions.assertTrue(cases.size() >= 10, "recommendation evaluation set should contain at least 10 cases");

        Metric structured = new Metric();
        Metric hybrid = new Metric();
        for (EvaluationCase evaluationCase : cases) {
            List<Candidate> structuredRanking = evaluationCase.candidates.stream()
                    .sorted(Comparator.comparingDouble(Candidate::structuredScore).reversed())
                    .toList();
            float[] query = codec.normalize(evaluationCase.queryVector);
            List<Candidate> hybridRanking = evaluationCase.candidates.stream()
                    .sorted(Comparator.comparingDouble((Candidate candidate) -> hybridScore(query, candidate)).reversed())
                    .toList();
            structured.add(structuredRanking);
            hybrid.add(hybridRanking);
        }

        System.out.printf(
                "AI recommendation evaluation v1 (synthetic reference): cases=%d, structuredHit@1=%.2f, structuredNDCG@3=%.2f, hybridHit@1=%.2f, hybridNDCG@3=%.2f%n",
                cases.size(), structured.hitAtOne(), structured.ndcgAtThree(), hybrid.hitAtOne(), hybrid.ndcgAtThree());

        Assertions.assertTrue(hybrid.hitAtOne() > structured.hitAtOne());
        Assertions.assertTrue(hybrid.ndcgAtThree() > structured.ndcgAtThree());
        Assertions.assertEquals(1D, hybrid.hitAtOne());
    }

    private double hybridScore(float[] query, Candidate candidate) {
        float[] vector = codec.normalize(candidate.vector);
        double semantic = (vectorSimilarity.cosine(query, vector) + 1D) / 2D;
        return 0.80D * semantic + 0.20D * candidate.structuredScore;
    }

    private List<EvaluationCase> loadCases() throws Exception {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("ai/recommendation-evaluation-v1.json")) {
            Assertions.assertNotNull(inputStream);
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        }
    }

    private static class Metric {
        private int cases;
        private int hitAtOne;
        private double ndcgAtThree;

        void add(List<Candidate> ranking) {
            cases++;
            if (!ranking.isEmpty() && ranking.getFirst().relevant) {
                hitAtOne++;
            }
            for (int i = 0; i < Math.min(3, ranking.size()); i++) {
                if (ranking.get(i).relevant) {
                    ndcgAtThree += 1D / (Math.log(i + 2D) / Math.log(2D));
                    break;
                }
            }
        }

        double hitAtOne() {
            return cases == 0 ? 0D : (double) hitAtOne / cases;
        }

        double ndcgAtThree() {
            return cases == 0 ? 0D : ndcgAtThree / cases;
        }
    }

    private static class EvaluationCase {
        public String id;
        public float[] queryVector;
        public List<Candidate> candidates;
    }

    private static class Candidate {
        public String id;
        public float[] vector;
        public double structuredScore;
        public boolean relevant;

        double structuredScore() {
            return structuredScore;
        }
    }
}
