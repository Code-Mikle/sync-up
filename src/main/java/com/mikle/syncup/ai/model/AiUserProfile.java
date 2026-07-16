package com.mikle.syncup.ai.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class AiUserProfile implements Serializable {

    private long id;

    private String username;

    private String avatarUrl;

    private Integer gender;

    private String tags;

    private String planetCode;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
