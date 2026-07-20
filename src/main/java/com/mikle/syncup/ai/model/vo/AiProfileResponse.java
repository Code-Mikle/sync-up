package com.mikle.syncup.ai.model.vo;

import com.mikle.syncup.ai.model.schema.ProfileExtraction;
import lombok.Data;

import java.util.Date;

@Data
public class AiProfileResponse {

    private String draftId;

    private Long userId;

    private Integer status;

    private ProfileExtraction profile;

    private String sourceText;

    private String modelVersion;

    private Date confirmedAt;

    private Date expiresAt;

    private Date updateTime;
}
