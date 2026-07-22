package com.mikle.syncup.ai.exception;

import lombok.Getter;

@Getter
public class InvalidToolArgumentsException extends RuntimeException {

    private final String toolName;

    public InvalidToolArgumentsException(String toolName, String message) {
        super(message);
        this.toolName = toolName;
    }

    public InvalidToolArgumentsException(String toolName, String message, Throwable cause) {
        super(message, cause);
        this.toolName = toolName;
    }

}
