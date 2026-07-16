package com.mikle.syncup.ai.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "ai_tool_call_log")
@Data
public class AiToolCallLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private Long userId;

    private String actionType;

    private String toolName;

    private String status;

    private String argumentsSummary;

    private String resultSummary;

    private String errorMessage;

    private Long durationMs;

    private String relatedDraftId;

    private Long relatedTeamId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
