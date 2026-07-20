package com.mikle.syncup.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "ai_chat_message")
@Data
public class AiChatMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    /**
     * user / assistant / event.
     */
    private String role;

    /**
     * Text shown to user or event text used by memory.
     */
    private String content;

    /**
     * Serialized assistant response or event payload.
     */
    private String responseJson;

    /**
     * 1 - shown in chat history, 0 - hidden event for context/state recovery.
     */
    private Integer visible;

    private Date expireAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
