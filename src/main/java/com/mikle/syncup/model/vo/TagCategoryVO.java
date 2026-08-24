package com.mikle.syncup.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class TagCategoryVO implements Serializable {

    private Long id;

    private String code;

    private String name;

    private String description;

    private List<TagVO> tags = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
