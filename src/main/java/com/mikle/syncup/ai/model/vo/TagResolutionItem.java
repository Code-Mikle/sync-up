package com.mikle.syncup.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class TagResolutionItem implements Serializable {

    private String query;

    private TagResolutionCandidate resolvedTag;

    /** RESOLVED / NEEDS_JUDGMENT / UNRESOLVED */
    private String status;

    private List<TagResolutionCandidate> candidates = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
