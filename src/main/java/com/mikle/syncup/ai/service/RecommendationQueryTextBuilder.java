package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.model.enums.TeamActivityCategoryEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RecommendationQueryTextBuilder {

    public String build(TeamIntent intent, AiUserProfileEntity profile) {
        List<String> parts = new ArrayList<>();
        if (intent != null) {
            add(parts, "当前请求", intent.getSourceText());
            TeamActivityCategoryEnum category = TeamActivityCategoryEnum.getEnumByCode(intent.getActivityCategory());
            add(parts, "活动大类", category == null ? null : category.getName());
            add(parts, "活动类型", intent.getActivityType());
            add(parts, "区域", intent.getDistrict());
            add(parts, "水平", intent.getSkillLevel());
            if (intent.getDurationMinutes() != null) {
                add(parts, "时长", intent.getDurationMinutes() + "分钟");
            }
            if (intent.getBudgetMax() != null) {
                add(parts, "预算上限", intent.getBudgetMax().stripTrailingZeros().toPlainString() + "元");
            }
            if (intent.getTags() != null && !intent.getTags().isEmpty()) {
                add(parts, "本次标签", String.join("、", intent.getTags()));
            }
        }
        if (profile != null) {
            add(parts, "长期匹配画像", profile.getMatchProfileText());
        }
        return String.join("\n", parts);
    }

    private void add(List<String> parts, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            parts.add(label + "：" + value.trim());
        }
    }
}
