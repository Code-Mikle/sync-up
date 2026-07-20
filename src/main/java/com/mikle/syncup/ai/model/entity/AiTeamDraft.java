package com.mikle.syncup.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@TableName(value = "ai_team_draft")
@Data
public class AiTeamDraft implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String draftId;

    private String sessionId;

    private Long userId;

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

    /**
     * 0 - pending, 1 - confirmed, 2 - expired.
     */
    private Integer status;

    private Long confirmedTeamId;

    private Date confirmedAt;

    private Date expiresAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
