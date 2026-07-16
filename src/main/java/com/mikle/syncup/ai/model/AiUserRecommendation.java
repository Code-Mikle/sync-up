package com.mikle.syncup.ai.model;

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

    private String tags;

    private String planetCode;

    private Date createTime;

    private List<String> reasons = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
