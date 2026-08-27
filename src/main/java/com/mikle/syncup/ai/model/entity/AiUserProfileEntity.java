package com.mikle.syncup.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName(value = "ai_user_profile")
@Data
public class AiUserProfileEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String activityPreferenceText;

    private String socialPersonalityText;

    private String partnerPreferenceText;

    private String activityConstraintHabitText;

    private String aiInteractionPreferenceText;

    private String profileText;

    private String matchProfileText;

    private String interactionProfileText;

    private Integer profileVersion;

    private String evidenceDigest;

    private String model;

    private String promptVersion;

    private String status;

    private Date generatedAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
