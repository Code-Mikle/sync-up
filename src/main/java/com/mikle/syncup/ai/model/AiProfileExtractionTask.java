package com.mikle.syncup.ai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "ai_profile_extraction_task")
@Data
public class AiProfileExtractionTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskId;

    private Long userId;

    private String sourceText;

    private String extractionJson;

    /**
     * 0 - pending, 1 - extracted, 2 - confirmed, 3 - rejected, 4 - failed.
     */
    private Integer status;

    private Integer retryCount;

    private Date nextRetryAt;

    private String lastError;

    private String modelVersion;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}

