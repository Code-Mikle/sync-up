package com.mikle.syncup.ai;

import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiProfileUpdateTaskMapper;
import com.mikle.syncup.ai.mapper.AiUserEpisodeMapper;
import com.mikle.syncup.ai.mapper.AiUserProfileMapper;
import com.mikle.syncup.ai.model.entity.AiProfileUpdateTask;
import com.mikle.syncup.ai.model.entity.AiUserEpisode;
import com.mikle.syncup.ai.model.entity.AiUserProfileEntity;
import com.mikle.syncup.ai.model.enums.ProfileType;
import com.mikle.syncup.ai.model.enums.ProfileUpdateTriggerType;
import com.mikle.syncup.ai.service.TextHashService;
import com.mikle.syncup.ai.service.impl.AiProfileUpdateTaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AiProfileUpdateTaskServiceTest {

    @Mock private AiProfileUpdateTaskMapper taskMapper;
    @Mock private AiUserEpisodeMapper episodeMapper;
    @Mock private AiUserProfileMapper profileMapper;

    private AiProfileUpdateTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        AiMemoryProperties properties = new AiMemoryProperties();
        properties.getProfileUpdate().setDefaultEvidenceThreshold(2);
        service = new AiProfileUpdateTaskServiceImpl();
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "episodeMapper", episodeMapper);
        ReflectionTestUtils.setField(service, "profileMapper", profileMapper);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "textHashService", new TextHashService());
        when(taskMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void threshold_shouldCountDistinctEvidenceGroups() {
        when(episodeMapper.selectList(any())).thenReturn(List.of(
                episode(1L, "message:100"), episode(2L, "message:100")));

        service.enqueueIfNecessary(7L, ProfileType.ACTIVITY_PREFERENCE,
                ProfileUpdateTriggerType.COUNT, false);

        verify(taskMapper, never()).insert(any(AiProfileUpdateTask.class));

        reset(episodeMapper);
        when(episodeMapper.selectList(any())).thenReturn(List.of(
                episode(1L, "message:100"), episode(3L, "message:200")));

        service.enqueueIfNecessary(7L, ProfileType.ACTIVITY_PREFERENCE,
                ProfileUpdateTriggerType.COUNT, false);

        ArgumentCaptor<AiProfileUpdateTask> captor = ArgumentCaptor.forClass(AiProfileUpdateTask.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getExpectedProfileVersion());
    }

    @Test
    void targetDigest_shouldChangeAfterProfileVersionConflict() {
        when(episodeMapper.selectList(any())).thenReturn(List.of(episode(1L, "message:100")));
        AiUserProfileEntity versionOne = new AiUserProfileEntity();
        versionOne.setProfileVersion(1);
        AiUserProfileEntity versionTwo = new AiUserProfileEntity();
        versionTwo.setProfileVersion(2);
        when(profileMapper.selectOne(any())).thenReturn(versionOne, versionTwo);

        service.enqueueIfNecessary(7L, ProfileType.ACTIVITY_PREFERENCE,
                ProfileUpdateTriggerType.REBUILD, true);
        service.enqueueIfNecessary(7L, ProfileType.ACTIVITY_PREFERENCE,
                ProfileUpdateTriggerType.REBUILD, true);

        ArgumentCaptor<AiProfileUpdateTask> captor = ArgumentCaptor.forClass(AiProfileUpdateTask.class);
        verify(taskMapper, times(2)).insert(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).getTargetEvidenceDigest(),
                captor.getAllValues().get(1).getTargetEvidenceDigest());
        assertEquals(2, captor.getAllValues().get(1).getExpectedProfileVersion());
    }

    private AiUserEpisode episode(long id, String group) {
        AiUserEpisode episode = new AiUserEpisode();
        episode.setId(id);
        episode.setEvidenceGroupKey(group);
        episode.setDedupeHash("hash-" + id);
        return episode;
    }
}
