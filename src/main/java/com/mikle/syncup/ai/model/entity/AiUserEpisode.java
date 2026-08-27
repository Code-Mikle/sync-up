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
@TableName("ai_user_episode")
public class AiUserEpisode implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String profileType;

    private String content;

    private String sourceType;

    private Long sourceSessionId;

    private String sourceMessageIds;

    private String sourceReferenceId;

    private String signalType;

    private String priority;

    private String evidenceGroupKey;

    private String dedupeHash;

    private Long extractionTaskId;

    private String supersededEpisodeIds;

    private String status;

    private Integer consolidatedProfileVersion;

    private Date observedAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
