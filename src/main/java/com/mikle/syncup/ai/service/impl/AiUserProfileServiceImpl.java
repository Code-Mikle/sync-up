package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.mapper.AiProfileExtractionTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.ai.model.AiProfileConfirmRequest;
import com.mikle.syncup.ai.model.AiProfileExtractionTask;
import com.mikle.syncup.ai.model.AiProfileResponse;
import com.mikle.syncup.ai.model.AiUserProfileEntity;
import com.mikle.syncup.ai.model.ProfileExtraction;
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

    private static final int TASK_STATUS_EXTRACTED = 1;

    private static final int TASK_STATUS_CONFIRMED = 2;

    private static final int TASK_STATUS_REJECTED = 3;

    private static final int PROFILE_STATUS_CONFIRMED = 1;

    private static final int MAX_SOURCE_TEXT_LENGTH = 1000;

    @Resource
    private AiProfileExtractionTaskMapper aiProfileExtractionTaskMapper;

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
    public AiProfileResponse extractProfile(String sourceText, User loginUser) {
        validateLoginUser(loginUser);
        String sanitizedSourceText = sanitizeSourceText(sourceText);
        ProfileExtraction extraction = profileExtractionParser.parse(sanitizedSourceText);
        AiProfileExtractionTask task = new AiProfileExtractionTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setUserId(loginUser.getId());
        task.setSourceText(sanitizedSourceText);
        task.setExtractionJson(writeProfileJson(extraction));
        task.setStatus(TASK_STATUS_EXTRACTED);
        task.setRetryCount(0);
        task.setModelVersion(extraction.getModelVersion());
        int inserted = aiProfileExtractionTaskMapper.insert(task);
        if (inserted <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save profile extraction task failed");
        }
        return toTaskResponse(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiProfileResponse confirmExtraction(String taskId, AiProfileConfirmRequest request, User loginUser) {
        validateLoginUser(loginUser);
        if (StringUtils.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskId is required");
        }
        AiProfileExtractionTask task = aiProfileExtractionTaskMapper.lockByTaskId(taskId.trim());
        if (task == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "profile extraction task does not exist");
        }
        if (!task.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "no permission to confirm this profile task");
        }
        if (TASK_STATUS_CONFIRMED == safeStatus(task)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profile task has already been confirmed");
        }
        if (TASK_STATUS_REJECTED == safeStatus(task)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "profile task has been rejected");
        }

        ProfileExtraction profile = request != null && request.getProfile() != null
                ? normalizeConfirmedProfile(request.getProfile(), task)
                : readProfile(task.getExtractionJson());
        Date now = new Date();

        AiUserProfileEntity entity = getOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", loginUser.getId())
                .last("limit 1"));
        if (entity == null) {
            entity = new AiUserProfileEntity();
            entity.setUserId(loginUser.getId());
        }
        entity.setProfileJson(writeProfileJson(profile));
        entity.setSourceText(task.getSourceText());
        entity.setModelVersion(profile.getModelVersion());
        entity.setStatus(PROFILE_STATUS_CONFIRMED);
        entity.setConfirmedAt(now);
        boolean saved = entity.getId() == null ? this.save(entity) : this.updateById(entity);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save AI user profile failed");
        }

        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setProfile(task.getSourceText());
        if (userMapper.updateById(updateUser) <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "update user self introduction failed");
        }

        AiProfileExtractionTask updateTask = new AiProfileExtractionTask();
        updateTask.setId(task.getId());
        updateTask.setStatus(TASK_STATUS_CONFIRMED);
        int updated = aiProfileExtractionTaskMapper.updateById(updateTask);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "confirm profile extraction task failed");
        }
        return toProfileResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiProfileResponse rejectExtraction(String taskId, User loginUser) {
        validateLoginUser(loginUser);
        if (StringUtils.isBlank(taskId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "taskId is required");
        }
        AiProfileExtractionTask task = aiProfileExtractionTaskMapper.lockByTaskId(taskId.trim());
        if (task == null) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "profile extraction task does not exist");
        }
        if (!task.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH, "no permission to reject this profile task");
        }
        AiProfileExtractionTask updateTask = new AiProfileExtractionTask();
        updateTask.setId(task.getId());
        updateTask.setStatus(TASK_STATUS_REJECTED);
        int updated = aiProfileExtractionTaskMapper.updateById(updateTask);
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "reject profile extraction task failed");
        }
        task.setStatus(TASK_STATUS_REJECTED);
        return toTaskResponse(task);
    }

    @Override
    public void createExtractionTaskFromUserUpdate(User updateUser, User loginUser) {
        if (updateUser == null || loginUser == null || updateUser.getId() != loginUser.getId()) {
            return;
        }
        String sourceText = StringUtils.defaultIfBlank(updateUser.getProfile(), updateUser.getTags());
        if (StringUtils.isBlank(sourceText)) {
            return;
        }
        extractProfile(sourceText, loginUser);
    }

    @Override
    public AiProfileExtractionTask findLatestTask(long userId) {
        return aiProfileExtractionTaskMapper.selectOne(new QueryWrapper<AiProfileExtractionTask>()
                .eq("userId", userId)
                .orderByDesc("createTime")
                .last("limit 1"));
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

    private ProfileExtraction normalizeConfirmedProfile(ProfileExtraction profile, AiProfileExtractionTask task) {
        profile.setSourceText(task.getSourceText());
        if (StringUtils.isBlank(profile.getModelVersion())) {
            profile.setModelVersion(task.getModelVersion());
        }
        if (profile.getConfidence() == null) {
            profile.setConfidence(readProfile(task.getExtractionJson()).getConfidence());
        }
        return profile;
    }

    private int safeStatus(AiProfileExtractionTask task) {
        return task.getStatus() == null ? TASK_STATUS_EXTRACTED : task.getStatus();
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

    private AiProfileResponse toTaskResponse(AiProfileExtractionTask task) {
        AiProfileResponse response = new AiProfileResponse();
        response.setTaskId(task.getTaskId());
        response.setUserId(task.getUserId());
        response.setStatus(task.getStatus());
        response.setProfile(readProfile(task.getExtractionJson()));
        response.setSourceText(task.getSourceText());
        response.setModelVersion(task.getModelVersion());
        response.setUpdateTime(task.getUpdateTime());
        return response;
    }
}
