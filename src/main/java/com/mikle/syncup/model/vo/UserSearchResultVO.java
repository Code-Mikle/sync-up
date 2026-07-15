package com.mikle.syncup.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Public user information returned by keyword search.
 */
@Data
public class UserSearchResultVO implements Serializable {

    private long id;

    private String username;

    private String avatarUrl;

    private Integer gender;

    private String tags;

    private Date createTime;

    private String planetCode;

    private static final long serialVersionUID = 1L;
}
