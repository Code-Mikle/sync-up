package com.mikle.syncup.ai.model;

import lombok.Data;

import java.util.Date;

@Data
public class AiProfileResponse {

    private String taskId;

    private Long userId;

    private Integer status;

    private ProfileExtraction profile;

    private String sourceText;

    private String modelVersion;

    private Date confirmedAt;

    private Date updateTime;
}

