package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mikle.syncup.ai.mapper.AiProfileGenerationTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.entity.AiProfileGenerationTask;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;
import com.mikle.syncup.ai.service.AiUserProfileService;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.UserProfileTextAssembler;
import com.mikle.syncup.ai.service.UserProfileTextGenerator;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
public class AiUserProfileServiceImpl extends ServiceImpl<AiUserProfileMapper, AiUserProfileEntity>
        implements AiUserProfileService {

    private static final int PROFILE_STATUS_ACTIVE = 1;

    private static final int TASK_STATUS_PENDING = 0;
    private static final int TASK_STATUS_PROCESSING = 1;
    private static final int TASK_STATUS_SUCCESS = 2;
    private static final int TASK_STATUS_FAILED = 3;
    private static final int TASK_STATUS_SUPERSEDED = 4;

    private static final int MAX_SOURCE_TEXT_LENGTH = 1000;
    private static final int MAX_ERROR_LENGTH = 1024;
    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 20;
    private static final long PROCESSING_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    @Resource
    private AiProfileGenerationTaskMapper taskMapper;

    @Resource
    private UserProfileTextGenerator generator;

    @Resource
    private ProfileEmbeddingGenerator embeddingGenerator;

    @Resource
    private AiUserProfileEmbeddingMapper embeddingMapper;

    @Resource
    private ProfileEmbeddingCodec embeddingCodec;

    @Resource
    private UserProfileTextAssembler assembler;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onSelfIntroductionChanged(long userId, String sourceText) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId is invalid");
        }
        String sanitizedSourceText = sanitizeSourceText(sourceText);
        supersedeUnfinishedTasks(userId);
        if (StringUtils.isBlank(sanitizedSourceText)) {
            baseMapper.deletePhysicallyByUserId(userId);
            embeddingMapper.deletePhysicallyByUserId(userId);
            return;
        }

        AiProfileGenerationTask task = new AiProfileGenerationTask();
        task.setUserId(userId);
        task.setSourceText(sanitizedSourceText);
        task.setSourceHash(sha256(sanitizedSourceText));
        task.setStatus(TASK_STATUS_PENDING);
        task.setRetryCount(0);
        // MySQL DATETIME may round milliseconds, so make a freshly inserted task immediately claimable.
        task.setNextRetryAt(new Date(System.currentTimeMillis() - 1000L));
        task.setModel(generator.modelName());
        task.setPromptVersion(generator.promptVersion());
        if (taskMapper.insert(task) <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "create AI profile generation task failed");
        }
    }

    @Override
    public int processPendingTasks() {
        if (!generator.isAvailable() || !embeddingGenerator.isAvailable()) {
            return 0;
        }
        recoverTimedOutTasks();
        Date now = new Date();
        List<AiProfileGenerationTask> tasks = taskMapper.selectList(
                new QueryWrapper<AiProfileGenerationTask>()
                        .eq("status", TASK_STATUS_PENDING)
                        .le("nextRetryAt", now)
                        .orderByAsc("id")
                        .last("limit " + BATCH_SIZE));
        int processed = 0;
        for (AiProfileGenerationTask task : tasks) {
            if (!claimTask(task.getId())) {
                continue;
            }
            try {
                GeneratedUserProfile generatedProfile = generator.generate(task.getSourceText());
                String profileText = assembler.renderFull(generatedProfile);
                String matchProfileText = assembler.renderMatch(generatedProfile);
                String interactionProfileText = assembler.renderInteraction(generatedProfile);
                GeneratedEmbedding generatedEmbedding = embeddingGenerator.generate(matchProfileText);
                float[] normalizedEmbedding = embeddingCodec.normalize(generatedEmbedding.vector());
                completeTask(task, profileText, matchProfileText, interactionProfileText,
                        generatedEmbedding.model(), normalizedEmbedding);
            } catch (Exception e) {
                failTask(task, e);
                log.warn("generate AI user profile failed, taskId={}, userId={}",
                        task.getId(), task.getUserId(), e);
            }
            processed++;
        }
        return processed;
    }

    @Override
    public AiUserProfileEntity getInternalProfile(long userId) {
        if (userId <= 0) {
            return null;
        }
        return getOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", userId)
                .eq("status", PROFILE_STATUS_ACTIVE)
                .last("limit 1"));
    }

    @Override
    public String getInteractionProfileText(long userId) {
        AiUserProfileEntity profile = getInternalProfile(userId);
        return profile == null ? null : profile.getInteractionProfileText();
    }

    @Override
    public AiUserProfileEmbedding getActiveEmbedding(long userId) {
        if (userId <= 0) {
            return null;
        }
        return embeddingMapper.selectOne(new QueryWrapper<AiUserProfileEmbedding>()
                .eq("userId", userId)
                .eq("status", PROFILE_STATUS_ACTIVE)
                .last("limit 1"));
    }

    private void completeTask(AiProfileGenerationTask task,
                              String profileText,
                              String matchProfileText,
                              String interactionProfileText,
                              String embeddingModel,
                              float[] normalizedEmbedding) {
        transactionTemplate.executeWithoutResult(status -> {
            if (hasNewerTask(task)) {
                updateTaskStatus(task.getId(), TASK_STATUS_SUPERSEDED, null, null);
                return;
            }
            AiUserProfileEntity current = getInternalProfile(task.getUserId());
            int profileVersion = current == null || current.getProfileVersion() == null
                    ? 1 : current.getProfileVersion() + 1;
            AiUserProfileEntity profile = current == null ? new AiUserProfileEntity() : current;
            profile.setUserId(task.getUserId());
            profile.setProfileText(profileText);
            profile.setMatchProfileText(matchProfileText);
            profile.setInteractionProfileText(interactionProfileText);
            profile.setProfileVersion(profileVersion);
            profile.setSourceHash(task.getSourceHash());
            profile.setModel(generator.modelName());
            profile.setPromptVersion(generator.promptVersion());
            profile.setStatus(PROFILE_STATUS_ACTIVE);
            profile.setGeneratedAt(new Date());
            boolean saved = profile.getId() == null ? save(profile) : updateById(profile);
            if (!saved) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save AI user profile failed");
            }
            embeddingMapper.update(null, new UpdateWrapper<AiUserProfileEmbedding>()
                    .set("status", 0)
                    .eq("userId", task.getUserId())
                    .eq("status", PROFILE_STATUS_ACTIVE));
            AiUserProfileEmbedding embedding = new AiUserProfileEmbedding();
            embedding.setUserId(task.getUserId());
            embedding.setProfileVersion(profileVersion);
            embedding.setMatchTextHash(sha256(matchProfileText));
            embedding.setEmbeddingModel(StringUtils.defaultIfBlank(
                    embeddingModel, embeddingGenerator.modelName()));
            embedding.setDimensions(normalizedEmbedding.length);
            embedding.setVectorJson(embeddingCodec.serialize(normalizedEmbedding));
            embedding.setStatus(PROFILE_STATUS_ACTIVE);
            embedding.setGeneratedAt(new Date());
            if (embeddingMapper.insert(embedding) <= 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "save AI profile embedding failed");
            }
            updateTaskStatus(task.getId(), TASK_STATUS_SUCCESS, profileVersion, null);
        });
    }

    private boolean hasNewerTask(AiProfileGenerationTask task) {
        return taskMapper.selectCount(new QueryWrapper<AiProfileGenerationTask>()
                .eq("userId", task.getUserId())
                .gt("id", task.getId())) > 0;
    }

    private boolean claimTask(long taskId) {
        return taskMapper.update(null, new UpdateWrapper<AiProfileGenerationTask>()
                .set("status", TASK_STATUS_PROCESSING)
                .set("updateTime", new Date())
                .eq("id", taskId)
                .eq("status", TASK_STATUS_PENDING)) > 0;
    }

    private void failTask(AiProfileGenerationTask task, Exception exception) {
        int retryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        boolean retryable = retryCount < MAX_RETRY_COUNT;
        long delayMinutes = retryCount == 1 ? 1 : 5;
        taskMapper.update(null, new UpdateWrapper<AiProfileGenerationTask>()
                .set("status", retryable ? TASK_STATUS_PENDING : TASK_STATUS_FAILED)
                .set("retryCount", retryCount)
                .set("nextRetryAt", retryable
                        ? new Date(System.currentTimeMillis() + delayMinutes * 60 * 1000L) : null)
                .set("lastError", truncateError(exception))
                .eq("id", task.getId())
                .eq("status", TASK_STATUS_PROCESSING));
    }

    private void updateTaskStatus(long taskId, int status, Integer profileVersion, String error) {
        taskMapper.update(null, new UpdateWrapper<AiProfileGenerationTask>()
                .set("status", status)
                .set("profileVersion", profileVersion)
                .set("lastError", error)
                .set("nextRetryAt", null)
                .eq("id", taskId));
    }

    private void recoverTimedOutTasks() {
        Date timeout = new Date(System.currentTimeMillis() - PROCESSING_TIMEOUT_MILLIS);
        taskMapper.update(null, new UpdateWrapper<AiProfileGenerationTask>()
                .set("status", TASK_STATUS_PENDING)
                .set("nextRetryAt", new Date())
                .eq("status", TASK_STATUS_PROCESSING)
                .lt("updateTime", timeout));
    }

    private void supersedeUnfinishedTasks(long userId) {
        taskMapper.update(null, new UpdateWrapper<AiProfileGenerationTask>()
                .set("status", TASK_STATUS_SUPERSEDED)
                .set("nextRetryAt", null)
                .eq("userId", userId)
                .in("status", TASK_STATUS_PENDING, TASK_STATUS_PROCESSING, TASK_STATUS_FAILED));
    }

    private String sanitizeSourceText(String sourceText) {
        String sanitized = StringUtils.defaultString(sourceText).trim()
                .replaceAll("(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*\\S+", "$1=***")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("1[3-9]\\d{9}", "1**********");
        if (sanitized.length() > MAX_SOURCE_TEXT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "self introduction is too long");
        }
        return sanitized;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String truncateError(Exception exception) {
        String message = StringUtils.defaultIfBlank(exception.getMessage(), exception.getClass().getSimpleName());
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
