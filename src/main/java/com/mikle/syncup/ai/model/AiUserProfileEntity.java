package com.mikle.syncup.ai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "ai_user_profile")
@Data
public class AiUserProfileEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String profileJson;

    private String sourceText;

    private String modelVersion;

    /**
     * 1 - confirmed.
     */
    private Integer status;

    private Date confirmedAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

