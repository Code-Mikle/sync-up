package com.mikle.syncup.ai.model.agent;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * AI 工具意图的公共基类。
 * 只保留所有工具共享的对话来源文本，避免把队伍和用户搜索的业务字段混在同一个意图中。
 */
@Getter
@Setter
public abstract class AiIntent implements Serializable {

    /**
     * 用户原始输入文本
     */
    private String sourceText;
}
