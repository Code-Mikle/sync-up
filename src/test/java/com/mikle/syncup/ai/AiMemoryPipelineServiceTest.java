package com.mikle.syncup.ai;

import com.mikle.syncup.ai.config.AiMemoryProperties;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.mapper.AiEpisodeExtractionTaskMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.entity.AiEpisodeExtractionTask;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.impl.AiMemoryPipelineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiMemoryPipelineServiceTest {

    @Mock private AiChatSessionMapper sessionMapper;
    @Mock private AiEpisodeExtractionTaskMapper extractionTaskMapper;
    @Mock private AiChatMessageService chatMessageService;

    private AiMemoryPipelineServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiMemoryPipelineServiceImpl();
        ReflectionTestUtils.setField(service, "chatSessionMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "extractionTaskMapper", extractionTaskMapper);
        ReflectionTestUtils.setField(service, "chatMessageService", chatMessageService);
        ReflectionTestUtils.setField(service, "memoryProperties", new AiMemoryProperties());
    }

    @Test
    void createNextTask_shouldNotCreateOverlappingTaskWhenActiveTaskExists() {
        when(sessionMapper.selectByIdForUpdate(10L)).thenReturn(session(10L, 7L, 0L, 4L));
        when(extractionTaskMapper.selectCount(any())).thenReturn(1L);

        service.createNextChatExtractionTaskIfNecessary(7L, 10L);

        verify(chatMessageService, never()).listClosedMessages(anyLong(), anyLong(), anyLong(), anyInt());
        verify(extractionTaskMapper, never()).insert(any(AiEpisodeExtractionTask.class));
    }

    @Test
    void createNextTask_shouldUseLastMessageInBoundedBatchAsTargetCursor() {
        when(sessionMapper.selectByIdForUpdate(10L)).thenReturn(session(10L, 7L, 0L, 8L));
        when(extractionTaskMapper.selectCount(any())).thenReturn(0L);
        AiChatMessage first = message(2L);
        AiChatMessage last = message(6L);
        when(chatMessageService.listClosedMessages(10L, 0L, 8L, 100)).thenReturn(List.of(first, last));

        service.createNextChatExtractionTaskIfNecessary(7L, 10L);

        ArgumentCaptor<AiEpisodeExtractionTask> captor = ArgumentCaptor.forClass(AiEpisodeExtractionTask.class);
        verify(extractionTaskMapper).insert(captor.capture());
        assertEquals(0L, captor.getValue().getFromMessageIdExclusive());
        assertEquals(6L, captor.getValue().getToMessageIdInclusive());
    }

    private AiChatSession session(long id, long userId, long extracted, long closed) {
        AiChatSession session = new AiChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setLastEpisodeExtractedMessageId(extracted);
        session.setLastClosedMessageId(closed);
        return session;
    }

    private AiChatMessage message(long id) {
        AiChatMessage message = new AiChatMessage();
        message.setId(id);
        return message;
    }
}
