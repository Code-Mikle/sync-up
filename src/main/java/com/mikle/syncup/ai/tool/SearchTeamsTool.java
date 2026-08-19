package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.vo.HybridRecommendationResult;
import com.mikle.syncup.ai.service.AiHybridRecommendationService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.TeamUserVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchTeamsTool implements AiTool {

    public static final String TOOL_NAME = "search_teams";

    @Resource
    private AiHybridRecommendationService recommendationService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "read";
    }

    @Override
    public AiToolResult execute(TeamIntent intent, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        if (intent == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        HybridRecommendationResult<TeamUserVO> result =
                recommendationService.recommendTeams(intent, loginUser, 3);
        List<TeamUserVO> teams = result.items();
        return AiToolResult.success(name(), type(),
                "found " + teams.size() + " teams from " + result.candidateCount()
                        + " candidates, degraded=" + result.degraded(), teams);
    }
}
