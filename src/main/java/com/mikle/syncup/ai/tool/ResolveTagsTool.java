package com.mikle.syncup.ai.tool;

import com.mikle.syncup.ai.model.agent.TagResolutionIntent;
import com.mikle.syncup.ai.model.tool.AiToolResult;
import com.mikle.syncup.ai.model.vo.TagResolutionResult;
import com.mikle.syncup.ai.service.TagResolutionService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ResolveTagsTool implements AiTool<TagResolutionIntent> {

    public static final String TOOL_NAME = "resolve_tags";

    @Resource
    private TagResolutionService tagResolutionService;

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String type() {
        return "read";
    }

    @Override
    public Class<TagResolutionIntent> intentType() {
        return TagResolutionIntent.class;
    }

    @Override
    public AiToolResult execute(TagResolutionIntent intent, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        TagResolutionResult result = tagResolutionService.resolve(intent == null ? null : intent.getTagQueries());
        return AiToolResult.success(name(), type(), "resolved " + result.getItems().size() + " tag queries", result);
    }
}
