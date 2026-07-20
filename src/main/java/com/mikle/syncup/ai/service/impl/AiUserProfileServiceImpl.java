package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.mapper.AiProfileDraftMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.ai.model.dto.AiProfileConfirmRequest;
import com.mikle.syncup.ai.model.entity.AiProfileDraft;
import com.mikle.syncup.ai.model.vo.AiProfileResponse;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.schema.ProfileExtraction;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.ai.service.ProfileExtractionParser;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

@Service
public class AiUserProfileServiceImpl extends ServiceImpl<AiUserProfileMapper, AiUserProfileEntity>
        implements AiUserProfileService {

    private static final int DRAFT_STATUS_PENDING = 0;

    private static final int DRAFT_STATUS_CONFIRMED = 1;

    private static final int DRAFT_STATUS_REJECTED = 2;

    private static final int PROFILE_STATUS_CONFIRMED = 1;

    private static final int MAX_SOURCE_TEXT_LENGTH = 1000;

    private static final long DRAFT_TTL_MILLIS = 24 * 60 * 60 * 1000L;

    @Resource
    private AiProfileDraftMapper aiProfileDraftMapper;

    @Resource
    private ProfileExtractionParser profileExtractionParser;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public AiProfileResponse getCurrentProfile(User loginUser) {
        validateLoginUser(loginUser);
        AiUserProfileEntity entity = getOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", loginUser.getId())
                .eq("status", PROFILE_STATUS_CONFIRMED)
                .orderByDesc("updateTime")
                .last("limit 1"));
        return toProfileResponse(entity);
    }

    @Override
    public AiProfileResponse createProfileDraft(String sourceText, User loginUser) {
        validateLoginUser(loginUser);
        String sanitizedSourceText = sanitizeSourceText(sourceText);
        ProfileExtraction extraction = profileExtractionParser.parse(sanitizedSourceText);
        AiProfileDraft draft = new AiProfileDraft();
        draft.setDraftId(UUID.randomUUID().toString());
        draft.setUserId(loginUser.getId());
        draft.setSourceText(sanitizedSourceText);
        draft.setProfileJson(writeProfileJson(extraction));
        draft.setStatus(DRAFT_STATUS_PENDING);
        draft.setExpiresAt(new Date(System.currentTimeMillis() + DRAFT_TTL_MILLIS));
        draft.setModelVersion(extraction.getModelVersion());
        int inserted = aiProfileDraftMapper.insert(draft);
        if (inserted <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save profile draft failed");
        }
        return toDraftResponse(draft);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiProfileResponse confirmDraft(String draftId, AiProfileConfirmRequest request, User loginUser) {
        validateLoginUser(loginUser);
        if (StringUtils.isBlank(draftId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draftId is required");
        }
        AiProfileDraft draft = aiProfileDraftMapper.lockByDraftId(draftId.trim());
        if (draft == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "profile draft does not exist");
        }
        if (!draft.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "no permission to confirm this profile draft");
        }
        if (DRAFT_STATUS_CONFIRMED == safeStatus(draft)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profile draft has already been confirmed");
        }
        if (DRAFT_STATUS_REJECTED == safeStatus(draft)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profile draft has been rejected");
        }
        ensureDraftNotExpired(draft);

        ProfileExtraction profile = request != null && request.getProfile() != null
                ? normalizeConfirmedProfile(request.getProfile(), draft)
                : readProfile(draft.getProfileJson());
        Date now = new Date();

        AiUserProfileEntity entity = getOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", loginUser.getId())
                .last("limit 1"));
        if (entity == null) {
            entity = new AiUserProfileEntity();
            entity.setUserId(loginUser.getId());
        }
        entity.setProfileJson(writeProfileJson(profile));
        entity.setSourceText(draft.getSourceText());
        entity.setModelVersion(profile.getModelVersion());
        entity.setStatus(PROFILE_STATUS_CONFIRMED);
        entity.setConfirmedAt(now);
        boolean saved = entity.getId() == null ? this.save(entity) : this.updateById(entity);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save AI user profile failed");
        }

        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setProfile(draft.getSourceText());
        if (userMapper.updateById(updateUser) <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "update user self introduction failed");
        }

        AiProfileDraft updateDraft = new AiProfileDraft();
        updateDraft.setId(draft.getId());
        updateDraft.setStatus(DRAFT_STATUS_CONFIRMED);
        updateDraft.setConfirmedAt(now);
        int updated = aiProfileDraftMapper.updateById(updateDraft);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "confirm profile draft failed");
        }
        return toProfileResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiProfileResponse rejectDraft(String draftId, User loginUser) {
        validateLoginUser(loginUser);
        if (StringUtils.isBlank(draftId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "draftId is required");
        }
        AiProfileDraft draft = aiProfileDraftMapper.lockByDraftId(draftId.trim());
        if (draft == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "profile draft does not exist");
        }
        if (!draft.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "no permission to reject this profile draft");
        }
        if (DRAFT_STATUS_CONFIRMED == safeStatus(draft)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profile draft has already been confirmed");
        }
        ensureDraftNotExpired(draft);
        AiProfileDraft updateDraft = new AiProfileDraft();
        updateDraft.setId(draft.getId());
        updateDraft.setStatus(DRAFT_STATUS_REJECTED);
        int updated = aiProfileDraftMapper.updateById(updateDraft);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "reject profile draft failed");
        }
        draft.setStatus(DRAFT_STATUS_REJECTED);
        return toDraftResponse(draft);
    }

    @Override
    public void createDraftFromUserUpdate(User updateUser, User loginUser) {
        if (updateUser == null || loginUser == null || updateUser.getId() != loginUser.getId()) {
            return;
        }
        String sourceText = StringUtils.defaultIfBlank(updateUser.getProfile(), updateUser.getTags());
        if (StringUtils.isBlank(sourceText)) {
            return;
        }
        createProfileDraft(sourceText, loginUser);
    }

    @Override
    public AiProfileDraft findLatestDraft(long userId) {
        return aiProfileDraftMapper.selectOne(new QueryWrapper<AiProfileDraft>()
                .eq("userId", userId)
                .orderByDesc("createTime")
                .last("limit 1"));
    }

    @Override
    public int deleteExpiredDraftsPhysically() {
        return aiProfileDraftMapper.deleteExpiredPhysically(new Date());
    }

    private void validateLoginUser(User loginUser) {
        if (loginUser == null || loginUser.getId() <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
    }

    private String sanitizeSourceText(String sourceText) {
        String sanitized = StringUtils.defaultString(sourceText).trim()
                .replaceAll("(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*\\S+", "$1=***")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("1[3-9]\\d{9}", "1**********");
        if (StringUtils.isBlank(sanitized)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "sourceText is required");
        }
        if (sanitized.length() > MAX_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "sourceText is too long");
        }
        return sanitized;
    }

    private ProfileExtraction normalizeConfirmedProfile(ProfileExtraction profile, AiProfileDraft draft) {
        profile.setSourceText(draft.getSourceText());
        if (StringUtils.isBlank(profile.getModelVersion())) {
            profile.setModelVersion(draft.getModelVersion());
        }
        if (profile.getConfidence() == null) {
            profile.setConfidence(readProfile(draft.getProfileJson()).getConfidence());
        }
        return profile;
    }

    private void ensureDraftNotExpired(AiProfileDraft draft) {
        if (draft.getExpiresAt() == null || draft.getExpiresAt().after(new Date())) {
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "profile draft has expired");
    }

    private int safeStatus(AiProfileDraft draft) {
        return draft.getStatus() == null ? DRAFT_STATUS_PENDING : draft.getStatus();
    }

    private String writeProfileJson(ProfileExtraction profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "serialize profile extraction failed");
        }
    }

    private ProfileExtraction readProfile(String profileJson) {
        try {
            return objectMapper.readValue(profileJson, ProfileExtraction.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "parse profile extraction failed");
        }
    }

    private AiProfileResponse toProfileResponse(AiUserProfileEntity entity) {
        if (entity == null) {
            return null;
        }
        AiProfileResponse response = new AiProfileResponse();
        response.setUserId(entity.getUserId());
        response.setStatus(entity.getStatus());
        response.setProfile(readProfile(entity.getProfileJson()));
        response.setSourceText(entity.getSourceText());
        response.setModelVersion(entity.getModelVersion());
        response.setConfirmedAt(entity.getConfirmedAt());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private AiProfileResponse toDraftResponse(AiProfileDraft draft) {
        AiProfileResponse response = new AiProfileResponse();
        response.setDraftId(draft.getDraftId());
        response.setUserId(draft.getUserId());
        response.setStatus(draft.getStatus());
        response.setProfile(readProfile(draft.getProfileJson()));
        response.setSourceText(draft.getSourceText());
        response.setModelVersion(draft.getModelVersion());
        response.setExpiresAt(draft.getExpiresAt());
        response.setConfirmedAt(draft.getConfirmedAt());
        response.setUpdateTime(draft.getUpdateTime());
        return response;
    }
}
