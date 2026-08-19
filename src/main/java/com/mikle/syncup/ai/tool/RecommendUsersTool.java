package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.vo.HybridRecommendationResult;
import com.mikle.syncup.ai.model.vo.AiUserRecommendation;
import com.mikle.syncup.ai.service.AiHybridRecommendationService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecommendUsersTool implements AiTool {

    public static final String TOOL_NAME = "recommend_users";

    private static final int DEFAULT_LIMIT = 3;

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
        HybridRecommendationResult<AiUserRecommendation> result =
                recommendationService.recommendUsers(intent, loginUser, DEFAULT_LIMIT);
        List<AiUserRecommendation> recommendations = result.items();
        return AiToolResult.success(
                name(),
                type(),
                "recommended " + recommendations.size()
                        + " users from " + result.candidateCount()
                        + " candidates, degraded=" + result.degraded(),
                recommendations
        );
    }
}
