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
@TableName("ai_team_embedding")
public class AiTeamEmbedding implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teamId;

    private Integer contentVersion;

    private String contentHash;

    private String embeddingModel;

    private Integer dimensions;

    private String vectorJson;

    /** 0 - historical, 1 - active. */
    private Integer status;

    private Date generatedAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
