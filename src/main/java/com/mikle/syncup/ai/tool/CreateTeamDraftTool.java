package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamDraft;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.model.domain.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
public class CreateTeamDraftTool implements AiTool {

    public static final String TOOL_NAME = "createTeamDraft";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "draft";
    }

    @Override
    public AiToolResult execute(TeamIntent intent, User loginUser) {
        TeamDraft draft = new TeamDraft();
        draft.setDraftId(UUID.randomUUID().toString());
        draft.setName(StringUtils.defaultIfBlank(intent.getTeamName(), intent.getCity() + intent.getActivityType() + "搭子队"));
        draft.setDescription(StringUtils.defaultIfBlank(intent.getDescription(), "由 AI 根据用户需求生成的组队草稿，确认前不会写入业务表。"));
        draft.setMaxNum(intent.getMemberCount());
        draft.setActivityType(intent.getActivityType());
        draft.setCity(intent.getCity());
        draft.setDistrict(intent.getDistrict());
        draft.setStartTime(intent.getStartTime());
        draft.setDurationMinutes(intent.getDurationMinutes());
        draft.setBudgetPerPerson(intent.getBudgetMax());
        draft.setSkillLevel(intent.getSkillLevel());
        draft.setExpiresAt(new Date(System.currentTimeMillis() + 30 * 60 * 1000));
        return AiToolResult.success(name(), type(), "created a team draft without writing business tables", draft);
    }
}
