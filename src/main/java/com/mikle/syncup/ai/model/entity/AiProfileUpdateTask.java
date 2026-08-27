package com.mikle.syncup.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ai_profile_update_task")
public class AiProfileUpdateTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String profileType;

    private String triggerType;

    private String targetEvidenceDigest;

    private Integer expectedProfileVersion;

    private String status;

    private Integer retryCount;

    private Date nextRetryAt;

    private String lastError;

    private String model;

    private String promptVersion;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
