package com.mikle.syncup.ai.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiChatRequest implements Serializable {

    private String sessionId;

    private String message;

    private static final long serialVersionUID = 1L;
}
