package com.mikle.syncup.ai.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.exception.InvalidToolArgumentsException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiToolArgumentsValidator {

    private final ObjectMapper objectMapper;

    public AiToolArgumentsValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(AiMessage aiMessage) {
        if (aiMessage == null || !aiMessage.hasToolExecutionRequests()) {
            return;
        }
        for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
            validate(request);
        }
    }

    public void validateMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (ChatMessage message : messages) {
            if (message instanceof AiMessage aiMessage) {
                validate(aiMessage);
            }
        }
    }

    private void validate(ToolExecutionRequest request) {
        String toolName = request == null ? null : request.name();
        if (request == null || StringUtils.isBlank(toolName)) {
            throw new InvalidToolArgumentsException(toolName, "AI tool name is missing");
        }
        String arguments = request.arguments();
        if (StringUtils.isBlank(arguments)) {
            throw new InvalidToolArgumentsException(toolName, "AI tool arguments are blank");
        }
        try {
            JsonNode root = objectMapper.readTree(arguments);
            if (root == null || !root.isObject()) {
                throw new InvalidToolArgumentsException(toolName, "AI tool arguments must be a JSON object");
            }
        } catch (JsonProcessingException e) {
            throw new InvalidToolArgumentsException(toolName, "AI tool arguments are not valid JSON", e);
        }
    }
}
