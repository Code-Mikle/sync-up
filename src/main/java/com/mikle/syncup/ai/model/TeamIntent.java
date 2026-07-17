package com.mikle.syncup.ai.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class TeamIntent implements Serializable {

    private String sourceText;

    private Long teamId;

    private String teamPassword;

    private String activityType;

    private String city;

    private String district;

    private Date startTime;

    private Integer durationMinutes;

    /**
     * 创建队伍时表示总人数上限；搜索队伍时表示所需的剩余可用名额数。
     */
    private Integer memberCount;

    private BigDecimal budgetMin;

    private BigDecimal budgetMax;

    private String skillLevel;

    private List<String> tags = new ArrayList<>();

    private String teamName;

    private String description;

    private String profileText;

    private boolean createTeamRequested;

    private boolean teamRelated;

    private List<String> missingFields = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
