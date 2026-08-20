package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class AiUserRecommendation implements Serializable {

    private Long id;

    private String username;

    private String avatarUrl;

    private Integer gender;

    private String city;

    private String tags;

    /**
     * 用户个人简介 / 自我介绍
     */
    private String profile;

    private Date createTime;

    private Date lastActiveTime;

    private Boolean degraded;

    private List<String> reasons = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
