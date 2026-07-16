package com.mikle.syncup.ai.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiToolResult implements Serializable {

    private String toolName;

    private String type;

    private boolean success;

    private String summary;

    private Object data;

    public static AiToolResult success(String toolName, String type, String summary, Object data) {
        AiToolResult result = new AiToolResult();
        result.setToolName(toolName);
        result.setType(type);
        result.setSuccess(true);
        result.setSummary(summary);
        result.setData(data);
        return result;
    }

    public static AiToolResult failure(String toolName, String type, String summary) {
        AiToolResult result = new AiToolResult();
        result.setToolName(toolName);
        result.setType(type);
        result.setSuccess(false);
        result.setSummary(summary);
        return result;
    }

    private static final long serialVersionUID = 1L;
}
