package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class AiTeamDeleteConfirmationVO implements Serializable {

    private Long teamId;

    private String name;

    private String description;

    private Integer activityCategory;

    private String activityType;

    private String city;

    private String district;

    private Date startTime;

    private Integer maxNum;

    private Integer hasJoinNum;

    private String warning;

    private static final long serialVersionUID = 1L;
}
