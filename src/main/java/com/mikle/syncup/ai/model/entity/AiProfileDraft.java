package com.mikle.syncup.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "ai_profile_draft")
@Data
public class AiProfileDraft implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String draftId;

    private Long userId;

    private String sourceText;

    private String profileJson;

    /**
     * 0 - pending, 1 - confirmed, 2 - rejected, 3 - expired.
     */
    private Integer status;

    private Date expiresAt;

    private Date confirmedAt;

    private String modelVersion;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
