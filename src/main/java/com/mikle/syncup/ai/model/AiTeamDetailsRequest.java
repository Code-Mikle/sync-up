package com.mikle.syncup.ai.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiTeamDetailsRequest implements Serializable {

    private String sessionId;

    private static final long serialVersionUID = 1L;
}
