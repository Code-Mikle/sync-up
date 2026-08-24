package com.mikle.syncup.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * Public user information returned by keyword search.
 */
@Data
public class UserSearchResultVO implements Serializable {

    private long id;

    private String username;

    private String avatarUrl;

    private Integer gender;

    private String city;

    private List<String> tagNames;

    /**
     * 用户个人简介 / 自我介绍
     */
    private String profile;

    private Date createTime;

    private Date lastActiveTime;

    private static final long serialVersionUID = 1L;
}
