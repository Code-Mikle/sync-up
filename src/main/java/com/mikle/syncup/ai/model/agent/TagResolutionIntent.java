package com.mikle.syncup.ai.model.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/** AI 提取出的、等待归一化的活动短语。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TagResolutionIntent extends AiIntent {

    private List<String> tagQueries = new ArrayList<>();
}
