package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.AiUserRecommendation;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RecommendUsersTool implements AiTool {

    public static final String TOOL_NAME = "recommendUsers";

    private static final int DEFAULT_LIMIT = 5;

    @Resource
    private UserService userService;

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
        Map<Long, AiUserRecommendation> recommendationMap = new LinkedHashMap<>();

        List<String> desiredTags = buildDesiredTags(intent);
        if (!desiredTags.isEmpty()) {
            List<User> tagMatchedUsers = userService.searchUsersByTags(desiredTags);
            for (User user : tagMatchedUsers) {
                addRecommendation(recommendationMap, user, loginUser, buildTagReasons(desiredTags));
                if (recommendationMap.size() >= DEFAULT_LIMIT) {
                    break;
                }
            }
        }

        if (recommendationMap.size() < DEFAULT_LIMIT) {
            List<User> matchedUsers = userService.matchUsers(DEFAULT_LIMIT, loginUser);
            for (User user : matchedUsers) {
                addRecommendation(recommendationMap, user, loginUser, List.of("与当前用户标签相近"));
                if (recommendationMap.size() >= DEFAULT_LIMIT) {
                    break;
                }
            }
        }

        List<AiUserRecommendation> recommendations = new ArrayList<>(recommendationMap.values());
        return AiToolResult.success(name(), type(), "recommended " + recommendations.size() + " users", recommendations);
    }

    private List<String> buildDesiredTags(TeamIntent intent) {
        List<String> tags = new ArrayList<>();
        if (intent == null) {
            return tags;
        }
        if (StringUtils.isNotBlank(intent.getActivityType())) {
            tags.add(intent.getActivityType().trim());
        }
        if (intent.getTags() != null) {
            for (String tag : intent.getTags()) {
                if (StringUtils.isNotBlank(tag) && !tags.contains(tag.trim())) {
                    tags.add(tag.trim());
                }
            }
        }
        return tags;
    }

    private List<String> buildTagReasons(List<String> desiredTags) {
        return desiredTags.stream()
                .map(tag -> "标签匹配：" + tag)
                .toList();
    }

    private void addRecommendation(Map<Long, AiUserRecommendation> recommendationMap,
                                   User user,
                                   User loginUser,
                                   List<String> reasons) {
        if (user == null || user.getId() <= 0 || user.getId() == loginUser.getId()) {
            return;
        }
        if (recommendationMap.containsKey(user.getId())) {
            recommendationMap.get(user.getId()).getReasons().addAll(reasons);
            return;
        }
        AiUserRecommendation recommendation = new AiUserRecommendation();
        recommendation.setId(user.getId());
        recommendation.setUsername(user.getUsername());
        recommendation.setAvatarUrl(user.getAvatarUrl());
        recommendation.setGender(user.getGender());
        recommendation.setTags(user.getTags());
        recommendation.setPlanetCode(user.getPlanetCode());
        recommendation.setCreateTime(user.getCreateTime());
        recommendation.getReasons().addAll(reasons);
        recommendationMap.put(user.getId(), recommendation);
    }
}
