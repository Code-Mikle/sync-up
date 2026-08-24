package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class TagResolutionResult implements Serializable {

    private List<TagResolutionItem> items = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
