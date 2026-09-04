package com.mikle.syncup.ai;

import com.mikle.syncup.ai.mapper.AiChatMessageMapper;
import com.mikle.syncup.ai.mapper.AiChatSessionMapper;
import com.mikle.syncup.ai.model.entity.AiChatMessage;
import com.mikle.syncup.ai.model.entity.AiChatSession;
import com.mikle.syncup.ai.model.vo.AiChatHistoryVO;
import com.mikle.syncup.ai.model.vo.AiChatResponseVO;
import com.mikle.syncup.ai.model.vo.AiUiBlockVO;
import com.mikle.syncup.ai.service.AiChatMessageService;
import com.mikle.syncup.ai.service.AiChatSessionService;
import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;
import com.mikle.syncup.mapper.UserMapper;
import com.mikle.syncup.model.domain.User;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AiChatMessageServiceTest {

    @Resource
    private AiChatMessageService messageService;

    @Resource
    private AiChatSessionService sessionService;

    @Resource
    private AiChatMessageMapper messageMapper;

    @Resource
    private AiChatSessionMapper sessionMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private DataSource dataSource;

    @BeforeEach
    void ensureUsingTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            Assertions.assertEquals(
                    "sync_up_test",
                    connection.getCatalog(),
                    "当前连接的不是 sync_up_test，已停止测试，避免污染开发数据库"
            );
        }
    }

    @Test
    void saveUserMessage_sessionBelongsToAnotherUser_shouldRejectWithoutSaving() {
        User owner = createUser();
        User anotherUser = createUser();
        AiChatSession session = sessionService.getOrCreate(owner.getId(), unique("session"));

        BusinessException exception = Assertions.assertThrows(
                BusinessException.class,
                () -> messageService.saveUserMessage(anotherUser, session, "不能写入别人的会话")
        );

        Assertions.assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        Assertions.assertEquals(0L, countMessages(session.getId()));
    }

    @Test
    void saveUserMessage_sensitiveContent_shouldMaskSecrets() {
        User user = createUser();
        AiChatSession session = sessionService.getOrCreate(user.getId(), unique("session"));
        String content = "password=Secret123 token:abc-token api_key=my-key "
                + "邮箱 tester@example.com，手机号 13812345678";

        AiChatMessage saved = messageService.saveUserMessage(user, session, content);
        AiChatMessage stored = messageMapper.selectById(saved.getId());

        Assertions.assertAll(
                () -> Assertions.assertFalse(stored.getContent().contains("Secret123")),
                () -> Assertions.assertFalse(stored.getContent().contains("abc-token")),
                () -> Assertions.assertFalse(stored.getContent().contains("my-key")),
                () -> Assertions.assertFalse(stored.getContent().contains("tester@example.com")),
                () -> Assertions.assertFalse(stored.getContent().contains("13812345678")),
                () -> Assertions.assertTrue(stored.getContent().contains("password=***")),
                () -> Assertions.assertTrue(stored.getContent().contains("***@***")),
                () -> Assertions.assertTrue(stored.getContent().contains("1**********"))
        );
    }

    @Test
    void saveUserMessage_contentOverLimit_shouldStoreAtMost2048Characters() {
        User user = createUser();
        AiChatSession session = sessionService.getOrCreate(user.getId(), unique("session"));

        AiChatMessage saved = messageService.saveUserMessage(user, session, "x".repeat(3000));
        AiChatMessage stored = messageMapper.selectById(saved.getId());

        Assertions.assertEquals(2048, stored.getContent().length());
    }

    @Test
    void getLatestHistory_shouldReturnOnlyCurrentUsersLatestSession() {
        User currentUser = createUser();
        User otherUser = createUser();
        AiChatSession older = sessionService.getOrCreate(currentUser.getId(), unique("older"));
        AiChatSession latest = sessionService.getOrCreate(currentUser.getId(), unique("latest"));
        AiChatSession other = sessionService.getOrCreate(otherUser.getId(), unique("other"));
        messageService.saveUserMessage(currentUser, older, "旧会话消息");
        messageService.saveUserMessage(currentUser, latest, "当前会话消息");
        messageService.saveUserMessage(otherUser, other, "其他用户消息");
        setUpdateTime(older, new Date(System.currentTimeMillis() - 120_000));
        setUpdateTime(latest, new Date(System.currentTimeMillis() + 120_000));
        setUpdateTime(other, new Date(System.currentTimeMillis() + 240_000));

        AiChatHistoryVO history = messageService.getLatestHistory(currentUser);

        Assertions.assertEquals(latest.getSessionKey(), history.getSessionId());
        Assertions.assertEquals(1, history.getMessages().size());
        Assertions.assertEquals("当前会话消息", history.getMessages().getFirst().getContent());
    }

    @Test
    void getLatestHistory_assistantResponse_shouldRestoreStructuredResponse() {
        User user = createUser();
        AiChatSession session = sessionService.getOrCreate(user.getId(), unique("session"));
        AiChatResponseVO response = new AiChatResponseVO();
        response.setSessionId(session.getSessionKey());
        response.setReply("找到一个合适的队伍");
        response.setUiBlocks(java.util.List.of(
                AiUiBlockVO.of(AiUiBlockVO.TEAM_LIST, Map.of("count", 1))
        ));
        messageService.saveAssistantMessage(user, session, response.getReply(), response);

        AiChatHistoryVO history = messageService.getLatestHistory(user);

        AiChatResponseVO restored = history.getMessages().getFirst().getResponse();
        Assertions.assertAll(
                () -> Assertions.assertEquals(response.getReply(), restored.getReply()),
                () -> Assertions.assertEquals(1, restored.getUiBlocks().size()),
                () -> Assertions.assertEquals(AiUiBlockVO.TEAM_LIST, restored.getUiBlocks().getFirst().getType())
        );
    }

    @Test
    void getLatestHistory_hiddenTeamEvent_shouldRestoreEventMetadata() {
        User user = createUser();
        AiChatSession session = sessionService.getOrCreate(user.getId(), unique("session"));
        messageService.saveTeamDraftConfirmedEvent(user, session, "draft-123", 9001L);

        AiChatHistoryVO history = messageService.getLatestHistory(user);

        Assertions.assertEquals(1, history.getMessages().size());
        Assertions.assertAll(
                () -> Assertions.assertEquals(0, history.getMessages().getFirst().getVisible()),
                () -> Assertions.assertEquals("TEAM_CREATED", history.getMessages().getFirst().getEventType()),
                () -> Assertions.assertEquals("draft-123", history.getMessages().getFirst().getRelatedDraftId()),
                () -> Assertions.assertEquals(9001L, history.getMessages().getFirst().getRelatedTeamId())
        );
    }

    @Test
    void getLatestHistory_userHasNoSession_shouldReturnEmptyHistory() {
        User user = createUser();

        AiChatHistoryVO history = messageService.getLatestHistory(user);

        Assertions.assertNull(history.getSessionId());
        Assertions.assertTrue(history.getMessages().isEmpty());
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        User user = new User();
        user.setUsername("message_test");
        user.setUserAccount("message_" + suffix);
        user.setUserPassword("Password123");
        user.setUserRole(0);
        user.setUserStatus(0);
        Assertions.assertEquals(1, userMapper.insert(user));
        return user;
    }

    private long countMessages(long sessionId) {
        return messageMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getChatSessionId, sessionId)
        );
    }

    private void setUpdateTime(AiChatSession session, Date updateTime) {
        session.setUpdateTime(updateTime);
        Assertions.assertEquals(1, sessionMapper.updateById(session));
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
