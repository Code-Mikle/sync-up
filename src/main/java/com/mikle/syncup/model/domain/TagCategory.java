package com.mikle.syncup.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 受控活动标签分类。 */
@Data
@TableName("tag_category")
public class TagCategory implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String description;

    private Integer status;

    private Integer sortOrder;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
