package com.mikle.syncup.ai.service;

import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.model.vo.AiUserRecommendation;
import com.mikle.syncup.ai.model.vo.HybridRecommendationResult;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.model.vo.TeamUserVO;

public interface AiHybridRecommendationService {

    HybridRecommendationResult<AiUserRecommendation> recommendUsers(
            TeamIntent intent, User loginUser, int limit);

    HybridRecommendationResult<TeamUserVO> recommendTeams(
            TeamIntent intent, User loginUser, int limit);
}
