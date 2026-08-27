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
@TableName("ai_chat_session")
public class AiChatSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionKey;

    private String summary;

    private Long lastSummaryMessageId;

    private Integer summaryVersion;

    private Date summaryUpdatedAt;

    private String summaryModel;

    private String summaryPromptVersion;

    private Long lastClosedMessageId;

    private Long lastEpisodeExtractedMessageId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
