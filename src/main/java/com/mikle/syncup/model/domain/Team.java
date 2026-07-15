package com.mikle.syncup.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 队伍实体
 */
@TableName(value = "team")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 队伍名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 最大人数
     */
    private Integer maxNum;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * Activity type, for example badminton or hiking.
     */
    private String activityType;

    /**
     * City name.
     */
    private String city;

    /**
     * District or business area.
     */
    private String district;

    /**
     * Activity start time. Do not reuse expireTime for this meaning.
     */
    private Date startTime;

    /**
     * Estimated duration in minutes.
     */
    private Integer durationMinutes;

    /**
     * Budget per person.
     */
    private BigDecimal budgetPerPerson;

    /**
     * Skill level, for example beginner, intermediate, advanced.
     */
    private String skillLevel;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 0 - 公开，1 - 私有，2 - 加密
     */
    private Integer status;

    /**
     * 密码
     */
    private String password;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     *
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
