package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.AiProfileResponse;
import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import com.mikle.syncup.service.UserService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateMyProfileTool implements AiTool {

    public static final String TOOL_NAME = "updateMyProfile";

    private static final int MAX_PROFILE_LENGTH = 500;

    @Resource
    private UserService userService;

    @Resource
    private AiUserProfileService aiUserProfileService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "write";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiToolResult execute(TeamIntent intent, User loginUser) {
        if (loginUser == null || loginUser.getId() <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        String profileText = intent == null ? null : intent.getProfileText();
        if (StringUtils.isBlank(profileText)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profileText is required");
        }
        profileText = sanitizeProfileText(profileText.trim());
        if (profileText.length() > MAX_PROFILE_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profileText is too long");
        }

        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setProfile(profileText);
        boolean updated = userService.updateById(updateUser);
        if (!updated) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "update user profile failed");
        }

        AiProfileResponse extraction = aiUserProfileService.extractProfile(profileText, loginUser);
        AiProfileResponse confirmedProfile = aiUserProfileService.confirmExtraction(extraction.getTaskId(), null, loginUser);
        return AiToolResult.success(name(), type(), "updated current user's self introduction and structured profile", confirmedProfile);
    }

    private String sanitizeProfileText(String profileText) {
        return profileText
                .replaceAll("(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*\\S+", "$1=***")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("1[3-9]\\d{9}", "1**********");
    }
}
