package com.mikle.syncup.ai.model.schema;

import lombok.Data;

import java.util.List;

@Data
public class GeneratedEpisode {

    private String profileType;

    private String content;

    private List<Long> sourceMessageIds;

    private String signalType;

    private String priority;

    private List<Long> supersededEpisodeIds;
}
