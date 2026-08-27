package com.mikle.syncup.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiProfileUpdateTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserEpisodeMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.entity.AiProfileUpdateTask;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.enums.EpisodeStatus;
import com.mikle.syncup.ai.model.enums.MemoryTaskStatus;
import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;
import com.mikle.syncup.ai.service.AiProfileUpdateTaskService;
import com.mikle.syncup.ai.service.TextHashService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiProfileUpdateTaskServiceImpl implements AiProfileUpdateTaskService {

    @Resource private AiProfileUpdateTaskMapper taskMapper;
    @Resource private AiUserEpisodeMapper episodeMapper;
    @Resource private AiUserProfileMapper profileMapper;
    @Resource private AiMemoryProperties properties;
    @Resource private TextHashService textHashService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enqueueIfNecessary(long userId, ProfileType profileType,
                                   ProfileUpdateTriggerType triggerType, boolean force) {
        if (userId <= 0 || profileType == null || triggerType == null) return;
        long active = taskMapper.selectCount(new QueryWrapper<AiProfileUpdateTask>()
                .eq("userId", userId).eq("profileType", profileType.name())
                .in("status", MemoryTaskStatus.PENDING.name(), MemoryTaskStatus.PROCESSING.name()));
        if (active > 0) return;

        List<AiUserEpisode> pending = episodeMapper.selectList(new QueryWrapper<AiUserEpisode>()
                .eq("userId", userId).eq("profileType", profileType.name())
                .eq("status", EpisodeStatus.PENDING.name()).orderByAsc("id").last("limit 500"));
        long evidenceGroups = pending.stream().map(AiUserEpisode::getEvidenceGroupKey).distinct().count();
        if (!force && evidenceGroups < properties.getProfileUpdate().getDefaultEvidenceThreshold()) return;

        AiUserProfileEntity profile = profileMapper.selectOne(new QueryWrapper<AiUserProfileEntity>()
                .eq("userId", userId).last("limit 1"));
        int expectedVersion = profile == null || profile.getProfileVersion() == null ? 0 : profile.getProfileVersion();
        String evidenceSnapshot = pending.stream()
                .map(episode -> episode.getId() + ":" + episode.getDedupeHash())
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
        String snapshot = "version:" + expectedVersion + "|trigger:" + triggerType.name()
                + "|evidence:" + evidenceSnapshot;
        AiProfileUpdateTask task = new AiProfileUpdateTask();
        task.setUserId(userId);
        task.setProfileType(profileType.name());
        task.setTriggerType(triggerType.name());
        task.setTargetEvidenceDigest(textHashService.sha256(snapshot));
        task.setExpectedProfileVersion(expectedVersion);
        task.setStatus(MemoryTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setNextRetryAt(new Date());
        try {
            taskMapper.insert(task);
        } catch (DuplicateKeyException ignored) {
            taskMapper.update(null, new UpdateWrapper<AiProfileUpdateTask>()
                    .set("status", MemoryTaskStatus.PENDING.name())
                    .set("retryCount", 0).set("lastError", null).set("nextRetryAt", new Date())
                    .set("expectedProfileVersion", expectedVersion).set("triggerType", triggerType.name())
                    .eq("userId", userId).eq("profileType", profileType.name())
                    .eq("targetEvidenceDigest", task.getTargetEvidenceDigest())
                    .eq("status", MemoryTaskStatus.FAILED.name()));
        }
    }

    @Override
    public void enqueueRebuildAll(long userId, ProfileUpdateTriggerType triggerType) {
        for (ProfileType type : ProfileType.values()) enqueueIfNecessary(userId, type, triggerType, true);
    }

    @Override
    public void enqueueOverdueTasks() {
        Date cutoff = Date.from(new Date().toInstant()
                .minus(properties.getProfileUpdate().getMaxWaitDays(), ChronoUnit.DAYS));
        List<AiUserEpisode> overdue = episodeMapper.selectList(new QueryWrapper<AiUserEpisode>()
                .eq("status", EpisodeStatus.PENDING.name()).le("observedAt", cutoff)
                .orderByAsc("observedAt").last("limit 500"));
        Map<String, AiUserEpisode> groups = new LinkedHashMap<>();
        for (AiUserEpisode episode : overdue) {
            groups.putIfAbsent(episode.getUserId() + ":" + episode.getProfileType(), episode);
        }
        for (AiUserEpisode episode : groups.values()) {
            enqueueIfNecessary(episode.getUserId(), ProfileType.valueOf(episode.getProfileType()),
                    ProfileUpdateTriggerType.TIME, true);
        }
    }

    @Override
    public void supersedeActiveTasks(long userId) {
        taskMapper.update(null, new UpdateWrapper<AiProfileUpdateTask>()
                .set("status", MemoryTaskStatus.SUPERSEDED.name()).eq("userId", userId)
                .in("status", MemoryTaskStatus.PENDING.name(), MemoryTaskStatus.PROCESSING.name()));
    }
}
