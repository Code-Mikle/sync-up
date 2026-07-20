package com.mikle.syncup.ai.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.mapper.AiChatMemoryMapper;
import com.mikle.syncup.ai.model.entity.AiChatMemory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private static final String REDIS_KEY_PREFIX = "syncup:ai:chat-memory:";

    private static final Pattern MEMORY_ID_PATTERN = Pattern.compile("^(\\d+):(.+)$");

    @Resource
    private AiChatMemoryMapper aiChatMemoryMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String normalizedMemoryId = normalizeMemoryId(memoryId);
        if (StringUtils.isBlank(normalizedMemoryId)) {
            return Collections.emptyList();
        }
        String cachedJson = getFromRedis(normalizedMemoryId);
        if (StringUtils.isNotBlank(cachedJson)) {
            return parseMessages(cachedJson);
        }

        AiChatMemory memory = aiChatMemoryMapper.selectOne(new QueryWrapper<AiChatMemory>()
                .eq("memoryId", normalizedMemoryId)
                .last("limit 1"));
        if (memory == null || memory.getExpireAt() == null) {
            return Collections.emptyList();
        }
        if (memory.getExpireAt().before(new Date())) {
            deleteMessages(normalizedMemoryId);
            return Collections.emptyList();
        }
        writeToRedis(normalizedMemoryId, memory.getMessagesJson());
        return parseMessages(memory.getMessagesJson());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String normalizedMemoryId = normalizeMemoryId(memoryId);
        if (StringUtils.isBlank(normalizedMemoryId)) {
            return;
        }
        String messagesJson = sanitizeMessagesJson(ChatMessageSerializer.messagesToJson(messages));
        MemoryIdParts parts = parseMemoryId(normalizedMemoryId);
        Date expireAt = new Date(System.currentTimeMillis()
                + Math.max(1, aiAgentProperties.getMemory().getMysqlTtlHours()) * 60 * 60 * 1000L);

        AiChatMemory existing = aiChatMemoryMapper.selectOne(new QueryWrapper<AiChatMemory>()
                .eq("memoryId", normalizedMemoryId)
                .last("limit 1"));
        if (existing == null) {
            AiChatMemory memory = new AiChatMemory();
            memory.setMemoryId(normalizedMemoryId);
            memory.setUserId(parts.userId());
            memory.setSessionId(parts.sessionId());
            memory.setMessagesJson(messagesJson);
            memory.setMessageCount(messages == null ? 0 : messages.size());
            memory.setExpireAt(expireAt);
            try {
                aiChatMemoryMapper.insert(memory);
            } catch (DuplicateKeyException e) {
                updateExisting(normalizedMemoryId, messagesJson, messages, expireAt);
            }
        } else {
            updateExisting(normalizedMemoryId, messagesJson, messages, expireAt);
        }
        writeToRedis(normalizedMemoryId, messagesJson);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String normalizedMemoryId = normalizeMemoryId(memoryId);
        if (StringUtils.isBlank(normalizedMemoryId)) {
            return;
        }
        aiChatMemoryMapper.deleteByMemoryIdPhysically(normalizedMemoryId);
        try {
            redisTemplate.delete(redisKey(normalizedMemoryId));
        } catch (Exception e) {
            log.warn("delete AI chat memory redis failed, memoryId={}", normalizedMemoryId, e);
        }
    }

    private void updateExisting(String memoryId, String messagesJson, List<ChatMessage> messages, Date expireAt) {
        AiChatMemory update = new AiChatMemory();
        update.setMessagesJson(messagesJson);
        update.setMessageCount(messages == null ? 0 : messages.size());
        update.setExpireAt(expireAt);
        aiChatMemoryMapper.update(update, new QueryWrapper<AiChatMemory>().eq("memoryId", memoryId));
    }

    private String getFromRedis(String memoryId) {
        try {
            Object value = redisTemplate.opsForValue().get(redisKey(memoryId));
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            log.warn("read AI chat memory redis failed, memoryId={}", memoryId, e);
            return null;
        }
    }

    private void writeToRedis(String memoryId, String messagesJson) {
        if (StringUtils.isBlank(messagesJson) || aiAgentProperties.getMemory().getRedisTtlHours() <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    redisKey(memoryId),
                    messagesJson,
                    Duration.ofHours(aiAgentProperties.getMemory().getRedisTtlHours())
            );
        } catch (Exception e) {
            log.warn("write AI chat memory redis failed, memoryId={}", memoryId, e);
        }
    }

    private List<ChatMessage> parseMessages(String messagesJson) {
        try {
            return ChatMessageDeserializer.messagesFromJson(messagesJson);
        } catch (Exception e) {
            log.warn("parse AI chat memory failed", e);
            return Collections.emptyList();
        }
    }

    private String normalizeMemoryId(Object memoryId) {
        return memoryId == null ? null : String.valueOf(memoryId).trim();
    }

    private MemoryIdParts parseMemoryId(String memoryId) {
        Matcher matcher = MEMORY_ID_PATTERN.matcher(memoryId);
        if (!matcher.matches()) {
            return new MemoryIdParts(0L, memoryId);
        }
        return new MemoryIdParts(Long.parseLong(matcher.group(1)), matcher.group(2));
    }

    private String sanitizeMessagesJson(String messagesJson) {
        if (StringUtils.isBlank(messagesJson)) {
            return messagesJson;
        }
        return messagesJson
                .replaceAll("(?i)(token|api[_-]?key|password|密码)\\s*[:：=]\\s*[^\\s,，。；;\"\\\\]+", "$1=***")
                .replaceAll("\\b[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}\\b", "***@***")
                .replaceAll("1[3-9]\\d{9}", "1**********");
    }

    private String redisKey(String memoryId) {
        return REDIS_KEY_PREFIX + memoryId;
    }

    private record MemoryIdParts(Long userId, String sessionId) {
    }
}
