package com.mikle.syncup.ai;

import com.mikle.syncup.ai.exception.InvalidToolArgumentsException;
import com.mikle.syncup.ai.mapper.AiChatMemoryMapper;
import com.mikle.syncup.ai.memory.PersistentChatMemoryStore;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@SpringBootTest(properties = "sync-up.ai.agent.enabled=false")
class AiChatMemoryStoreTest {

    @Resource
    private PersistentChatMemoryStore persistentChatMemoryStore;

    @Resource
    private AiChatMemoryMapper aiChatMemoryMapper;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureAiChatMemoryTable() {
        jdbcTemplate.execute("""
                create table if not exists ai_chat_memory
                (
                    id           bigint auto_increment comment 'id' primary key,
                    memoryId     varchar(160) not null comment '会话记忆 id，userId:sessionId',
                    userId       bigint not null comment '用户 id',
                    sessionId    varchar(64) not null comment 'AI 对话会话 id',
                    messagesJson mediumtext not null comment 'LangChain4j ChatMessage JSON',
                    messageCount int default 0 not null comment '消息数量',
                    expireAt     datetime not null comment '过期时间',
                    createTime   datetime default CURRENT_TIMESTAMP null comment '创建时间',
                    updateTime   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
                    isDelete     tinyint default 0 not null comment '是否删除'
                ) comment 'AI 短期会话记忆'
                """);
        addIndexIfMissing("uk_ai_chat_memory_memoryId",
                "alter table ai_chat_memory add unique index uk_ai_chat_memory_memoryId (memoryId)");
        addIndexIfMissing("idx_ai_chat_memory_expireAt",
                "alter table ai_chat_memory add index idx_ai_chat_memory_expireAt (expireAt)");
    }

    @Test
    void chatMemoryStore_shouldPersistAndReadMessages() {
        String memoryId = buildMemoryId();
        try {
            persistentChatMemoryStore.updateMessages(memoryId, List.of(
                    UserMessage.from("我的邮箱是 test@example.com，想找西安羽毛球队伍"),
                    AiMessage.from("已记录你的需求")
            ));

            List<ChatMessage> messages = persistentChatMemoryStore.getMessages(memoryId);

            Assertions.assertEquals(2, messages.size());
            Assertions.assertTrue(messages.get(0) instanceof UserMessage);
            Long count = jdbcTemplate.queryForObject(
                    "select count(1) from ai_chat_memory where memoryId = ?",
                    Long.class,
                    memoryId);
            Assertions.assertEquals(1L, count);
            String messagesJson = jdbcTemplate.queryForObject(
                    "select messagesJson from ai_chat_memory where memoryId = ?",
                    String.class,
                    memoryId);
            Assertions.assertNotNull(messagesJson);
            Assertions.assertFalse(messagesJson.contains("test@example.com"));
        } finally {
            persistentChatMemoryStore.deleteMessages(memoryId);
        }
    }

    @Test
    void deleteExpiredPhysically_shouldRemoveExpiredRows() {
        String memoryId = buildMemoryId();
        try {
            jdbcTemplate.update("""
                            insert into ai_chat_memory(memoryId, userId, sessionId, messagesJson, messageCount, expireAt)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                    memoryId,
                    10001L,
                    "expired-session",
                    "[]",
                    0,
                    new Date(System.currentTimeMillis() - 60_000));

            int deleted = aiChatMemoryMapper.deleteExpiredPhysically(new Date());

            Assertions.assertTrue(deleted >= 1);
            Long count = jdbcTemplate.queryForObject(
                    "select count(1) from ai_chat_memory where memoryId = ?",
                    Long.class,
                    memoryId);
            Assertions.assertEquals(0L, count);
        } finally {
            persistentChatMemoryStore.deleteMessages(memoryId);
        }
    }

    @Test
    void updateMessages_withInvalidToolArguments_shouldRejectAndClearMemory() {
        String memoryId = buildMemoryId();
        try {
            AiMessage invalidToolCall = buildInvalidToolCallMessage();

            Assertions.assertThrows(
                    InvalidToolArgumentsException.class,
                    () -> persistentChatMemoryStore.updateMessages(memoryId, List.of(invalidToolCall))
            );

            Assertions.assertEquals(0L, countMemoryRows(memoryId));
            Assertions.assertTrue(persistentChatMemoryStore.getMessages(memoryId).isEmpty());
        } finally {
            persistentChatMemoryStore.deleteMessages(memoryId);
        }
    }

    @Test
    void getMessages_withCorruptedStoredToolArguments_shouldSelfHeal() {
        String memoryId = buildMemoryId();
        try {
            String messagesJson = ChatMessageSerializer.messagesToJson(List.of(buildInvalidToolCallMessage()));
            jdbcTemplate.update("""
                            insert into ai_chat_memory(memoryId, userId, sessionId, messagesJson, messageCount, expireAt)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                    memoryId,
                    10001L,
                    memoryId.substring(memoryId.indexOf(':') + 1),
                    messagesJson,
                    1,
                    new Date(System.currentTimeMillis() + 60_000));

            List<ChatMessage> messages = persistentChatMemoryStore.getMessages(memoryId);

            Assertions.assertTrue(messages.isEmpty());
            Assertions.assertEquals(0L, countMemoryRows(memoryId));
        } finally {
            persistentChatMemoryStore.deleteMessages(memoryId);
        }
    }

    private AiMessage buildInvalidToolCallMessage() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("invalid-tool-call")
                .name("createTeamDraft")
                .arguments("{\"activityCategory\":1,\"budgetMax\":}")
                .build();
        return AiMessage.from(List.of(request));
    }

    private long countMemoryRows(String memoryId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(1) from ai_chat_memory where memoryId = ?",
                Long.class,
                memoryId
        );
        return count == null ? 0 : count;
    }

    private void addIndexIfMissing(String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(1)
                        from information_schema.statistics
                        where table_schema = database()
                          and table_name = 'ai_chat_memory'
                          and index_name = ?
                        """,
                Integer.class,
                indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private String buildMemoryId() {
        return "10001:" + UUID.randomUUID();
    }
}
