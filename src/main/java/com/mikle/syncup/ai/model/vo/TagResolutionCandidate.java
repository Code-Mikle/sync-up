package com.mikle.syncup.ai.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class TagResolutionCandidate implements Serializable {

    private Long tagId;

    private String name;

    private String description;

    private Double score;

    private static final long serialVersionUID = 1L;
}
