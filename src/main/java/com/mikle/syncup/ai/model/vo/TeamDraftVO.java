package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class TeamDraftVO implements Serializable {

    private String draftId;

    private String sessionId;

    private String name;

    private String description;

    private Integer maxNum;

    private Integer activityCategory;

    private String activityType;

    private String city;

    private String district;

    private Date startTime;

    private Integer durationMinutes;

    private BigDecimal budgetPerPerson;

    private String skillLevel;

    private Date expiresAt;

    private static final long serialVersionUID = 1L;
}
