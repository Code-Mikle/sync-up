package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class AiBusinessEventVO implements Serializable {

    private String eventType;

    private String subjectType;

    private Long subjectId;

    private String subjectName;

    private String action;

    private String status;

    private String summary;

    private String relatedDraftId;

    private Long relatedTeamId;

    private Date occurredAt;

    private static final long serialVersionUID = 1L;
}
