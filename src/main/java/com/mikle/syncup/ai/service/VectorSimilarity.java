package com.mikle.syncup.ai.service;

import org.springframework.stereotype.Component;

@Component
public class VectorSimilarity {

    public double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            throw new IllegalArgumentException("embedding dimensions do not match");
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < left.length; i++) {
            if (!Float.isFinite(left[i]) || !Float.isFinite(right[i])) {
                throw new IllegalArgumentException("embedding contains non-finite value");
            }
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm <= 0D || rightNorm <= 0D) {
            throw new IllegalArgumentException("embedding must not be zero");
        }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }
}
