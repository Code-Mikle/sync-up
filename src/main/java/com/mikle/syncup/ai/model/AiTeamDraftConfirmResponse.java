package com.mikle.syncup.ai.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiTeamDraftConfirmResponse implements Serializable {

    private String draftId;

    private Long teamId;

    private String status;

    private static final long serialVersionUID = 1L;
}
