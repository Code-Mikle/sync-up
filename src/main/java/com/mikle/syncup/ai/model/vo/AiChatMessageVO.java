package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class AiChatMessageVO implements Serializable {

    private Long id;

    private String sessionId;

    private String role;

    private String content;

    private AiChatResponseVO response;

    private String eventType;

    private Long relatedTeamId;

    private String relatedDraftId;

    private Integer visible;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
