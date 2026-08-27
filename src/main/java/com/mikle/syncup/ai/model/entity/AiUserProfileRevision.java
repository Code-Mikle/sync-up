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
@TableName("ai_user_profile_revision")
public class AiUserProfileRevision implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String profileType;

    private Integer fromProfileVersion;

    private Integer toProfileVersion;

    private String triggerType;

    private String oldContent;

    private String newContent;

    private String evidenceEpisodeIds;

    private String model;

    private String promptVersion;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
