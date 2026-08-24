package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class AiUserProfile implements Serializable {

    private long id;

    private String username;

    private String avatarUrl;

    private Integer gender;

    private List<String> tagNames;

    private String profile;

    private String city;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}
