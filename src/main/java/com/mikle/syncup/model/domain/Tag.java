package com.mikle.syncup.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 系统维护的标准活动标签。 */
@Data
@TableName("tag")
public class Tag implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private String code;

    private String name;

    private String description;

    private Integer status;

    private Integer sortOrder;

    private String embeddingTextHash;

    private String embeddingModel;

    private Integer embeddingDimensions;

    private String vectorJson;

    private Date embeddingUpdatedAt;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
