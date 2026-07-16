package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.AiUserProfile;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import org.springframework.stereotype.Component;

@Component
public class GetMyProfileTool implements AiTool {

    public static final String TOOL_NAME = "getMyProfile";

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
        profile.setPlanetCode(loginUser.getPlanetCode());
        profile.setCreateTime(loginUser.getCreateTime());
        return AiToolResult.success(name(), type(), "loaded current user public profile", profile);
    }
}
