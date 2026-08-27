package com.mikle.syncup.ai.model.schema;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GeneratedEpisodeExtraction {

    private List<GeneratedEpisode> episodes = new ArrayList<>();
}
