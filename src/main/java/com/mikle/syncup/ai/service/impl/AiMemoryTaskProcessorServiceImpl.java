package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.mapper.AiEpisodeExtractionTaskMapper;
import com.mikle.syncup.ai.mapper.AiProfileUpdateTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserEpisodeMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileEmbeddingMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileRevisionMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.entity.AiEpisodeExtractionTask;
import com.mikle.syncup.ai.model.entity.AiProfileUpdateTask;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.entity.AiUserProfileEmbedding;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.entity.AiUserProfileRevision;
import com.mikle.syncup.ai.model.enums.EpisodePriority;
import com.mikle.syncup.ai.model.enums.EpisodeSignalType;
import com.mikle.syncup.ai.model.enums.EpisodeStatus;
import com.mikle.syncup.ai.model.enums.MemorySourceType;
import com.mikle.syncup.ai.model.enums.MemoryTaskStatus;
import com.mikle.syncup.ai.model.enums.ProfileStatus;
import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;
import com.mikle.syncup.ai.model.schema.GeneratedEmbedding;
import com.mikle.syncup.ai.model.schema.GeneratedEpisode;
import com.mikle.syncup.ai.model.schema.GeneratedEpisodeExtraction;
import com.mikle.syncup.ai.model.schema.GeneratedUserProfile;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiMemoryPipelineService;
import com.mikle.syncup.ai.service.AiMemoryTaskProcessorService;
import com.mikle.syncup.ai.service.AiProfileUpdateTaskService;
import com.mikle.syncup.ai.service.EpisodeExtractor;
import com.mikle.syncup.ai.service.ProfileDimensionGenerator;
import com.mikle.syncup.ai.service.ProfileEmbeddingCodec;
import com.mikle.syncup.ai.service.ProfileEmbeddingGenerator;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.ai.service.UserProfileTextAssembler;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiMemoryTaskProcessorServiceImpl implements AiMemoryTaskProcessorService {

    private static final String UNKNOWN = "暂未观察到明确偏好";

    @Resource private AiMemoryProperties properties;
    @Resource private AiEpisodeExtractionTaskMapper extractionTaskMapper;
    @Resource private AiProfileUpdateTaskMapper profileTaskMapper;
    @Resource private AiUserEpisodeMapper episodeMapper;
    @Resource private AiUserProfileMapper profileMapper;
    @Resource private AiUserProfileEmbeddingMapper embeddingMapper;
    @Resource private AiUserProfileRevisionMapper revisionMapper;
    @Resource private AiChatSessionMapper sessionMapper;
    @Resource private AiChatMessageService chatMessageService;
    @Resource private AiMemoryPipelineService memoryPipelineService;
    @Resource private AiProfileUpdateTaskService profileTaskService;
    @Resource private EpisodeExtractor episodeExtractor;
    @Resource private ProfileDimensionGenerator dimensionGenerator;
    @Resource private UserProfileTextAssembler profileAssembler;
    @Resource private ProfileEmbeddingGenerator embeddingGenerator;
    @Resource private ProfileEmbeddingCodec embeddingCodec;
    @Resource private TextHashService textHashService;
    @Resource private ObjectMapper objectMapper;
    @Resource private TransactionTemplate transactionTemplate;

    @Override
    public int processEpisodeExtractionTasks() {
        if (!episodeExtractor.isAvailable()) return 0;
        recoverTimedOutExtractionTasks();
        List<AiEpisodeExtractionTask> tasks = extractionTaskMapper.selectList(new QueryWrapper<AiEpisodeExtractionTask>()
                .eq("status", MemoryTaskStatus.PENDING.name()).le("nextRetryAt", new Date())
                .orderByAsc("id").last("limit " + properties.getEpisode().getBatchSize()));
        int processed = 0;
        for (AiEpisodeExtractionTask listed : tasks) {
            if (!claimExtractionTask(listed.getId())) continue;
            try {
                PreparedExtraction prepared = prepareExtraction(listed.getId());
                ExtractionCommitResult result = transactionTemplate.execute(status -> commitExtraction(prepared));
                if (result != null) afterExtractionCommitted(result);
            } catch (StaleTaskException e) {
                supersedeExtractionTask(listed.getId());
            } catch (RuntimeException e) {
                retryClaimedExtractionTask(listed.getId(), e);
            }
            processed++;
        }
        return processed;
    }

    @Override
    public int processProfileUpdateTasks() {
        if (!dimensionGenerator.isAvailable()) return 0;
        recoverTimedOutProfileTasks();
        profileTaskService.enqueueOverdueTasks();
        List<AiProfileUpdateTask> tasks = profileTaskMapper.selectList(new QueryWrapper<AiProfileUpdateTask>()
                .eq("status", MemoryTaskStatus.PENDING.name()).le("nextRetryAt", new Date())
                .orderByAsc("id").last("limit " + properties.getProfileUpdate().getBatchSize()));
        int processed = 0;
        for (AiProfileUpdateTask listed : tasks) {
            if (!claimProfileTask(listed.getId())) continue;
            try {
                PreparedProfile prepared = prepareProfile(listed.getId());
                transactionTemplate.executeWithoutResult(status -> commitProfile(prepared));
                profileTaskService.enqueueIfNecessary(prepared.userId(), prepared.profileType(),
                        ProfileUpdateTriggerType.COUNT, false);
            } catch (StaleTaskException e) {
                supersedeProfileTask(listed.getId());
                AiProfileUpdateTask stale = profileTaskMapper.selectById(listed.getId());
                if (stale != null) {
                    profileTaskService.enqueueIfNecessary(stale.getUserId(), ProfileType.valueOf(stale.getProfileType()),
                            ProfileUpdateTriggerType.REBUILD, true);
                }
            } catch (RuntimeException e) {
                retryClaimedProfileTask(listed.getId(), e);
            }
            processed++;
        }
        return processed;
    }

    private PreparedExtraction prepareExtraction(long taskId) {
        AiEpisodeExtractionTask task = requiredProcessingExtractionTask(taskId);
        List<AiChatMessage> messages = sourceMessages(task);
        List<AiUserEpisode> correctionCandidates = correctionCandidates(task.getUserId());
        GeneratedEpisodeExtraction extraction = episodeExtractor.extract(
                buildExtractionSource(task, messages, correctionCandidates));
        return new PreparedExtraction(taskId, messages, correctionCandidates, extraction);
    }

    private ExtractionCommitResult commitExtraction(PreparedExtraction prepared) {
        AiEpisodeExtractionTask task = requiredProcessingExtractionTask(prepared.taskId());
        if (MemorySourceType.CHAT_MESSAGE.name().equals(task.getSourceType())) {
            AiChatSession session = sessionMapper.selectByIdForUpdate(task.getChatSessionId());
            if (session == null || !task.getFromMessageIdExclusive().equals(session.getLastEpisodeExtractedMessageId())) {
                throw new StaleTaskException();
            }
        }
        List<AiUserEpisode> saved = saveEpisodes(
                task, prepared.messages(), prepared.correctionCandidates(), prepared.extraction());
        if (MemorySourceType.CHAT_MESSAGE.name().equals(task.getSourceType())) {
            int advanced = sessionMapper.update(null, new UpdateWrapper<AiChatSession>()
                    .set("lastEpisodeExtractedMessageId", task.getToMessageIdInclusive())
                    .eq("id", task.getChatSessionId())
                    .eq("lastEpisodeExtractedMessageId", task.getFromMessageIdExclusive()));
            if (advanced != 1) throw new StaleTaskException();
        }
        int succeeded = extractionTaskMapper.update(null, new UpdateWrapper<AiEpisodeExtractionTask>()
                .set("status", MemoryTaskStatus.SUCCESS.name()).set("lastError", null)
                .set("model", episodeExtractor.modelName()).set("promptVersion", episodeExtractor.promptVersion())
                .eq("id", task.getId()).eq("status", MemoryTaskStatus.PROCESSING.name()));
        if (succeeded != 1) throw new StaleTaskException();
        Map<ProfileType, Boolean> triggers = new EnumMap<>(ProfileType.class);
        boolean selfIntroduction = MemorySourceType.SELF_INTRODUCTION.name().equals(task.getSourceType());
        for (AiUserEpisode episode : saved) {
            ProfileType type = ProfileType.valueOf(episode.getProfileType());
            boolean immediate = selfIntroduction || EpisodePriority.IMMEDIATE.name().equals(episode.getPriority());
            triggers.merge(type, immediate, Boolean::logicalOr);
        }
        return new ExtractionCommitResult(task.getUserId(), task.getChatSessionId(), selfIntroduction, triggers);
    }

    private void afterExtractionCommitted(ExtractionCommitResult result) {
        if (result.selfIntroduction()) {
            profileTaskService.enqueueRebuildAll(result.userId(), ProfileUpdateTriggerType.SELF_INTRODUCTION_CHANGED);
        }
        for (Map.Entry<ProfileType, Boolean> entry : result.triggers().entrySet()) {
            if (result.selfIntroduction()) continue;
            profileTaskService.enqueueIfNecessary(result.userId(), entry.getKey(),
                    entry.getValue() ? ProfileUpdateTriggerType.IMMEDIATE : ProfileUpdateTriggerType.COUNT,
                    entry.getValue());
        }
        if (result.chatSessionId() != null) {
            memoryPipelineService.createNextChatExtractionTaskIfNecessary(result.userId(), result.chatSessionId());
        }
    }

    private PreparedProfile prepareProfile(long taskId) {
        AiProfileUpdateTask task = requiredProcessingProfileTask(taskId);
        ProfileType type = ProfileType.valueOf(task.getProfileType());
        AiUserProfileEntity existing = activeOrRebuildProfile(task.getUserId());
        int currentVersion = profileVersion(existing);
        if (task.getExpectedProfileVersion() == null || task.getExpectedProfileVersion() != currentVersion) {
            throw new StaleTaskException();
        }
        List<AiUserEpisode> evidence = episodeMapper.selectList(new QueryWrapper<AiUserEpisode>()
                .eq("userId", task.getUserId()).eq("profileType", type.name())
                .in("status", EpisodeStatus.PENDING.name(), EpisodeStatus.CONSOLIDATED.name())
                .orderByDesc("observedAt").orderByDesc("id").last("limit 500"));
        java.util.Collections.reverse(evidence);
        List<Long> pendingIds = evidence.stream().filter(e -> EpisodeStatus.PENDING.name().equals(e.getStatus()))
                .map(AiUserEpisode::getId).toList();
        List<Long> supersededIds = evidence.stream()
                .filter(e -> EpisodeStatus.PENDING.name().equals(e.getStatus()))
                .flatMap(e -> readIds(e.getSupersededEpisodeIds()).stream())
                .distinct().toList();
        String oldDimension = dimensionText(existing, type);
        String newDimension = dimensionGenerator.generate(type, oldDimension, evidence);
        GeneratedUserProfile generated = mergeDimension(existing, type, newDimension);
        String fullText = profileAssembler.renderFull(generated);
        String matchText = profileAssembler.renderMatch(generated);
        String interactionText = profileAssembler.renderInteraction(generated);
        PreparedEmbedding embedding = prepareEmbedding(task.getUserId(), matchText);
        return new PreparedProfile(taskId, task.getUserId(), type, currentVersion, oldDimension, newDimension,
                generated, fullText, matchText, interactionText, pendingIds,
                evidence.stream().map(AiUserEpisode::getId).toList(), supersededIds, embedding);
    }

    private void commitProfile(PreparedProfile prepared) {
        AiProfileUpdateTask task = requiredProcessingProfileTask(prepared.taskId());
        AiUserProfileEntity current = activeOrRebuildProfile(prepared.userId());
        if (profileVersion(current) != prepared.expectedVersion()) throw new StaleTaskException();
        validateEvidenceSnapshot(prepared);

        if (!prepared.supersededEpisodeIds().isEmpty()) {
            episodeMapper.update(null, new UpdateWrapper<AiUserEpisode>()
                    .set("status", EpisodeStatus.INVALID.name())
                    .in("id", prepared.supersededEpisodeIds())
                    .eq("userId", prepared.userId())
                    .eq("profileType", prepared.profileType().name())
                    .ne("status", EpisodeStatus.INVALID.name()));
        }

        int nextVersion = prepared.expectedVersion() + 1;
        AiUserProfileEntity profile = current == null ? new AiUserProfileEntity() : current;
        boolean rebuildPending = shouldRemainRebuildRequired(task);
        applyGeneratedProfile(profile, prepared, nextVersion, rebuildPending);
        if (current == null) {
            profileMapper.insert(profile);
        } else {
            int updated = profileMapper.update(profile, new UpdateWrapper<AiUserProfileEntity>()
                    .eq("id", current.getId()).eq("profileVersion", prepared.expectedVersion()));
            if (updated != 1) throw new StaleTaskException();
        }
        switchEmbedding(prepared, nextVersion, !rebuildPending);
        if (!prepared.pendingEpisodeIds().isEmpty()) {
            episodeMapper.update(null, new UpdateWrapper<AiUserEpisode>()
                    .set("status", EpisodeStatus.CONSOLIDATED.name())
                    .set("consolidatedProfileVersion", nextVersion)
                    .in("id", prepared.pendingEpisodeIds()).eq("status", EpisodeStatus.PENDING.name()));
        }
        saveRevision(task, prepared, nextVersion);
        int succeeded = profileTaskMapper.update(null, new UpdateWrapper<AiProfileUpdateTask>()
                .set("status", MemoryTaskStatus.SUCCESS.name()).set("lastError", null)
                .set("model", dimensionGenerator.modelName()).set("promptVersion", dimensionGenerator.promptVersion())
                .eq("id", task.getId()).eq("status", MemoryTaskStatus.PROCESSING.name()));
        if (succeeded != 1) throw new StaleTaskException();
        profileTaskMapper.update(null, new UpdateWrapper<AiProfileUpdateTask>()
                .set("status", MemoryTaskStatus.SUPERSEDED.name())
                .eq("userId", prepared.userId()).eq("profileType", prepared.profileType().name())
                .eq("status", MemoryTaskStatus.PENDING.name()).ne("id", task.getId()));
    }

    private void validateEvidenceSnapshot(PreparedProfile prepared) {
        if (!prepared.evidenceEpisodeIds().isEmpty()) {
            long valid = episodeMapper.selectCount(new QueryWrapper<AiUserEpisode>()
                    .in("id", prepared.evidenceEpisodeIds()).ne("status", EpisodeStatus.INVALID.name()));
            if (valid != prepared.evidenceEpisodeIds().size()) throw new StaleTaskException();
        }
        if (!prepared.pendingEpisodeIds().isEmpty()) {
            long pending = episodeMapper.selectCount(new QueryWrapper<AiUserEpisode>()
                    .in("id", prepared.pendingEpisodeIds()).eq("status", EpisodeStatus.PENDING.name()));
            if (pending != prepared.pendingEpisodeIds().size()) throw new StaleTaskException();
        }
    }

    private boolean shouldRemainRebuildRequired(AiProfileUpdateTask currentTask) {
        if (!ProfileUpdateTriggerType.SELF_INTRODUCTION_CHANGED.name().equals(currentTask.getTriggerType())
                && !ProfileUpdateTriggerType.SOURCE_DELETED.name().equals(currentTask.getTriggerType())
                && !ProfileUpdateTriggerType.REBUILD.name().equals(currentTask.getTriggerType())) {
            return false;
        }
        return profileTaskMapper.selectCount(new QueryWrapper<AiProfileUpdateTask>()
                .eq("userId", currentTask.getUserId()).ne("id", currentTask.getId())
                .in("triggerType", ProfileUpdateTriggerType.SELF_INTRODUCTION_CHANGED.name(),
                        ProfileUpdateTriggerType.SOURCE_DELETED.name(), ProfileUpdateTriggerType.REBUILD.name())
                .in("status", MemoryTaskStatus.PENDING.name(), MemoryTaskStatus.PROCESSING.name())) > 0;
    }

    private void applyGeneratedProfile(AiUserProfileEntity profile, PreparedProfile prepared,
                                       int version, boolean rebuildPending) {
        GeneratedUserProfile generated = prepared.generated();
        profile.setUserId(prepared.userId());
        profile.setActivityPreferenceText(generated.getInterestAndActivityPreference());
        profile.setSocialPersonalityText(generated.getSocialAndPersonalityTendency());
        profile.setPartnerPreferenceText(generated.getPartnerMatchingPreference());
        profile.setActivityConstraintHabitText(generated.getActivityConstraintsAndHabits());
        profile.setAiInteractionPreferenceText(generated.getAiInteractionPreference());
        profile.setProfileText(prepared.fullText());
        profile.setMatchProfileText(prepared.matchText());
        profile.setInteractionProfileText(prepared.interactionText());
        profile.setProfileVersion(version);
        profile.setEvidenceDigest(textHashService.sha256(prepared.evidenceEpisodeIds().toString()));
        profile.setModel(dimensionGenerator.modelName());
        profile.setPromptVersion(dimensionGenerator.promptVersion());
        profile.setStatus(rebuildPending ? ProfileStatus.REBUILD_REQUIRED.name() : ProfileStatus.ACTIVE.name());
        profile.setGeneratedAt(new Date());
    }

    private void saveRevision(AiProfileUpdateTask task, PreparedProfile prepared, int nextVersion) {
        AiUserProfileRevision revision = new AiUserProfileRevision();
        revision.setUserId(prepared.userId());
        revision.setProfileType(prepared.profileType().name());
        revision.setFromProfileVersion(prepared.expectedVersion() == 0 ? null : prepared.expectedVersion());
        revision.setToProfileVersion(nextVersion);
        revision.setTriggerType(task.getTriggerType());
        revision.setOldContent(prepared.oldDimension());
        revision.setNewContent(prepared.newDimension());
        revision.setEvidenceEpisodeIds(writeJson(prepared.evidenceEpisodeIds()));
        revision.setModel(dimensionGenerator.modelName());
        revision.setPromptVersion(dimensionGenerator.promptVersion());
        revisionMapper.insert(revision);
    }

    private PreparedEmbedding prepareEmbedding(long userId, String matchText) {
        String hash = textHashService.sha256(matchText);
        AiUserProfileEmbedding reusable = embeddingMapper.selectOne(new QueryWrapper<AiUserProfileEmbedding>()
                .eq("userId", userId).orderByDesc("profileVersion").last("limit 1"));
        if (reusable != null && hash.equals(reusable.getMatchTextHash())) {
            return new PreparedEmbedding(hash, reusable.getEmbeddingModel(), reusable.getDimensions(), reusable.getVectorJson());
        }
        if (!embeddingGenerator.isAvailable()) return null;
        GeneratedEmbedding generated = embeddingGenerator.generate(matchText);
        float[] normalized = embeddingCodec.normalize(generated.vector());
        return new PreparedEmbedding(hash, generated.model(), normalized.length, embeddingCodec.serialize(normalized));
    }

    private void switchEmbedding(PreparedProfile prepared, int version, boolean activate) {
        embeddingMapper.update(null, new UpdateWrapper<AiUserProfileEmbedding>()
                .set("status", 0).eq("userId", prepared.userId()).eq("status", 1));
        if (prepared.embedding() == null) return;
        PreparedEmbedding candidate = prepared.embedding();
        AiUserProfileEmbedding embedding = new AiUserProfileEmbedding();
        embedding.setUserId(prepared.userId());
        embedding.setProfileVersion(version);
        embedding.setMatchTextHash(candidate.matchTextHash());
        embedding.setEmbeddingModel(candidate.model());
        embedding.setDimensions(candidate.dimensions());
        embedding.setVectorJson(candidate.vectorJson());
        embedding.setStatus(activate ? 1 : 0);
        embedding.setGeneratedAt(new Date());
        embeddingMapper.insert(embedding);
    }

    private GeneratedUserProfile mergeDimension(AiUserProfileEntity profile, ProfileType type, String value) {
        GeneratedUserProfile generated = new GeneratedUserProfile(
                safeText(profile == null ? null : profile.getActivityPreferenceText()),
                safeText(profile == null ? null : profile.getSocialPersonalityText()),
                safeText(profile == null ? null : profile.getPartnerPreferenceText()),
                safeText(profile == null ? null : profile.getActivityConstraintHabitText()),
                safeText(profile == null ? null : profile.getAiInteractionPreferenceText()));
        switch (type) {
            case ACTIVITY_PREFERENCE -> generated.setInterestAndActivityPreference(value);
            case SOCIAL_PERSONALITY -> generated.setSocialAndPersonalityTendency(value);
            case PARTNER_PREFERENCE -> generated.setPartnerMatchingPreference(value);
            case ACTIVITY_CONSTRAINT_HABIT -> generated.setActivityConstraintsAndHabits(value);
            case AI_INTERACTION_PREFERENCE -> generated.setAiInteractionPreference(value);
        }
        return generated;
    }

    private String dimensionText(AiUserProfileEntity profile, ProfileType type) {
        if (profile == null) return UNKNOWN;
        return switch (type) {
            case ACTIVITY_PREFERENCE -> safeText(profile.getActivityPreferenceText());
            case SOCIAL_PERSONALITY -> safeText(profile.getSocialPersonalityText());
            case PARTNER_PREFERENCE -> safeText(profile.getPartnerPreferenceText());
            case ACTIVITY_CONSTRAINT_HABIT -> safeText(profile.getActivityConstraintHabitText());
            case AI_INTERACTION_PREFERENCE -> safeText(profile.getAiInteractionPreferenceText());
        };
    }

    private String safeText(String value) { return StringUtils.defaultIfBlank(value, UNKNOWN); }

    private AiUserProfileEntity activeOrRebuildProfile(long userId) {
        return profileMapper.selectOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", userId).last("limit 1"));
    }

    private int profileVersion(AiUserProfileEntity profile) {
        return profile == null || profile.getProfileVersion() == null ? 0 : profile.getProfileVersion();
    }

    private AiEpisodeExtractionTask requiredProcessingExtractionTask(long taskId) {
        AiEpisodeExtractionTask task = extractionTaskMapper.selectById(taskId);
        if (task == null || !MemoryTaskStatus.PROCESSING.name().equals(task.getStatus())) throw new StaleTaskException();
        return task;
    }

    private AiProfileUpdateTask requiredProcessingProfileTask(long taskId) {
        AiProfileUpdateTask task = profileTaskMapper.selectById(taskId);
        if (task == null || !MemoryTaskStatus.PROCESSING.name().equals(task.getStatus())) throw new StaleTaskException();
        return task;
    }

    private List<AiChatMessage> sourceMessages(AiEpisodeExtractionTask task) {
        if (!MemorySourceType.CHAT_MESSAGE.name().equals(task.getSourceType())) return List.of();
        List<AiChatMessage> messages = chatMessageService.listClosedMessages(task.getChatSessionId(),
                task.getFromMessageIdExclusive(), task.getToMessageIdInclusive(), properties.getEpisode().getMessageBatchSize());
        if (messages.isEmpty() || !task.getToMessageIdInclusive().equals(messages.getLast().getId())) {
            throw new StaleTaskException();
        }
        return messages;
    }

    private List<AiUserEpisode> correctionCandidates(long userId) {
        return episodeMapper.selectList(new QueryWrapper<AiUserEpisode>()
                .eq("userId", userId)
                .in("status", EpisodeStatus.PENDING.name(), EpisodeStatus.CONSOLIDATED.name())
                .orderByDesc("observedAt").orderByDesc("id").last("limit 30"));
    }

    private String buildExtractionSource(AiEpisodeExtractionTask task,
                                         List<AiChatMessage> messages,
                                         List<AiUserEpisode> correctionCandidates) {
        StringBuilder builder = new StringBuilder();
        if (MemorySourceType.SELF_INTRODUCTION.name().equals(task.getSourceType())) {
            builder.append("来源：用户自我介绍\n").append(StringUtils.defaultString(task.getSourceText()));
        } else {
            builder.append("来源：聊天记录\n");
            for (AiChatMessage message : messages) {
                builder.append("[id=").append(message.getId()).append("][role=").append(message.getRole()).append("] ")
                        .append(StringUtils.defaultString(message.getContent())).append('\n');
            }
        }
        if (!correctionCandidates.isEmpty()) {
            builder.append("\n可纠正的历史证据（仅供明确纠正时引用）：");
            for (AiUserEpisode episode : correctionCandidates) {
                builder.append("\n[episodeId=").append(episode.getId())
                        .append("][profileType=").append(episode.getProfileType()).append("] ")
                        .append(episode.getContent());
            }
        }
        return builder.toString();
    }

    private List<AiUserEpisode> saveEpisodes(AiEpisodeExtractionTask task,
                                             List<AiChatMessage> messages,
                                             List<AiUserEpisode> correctionCandidates,
                                             GeneratedEpisodeExtraction extraction) {
        Set<Long> userMessageIds = messages.stream().filter(message -> "user".equals(message.getRole()))
                .map(AiChatMessage::getId).collect(Collectors.toSet());
        Map<Long, AiUserEpisode> correctionCandidatesById = correctionCandidates.stream()
                .collect(Collectors.toMap(AiUserEpisode::getId, episode -> episode, (left, right) -> left));
        List<AiUserEpisode> saved = new ArrayList<>();
        List<GeneratedEpisode> generated = extraction == null || extraction.getEpisodes() == null
                ? List.of() : extraction.getEpisodes();
        for (GeneratedEpisode candidate : generated) {
            validateEpisode(candidate, userMessageIds, correctionCandidatesById, task.getSourceType());
            AiUserEpisode episode = new AiUserEpisode();
            episode.setUserId(task.getUserId());
            episode.setProfileType(candidate.getProfileType());
            episode.setContent(candidate.getContent().trim());
            episode.setSourceType(task.getSourceType());
            episode.setSourceSessionId(task.getChatSessionId());
            episode.setSourceMessageIds(writeJson(candidate.getSourceMessageIds()));
            episode.setSourceReferenceId(task.getSourceReferenceId());
            episode.setSignalType(candidate.getSignalType());
            episode.setPriority(candidate.getPriority());
            episode.setEvidenceGroupKey(candidate.getSourceMessageIds() == null || candidate.getSourceMessageIds().isEmpty()
                    ? StringUtils.defaultIfBlank(task.getSourceReferenceId(), "task:" + task.getId())
                    : "message:" + candidate.getSourceMessageIds().getFirst());
            episode.setDedupeHash(textHashService.sha256(candidate.getProfileType() + "|" + candidate.getContent().trim()));
            episode.setExtractionTaskId(task.getId());
            episode.setSupersededEpisodeIds(writeJson(
                    candidate.getSupersededEpisodeIds() == null ? List.of() : candidate.getSupersededEpisodeIds()));
            episode.setStatus(EpisodeStatus.PENDING.name());
            episode.setObservedAt(observedAt(messages));
            try { episodeMapper.insert(episode); saved.add(episode); } catch (DuplicateKeyException ignored) { }
        }
        return saved;
    }

    private Date observedAt(List<AiChatMessage> messages) {
        return messages.stream().filter(message -> "user".equals(message.getRole()))
                .map(AiChatMessage::getCreateTime).filter(java.util.Objects::nonNull).max(Date::compareTo).orElseGet(Date::new);
    }

    private void validateEpisode(GeneratedEpisode episode,
                                 Set<Long> userMessageIds,
                                 Map<Long, AiUserEpisode> correctionCandidates,
                                 String sourceType) {
        if (episode == null || StringUtils.isBlank(episode.getContent()) || episode.getContent().trim().length() > 160) {
            throw new IllegalArgumentException("episode content is invalid");
        }
        if (episode.getContent().matches("(?s).*(1[3-9]\\d{9}|[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}|(?i:token|api[_-]?key|password|密码)\\s*[:：=]).*")) {
            throw new IllegalArgumentException("episode content contains sensitive data");
        }
        enumValue(ProfileType.class, episode.getProfileType());
        enumValue(EpisodeSignalType.class, episode.getSignalType());
        enumValue(EpisodePriority.class, episode.getPriority());
        List<Long> ids = episode.getSourceMessageIds() == null ? List.of() : episode.getSourceMessageIds();
        if (MemorySourceType.CHAT_MESSAGE.name().equals(sourceType) && (ids.isEmpty() || !userMessageIds.containsAll(ids))) {
            throw new IllegalArgumentException("episode source messages are not valid user messages");
        }
        if (MemorySourceType.SELF_INTRODUCTION.name().equals(sourceType) && !ids.isEmpty()) {
            throw new IllegalArgumentException("self introduction episode must not reference message ids");
        }
        List<Long> supersededIds = episode.getSupersededEpisodeIds() == null
                ? List.of() : episode.getSupersededEpisodeIds().stream().distinct().toList();
        if (!supersededIds.isEmpty() && !EpisodeSignalType.CORRECTION.name().equals(episode.getSignalType())) {
            throw new IllegalArgumentException("only correction episode may supersede old evidence");
        }
        for (Long supersededId : supersededIds) {
            AiUserEpisode old = correctionCandidates.get(supersededId);
            if (old == null || !episode.getProfileType().equals(old.getProfileType())) {
                throw new IllegalArgumentException("superseded episode is outside valid correction candidates");
            }
        }
    }

    private List<Long> readIds(String json) {
        if (StringUtils.isBlank(json)) return List.of();
        try {
            Long[] ids = objectMapper.readValue(json, Long[].class);
            return ids == null ? List.of() : List.of(ids);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("episode superseded ids are invalid", e);
        }
    }

    private <T extends Enum<T>> void enumValue(Class<T> type, String value) {
        try { Enum.valueOf(type, value); } catch (Exception e) { throw new IllegalArgumentException("episode enum is invalid", e); }
    }

    private boolean claimExtractionTask(long id) {
        return extractionTaskMapper.update(null, new UpdateWrapper<AiEpisodeExtractionTask>()
                .set("status", MemoryTaskStatus.PROCESSING.name()).set("updateTime", new Date()).eq("id", id)
                .eq("status", MemoryTaskStatus.PENDING.name())) == 1;
    }

    private boolean claimProfileTask(long id) {
        return profileTaskMapper.update(null, new UpdateWrapper<AiProfileUpdateTask>()
                .set("status", MemoryTaskStatus.PROCESSING.name()).set("updateTime", new Date()).eq("id", id)
                .eq("status", MemoryTaskStatus.PENDING.name())) == 1;
    }

    private void recoverTimedOutExtractionTasks() {
        Date cutoff = Date.from(new Date().toInstant().minus(properties.getEpisode().getProcessingTimeoutMinutes(), ChronoUnit.MINUTES));
        extractionTaskMapper.update(null, new UpdateWrapper<AiEpisodeExtractionTask>()
                .set("status", MemoryTaskStatus.PENDING.name()).eq("status", MemoryTaskStatus.PROCESSING.name())
                .lt("updateTime", cutoff));
    }

    private void recoverTimedOutProfileTasks() {
        Date cutoff = Date.from(new Date().toInstant().minus(properties.getProfileUpdate().getProcessingTimeoutMinutes(), ChronoUnit.MINUTES));
        profileTaskMapper.update(null, new UpdateWrapper<AiProfileUpdateTask>()
                .set("status", MemoryTaskStatus.PENDING.name()).eq("status", MemoryTaskStatus.PROCESSING.name())
                .lt("updateTime", cutoff));
    }

    private void retryClaimedExtractionTask(long taskId, RuntimeException error) {
        AiEpisodeExtractionTask task = extractionTaskMapper.selectById(taskId);
        if (task == null || !MemoryTaskStatus.PROCESSING.name().equals(task.getStatus())) return;
        int retries = safeRetry(task.getRetryCount()) + 1;
        extractionTaskMapper.update(null, new UpdateWrapper<AiEpisodeExtractionTask>()
                .set("retryCount", retries).set("lastError", errorText(error))
                .set("status", retries >= properties.getEpisode().getMaxRetries()
                        ? MemoryTaskStatus.FAILED.name() : MemoryTaskStatus.PENDING.name())
                .set("nextRetryAt", retryAt(retries)).eq("id", taskId).eq("status", MemoryTaskStatus.PROCESSING.name()));
        log.warn("AI episode extraction failed, taskId={}, errorType={}", taskId, error.getClass().getSimpleName());
    }

    private void retryClaimedProfileTask(long taskId, RuntimeException error) {
        AiProfileUpdateTask task = profileTaskMapper.selectById(taskId);
        if (task == null || !MemoryTaskStatus.PROCESSING.name().equals(task.getStatus())) return;
        int retries = safeRetry(task.getRetryCount()) + 1;
        profileTaskMapper.update(null, new UpdateWrapper<AiProfileUpdateTask>()
                .set("retryCount", retries).set("lastError", errorText(error))
                .set("status", retries >= properties.getProfileUpdate().getMaxRetries()
                        ? MemoryTaskStatus.FAILED.name() : MemoryTaskStatus.PENDING.name())
                .set("nextRetryAt", retryAt(retries)).eq("id", taskId).eq("status", MemoryTaskStatus.PROCESSING.name()));
        log.warn("AI profile update failed, taskId={}, errorType={}", taskId, error.getClass().getSimpleName());
    }

    private void supersedeExtractionTask(long taskId) {
        extractionTaskMapper.update(null, new UpdateWrapper<AiEpisodeExtractionTask>()
                .set("status", MemoryTaskStatus.SUPERSEDED.name()).eq("id", taskId)
                .eq("status", MemoryTaskStatus.PROCESSING.name()));
    }

    private void supersedeProfileTask(long taskId) {
        profileTaskMapper.update(null, new UpdateWrapper<AiProfileUpdateTask>()
                .set("status", MemoryTaskStatus.SUPERSEDED.name()).eq("id", taskId)
                .eq("status", MemoryTaskStatus.PROCESSING.name()));
    }

    private Date retryAt(int retries) {
        return Date.from(new Date().toInstant().plus(Math.min(retries * 5L, 60L), ChronoUnit.MINUTES));
    }

    private int safeRetry(Integer value) { return value == null ? 0 : value; }
    private String errorText(RuntimeException error) {
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(error.getMessage(), "unknown failure"), 1024);
    }
    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("serialize memory payload failed", e); }
    }

    private record PreparedExtraction(long taskId, List<AiChatMessage> messages,
                                      List<AiUserEpisode> correctionCandidates,
                                      GeneratedEpisodeExtraction extraction) { }
    private record ExtractionCommitResult(long userId, Long chatSessionId, boolean selfIntroduction,
                                          Map<ProfileType, Boolean> triggers) { }
    private record PreparedEmbedding(String matchTextHash, String model, int dimensions, String vectorJson) { }
    private record PreparedProfile(long taskId, long userId, ProfileType profileType, int expectedVersion,
                                    String oldDimension, String newDimension, GeneratedUserProfile generated,
                                    String fullText, String matchText, String interactionText,
                                    List<Long> pendingEpisodeIds, List<Long> evidenceEpisodeIds,
                                    List<Long> supersededEpisodeIds,
                                    PreparedEmbedding embedding) { }
    private static final class StaleTaskException extends RuntimeException { }
}
