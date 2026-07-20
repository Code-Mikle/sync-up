package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.AiProfileResponse;
import com.mikle.syncup.ai.model.vo.AiUserProfile;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GetMyProfileTool implements AiTool {

    public static final String TOOL_NAME = "getMyProfile";

    @Resource
    private AiUserProfileService aiUserProfileService;

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
        AiUserProfile profile = new AiUserProfile();
        profile.setId(loginUser.getId());
        profile.setUsername(loginUser.getUsername());
        profile.setAvatarUrl(loginUser.getAvatarUrl());
        profile.setGender(loginUser.getGender());
        profile.setTags(loginUser.getTags());
        profile.setProfile(loginUser.getProfile());
        profile.setCity(loginUser.getCity());
        profile.setPlanetCode(loginUser.getPlanetCode());
        profile.setCreateTime(loginUser.getCreateTime());
        try {
            AiProfileResponse structuredProfile = aiUserProfileService.getCurrentProfile(loginUser);
            if (structuredProfile != null) {
                profile.setStructuredProfile(structuredProfile.getProfile());
            }
        } catch (Exception e) {
            log.warn("load structured AI profile failed, userId={}", loginUser.getId(), e);
        }
        return AiToolResult.success(name(), type(), "loaded current user public profile", profile);
    }
}
