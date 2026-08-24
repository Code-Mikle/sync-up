package com.mikle.syncup.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class TagVO implements Serializable {

    private Long id;

    private Long categoryId;

    private String code;

    private String name;

    private String description;

    private static final long serialVersionUID = 1L;
}
