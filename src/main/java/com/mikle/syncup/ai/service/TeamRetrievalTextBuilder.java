package com.mikle.syncup.ai.service;

import com.mikle.syncup.model.domain.Team;
import com.mikle.syncup.model.enums.TeamActivityCategoryEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Component
public class TeamRetrievalTextBuilder {

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm";

    public String build(Team team) {
        if (team == null || team.getId() == null) {
            throw new IllegalArgumentException("team is required");
        }
        List<String> parts = new ArrayList<>();
        add(parts, "队伍名称", team.getName());
        TeamActivityCategoryEnum category = TeamActivityCategoryEnum.getEnumByCode(team.getActivityCategory());
        add(parts, "活动大类", category == null ? null : category.getName());
        add(parts, "活动类型", team.getActivityType());
        add(parts, "区域", team.getDistrict());
        add(parts, "水平", team.getSkillLevel());
        if (team.getStartTime() != null) {
            add(parts, "活动时间", new SimpleDateFormat(DATE_PATTERN).format(team.getStartTime()));
        }
        if (team.getDurationMinutes() != null) {
            add(parts, "活动时长", team.getDurationMinutes() + "分钟");
        }
        BigDecimal budget = team.getBudgetPerPerson();
        if (budget != null) {
            add(parts, "人均预算", budget.stripTrailingZeros().toPlainString() + "元");
        }
        if (team.getMaxNum() != null) {
            add(parts, "人数上限", team.getMaxNum() + "人");
        }
        add(parts, "活动描述", team.getDescription());
        return String.join("\n", parts);
    }

    private void add(List<String> parts, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            parts.add(label + "：" + value.trim());
        }
    }
}
