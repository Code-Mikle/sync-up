package com.mikle.syncup.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户实体
 */
@TableName(value = "user")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private long id;

    /**
     * 用户昵称
     */
    private String username;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String avatarUrl;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 电话
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 常驻城市
     */
    private String city;

    /**
     * 标准标签 id 列表 JSON，持久化到 user.tagIds。
     * 不直接作为接口响应字段，标签展示由 VO 中的 tagNames 承载。
     */
    @JsonIgnore
    private String tagIds;

    /**
     * 个人简介 / 自我介绍
     */
    private String profile;

    /**
     * 状态 0 - 正常
     */
    private Integer userStatus;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     *
     */
    private Date updateTime;

    /**
     * 最近活跃时间
     */
    private Date lastActiveTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 用户角色 0 - 普通用户 1 - 管理员
     */
    private Integer userRole;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
