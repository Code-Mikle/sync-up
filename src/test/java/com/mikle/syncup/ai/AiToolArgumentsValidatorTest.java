package com.mikle.syncup.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.exception.InvalidToolArgumentsException;
import com.mikle.syncup.ai.validation.AiToolArgumentsValidator;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class AiToolArgumentsValidatorTest {

    private final AiToolArgumentsValidator validator = new AiToolArgumentsValidator(new ObjectMapper());

    @Test
    void validate_shouldAcceptJsonObjectAndOmittedOptionalFields() {
        Assertions.assertDoesNotThrow(() -> validator.validate(aiMessage("{}")));
        Assertions.assertDoesNotThrow(() -> validator.validate(aiMessage("""
                {"activityCategory":1,"memberCount":6}
                """)));
    }

    @Test
    void validate_shouldRejectMalformedArguments() {
        InvalidToolArgumentsException exception = Assertions.assertThrows(
                InvalidToolArgumentsException.class,
                () -> validator.validate(aiMessage("{\"activityCategory\":1,\"budgetMax\":}"))
        );

        Assertions.assertEquals("createTeamDraft", exception.getToolName());
    }

    @Test
    void validate_shouldRejectBlankOrNonObjectArguments() {
        Assertions.assertThrows(InvalidToolArgumentsException.class, () -> validator.validate(aiMessage("")));
        Assertions.assertThrows(InvalidToolArgumentsException.class, () -> validator.validate(aiMessage("[]")));
        Assertions.assertThrows(InvalidToolArgumentsException.class, () -> validator.validate(aiMessage("null")));
    }

    private AiMessage aiMessage(String arguments) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-call-test")
                .name("createTeamDraft")
                .arguments(arguments)
                .build();
        return AiMessage.from(List.of(request));
    }
}
