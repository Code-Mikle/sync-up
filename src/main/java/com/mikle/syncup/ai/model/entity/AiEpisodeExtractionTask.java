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
@TableName("ai_episode_extraction_task")
public class AiEpisodeExtractionTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long chatSessionId;

    private String sourceType;

    private String sourceText;

    private String sourceReferenceId;

    private Long fromMessageIdExclusive;

    private Long toMessageIdInclusive;

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
