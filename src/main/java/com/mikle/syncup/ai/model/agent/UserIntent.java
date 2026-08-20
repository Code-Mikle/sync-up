package com.mikle.syncup.ai.model.agent;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 识别出的搭子搜索意图。
 */
@EqualsAndHashCode(callSuper = true) // 比较时会包含父类的 sourceText
@Data
@Schema(description = "AI 识别出的搭子搜索意图")
public class UserIntent extends AiIntent {

    @Schema(description = "希望匹配的兴趣标签；为空时不限制标签")
    private List<String> tags = new ArrayList<>();

    @Schema(description = "本次希望匹配的搭子描述；为空时使用当前用户的 AI 匹配画像")
    private String profile;

    @Schema(description = "目标城市；为空时使用当前用户常驻城市")
    private String city;

    @Schema(description = "目标性别：0=男，1=女；为空时不限")
    private Integer gender;
}
