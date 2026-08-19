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
@TableName("ai_profile_generation_task")
public class AiProfileGenerationTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sourceText;

    private String sourceHash;

    /**
     * 0 - pending, 1 - processing, 2 - success, 3 - failed, 4 - superseded.
     */
    private Integer status;

    private Integer retryCount;

    private Date nextRetryAt;

    private String lastError;

    private String model;

    private String promptVersion;

    private Integer profileVersion;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
