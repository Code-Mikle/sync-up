package com.mikle.syncup.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ProfileEmbeddingCodec {

    @Resource
    private ObjectMapper objectMapper;

    public float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("embedding vector must not be empty");
        }
        double squaredSum = 0D;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("embedding vector contains non-finite value");
            }
            squaredSum += (double) value * value;
        }
        if (squaredSum <= 0D) {
            throw new IllegalArgumentException("embedding vector must not be zero");
        }
        double norm = Math.sqrt(squaredSum);
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    public String serialize(float[] normalizedVector) {
        try {
            return objectMapper.writeValueAsString(normalizedVector);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "serialize profile embedding failed");
        }
    }

    public float[] deserialize(String vectorJson) {
        try {
            return objectMapper.readValue(vectorJson, float[].class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "parse profile embedding failed");
        }
    }
}
