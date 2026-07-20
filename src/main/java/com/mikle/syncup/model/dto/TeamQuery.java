package com.mikle.syncup.model.dto;

import com.mikle.syncup.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;


/**
 * 队伍查询封装类
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TeamQuery extends PageRequest {
    /**
     * id
     */
    private Long id;

    /**
     * id 列表
     */
    private List<Long> idList;

    /**
     * 搜索关键词（同时对队伍名称和描述搜索）
     */
    private String searchText;

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
     * 活动大类
     */
    private Integer activityCategory;

    /**
     * 活动类型
     */
    private String activityType;

    /**
     * 城市
     */
    private String city;

    /**
     * 区域
     */
    private String district;

    /**
     * 活动开始时间下限
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date startTimeBegin;

    /**
     * 活动开始时间上限
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date startTimeEnd;

    /**
     * 人均预算上限
     */
    private BigDecimal maxBudgetPerPerson;

    /**
     * 水平要求
     */
    private String skillLevel;

    /**
     * 是否只展示仍有名额的队伍
     */
    private Boolean onlyAvailable;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 0 - 公开，1 - 私有，2 - 加密
     */
    private Integer status;
}
