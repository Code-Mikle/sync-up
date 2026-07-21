package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.vo.AiProfileResponse;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.agent.TeamIntent;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class PrepareProfileUpdateTool implements AiTool {

    public static final String TOOL_NAME = "prepare_profile_update";

    private static final int MAX_PROFILE_LENGTH = 500;

    @Resource
    private AiUserProfileService aiUserProfileService;

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

        AiProfileResponse extraction = aiUserProfileService.createProfileDraft(profileText, loginUser);
        return AiToolResult.success(name(), type(), "created a profile draft for user confirmation", extraction);
    }

    private String sanitizeProfileText(String profileText) {
        return profileText
                .replaceAll("(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*\\S+", "$1=***")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("1[3-9]\\d{9}", "1**********");
    }
}
