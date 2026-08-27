package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.enums.ProfileStatus;
import com.mikle.syncup.ai.service.AiMemoryPipelineService;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class AiUserProfileServiceImpl extends ServiceImpl<AiUserProfileMapper, AiUserProfileEntity>
        implements AiUserProfileService {

    @Resource
    private AiMemoryPipelineService memoryPipelineService;

    @Resource
    private AiUserProfileEmbeddingMapper embeddingMapper;

    @Override
    public void onSelfIntroductionChanged(long userId, String sourceText) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId is invalid");
        }
        memoryPipelineService.onSelfIntroductionChanged(userId, sanitize(sourceText));
    }

    @Override
    public AiUserProfileEntity getInternalProfile(long userId) {
        if (userId <= 0) {
            return null;
        }
        return getOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", userId)
                .eq("status", ProfileStatus.ACTIVE.name())
                .last("limit 1"));
    }

    @Override
    public AiUserProfileEmbedding getActiveEmbedding(long userId) {
        if (userId <= 0) {
            return null;
        }
        return embeddingMapper.selectOne(new QueryWrapper<AiUserProfileEmbedding>()
                .eq("userId", userId)
                .eq("status", 1)
                .last("limit 1"));
    }

    @Override
    public String getInteractionProfileText(long userId) {
        AiUserProfileEntity profile = getInternalProfile(userId);
        return profile == null ? null : profile.getInteractionProfileText();
    }

    private String sanitize(String sourceText) {
        String sanitized = StringUtils.defaultString(sourceText).trim()
                .replaceAll("(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*\\S+", "$1=***")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("1[3-9]\\d{9}", "1**********");
        if (sanitized.length() > 1000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "self introduction is too long");
        }
        return sanitized;
    }
}
