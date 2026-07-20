package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.TeamDraftVO;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.enums.TeamActivityCategoryEnum;
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
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        AiToolResult invalidResult = validateIntent(intent);
        if (invalidResult != null) {
            return invalidResult;
        }
        TeamDraftVO draft = new TeamDraftVO();
        draft.setDraftId(UUID.randomUUID().toString());
        TeamActivityCategoryEnum categoryEnum = TeamActivityCategoryEnum.getEnumByCode(intent.getActivityCategory());
        String activityLabel = StringUtils.defaultIfBlank(intent.getActivityType(), categoryEnum.getName());
        draft.setName(StringUtils.defaultIfBlank(intent.getTeamName(), intent.getCity() + activityLabel + "搭子队"));
        draft.setDescription(StringUtils.defaultIfBlank(intent.getDescription(), "由 AI 根据用户需求生成的组队草稿，确认前不会写入业务表。"));
        draft.setMaxNum(intent.getMemberCount());
        draft.setActivityCategory(intent.getActivityCategory());
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

    private AiToolResult validateIntent(TeamIntent intent) {
        if (intent == null || !TeamActivityCategoryEnum.isValidCode(intent.getActivityCategory())) {
            return AiToolResult.failure(name(), type(), "创建队伍还需要明确活动大类。");
        }
        if (StringUtils.isBlank(intent.getCity())) {
            return AiToolResult.failure(name(), type(), "创建队伍还需要明确城市；不要根据地标或商圈猜测城市。");
        }
        Integer memberCount = intent.getMemberCount();
        if (memberCount == null || memberCount < 1 || memberCount > 20) {
            return AiToolResult.failure(name(), type(), "创建队伍的人数需要在 1 到 20 人之间。");
        }
        if (intent.getBudgetMax() != null && intent.getBudgetMax().signum() < 0) {
            return AiToolResult.failure(name(), type(), "预算不能为负数。");
        }
        if (intent.getStartTime() != null && intent.getStartTime().before(new Date())) {
            return AiToolResult.failure(name(), type(), "活动开始时间不能早于当前时间。");
        }
        return null;
    }
}
