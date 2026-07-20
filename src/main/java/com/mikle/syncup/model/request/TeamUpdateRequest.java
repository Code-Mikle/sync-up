package com.mikle.syncup.model.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 队伍更新请求体
 */
@Data
public class TeamUpdateRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * id
     */
    @NotNull
    @Positive
    private Long id;

    /**
     * 队伍名称
     */
    @Size(max = 20)
    private String name;

    /**
     * 描述
     */
    @Size(max = 512)
    private String description;

    /**
     * 活动大类
     */
    @Min(1)
    @Max(9)
    private Integer activityCategory;

    /**
     * 过期时间
     */
    private Date expireTime;

    /**
     * 活动类型
     */
    @Size(max = 64)
    private String activityType;

    /**
     * 城市
     */
    @Size(max = 64)
    private String city;

    /**
     * 区域
     */
    @Size(max = 64)
    private String district;

    /**
     * 活动开始时间
     */
    private Date startTime;

    /**
     * 预计时长，单位分钟
     */
    @Min(1)
    private Integer durationMinutes;

    /**
     * 人均预算
     */
    @DecimalMin(value = "0.00")
    private BigDecimal budgetPerPerson;

    /**
     * 水平要求
     */
    @Size(max = 32)
    private String skillLevel;

    /**
     * 0 - 公开，1 - 私有，2 - 加密
     */
    @Min(0)
    @Max(2)
    private Integer status;

    /**
     * 密码
     */
    @Size(max = 32)
    private String password;
}
