package com.mikle.syncup.ai.agent.impl;

import com.mikle.syncup.ai.agent.AiAgentToolContext;
import com.mikle.syncup.ai.agent.AiAssistantAgentService;
import com.mikle.syncup.ai.agent.AiAssistantTools;
import com.mikle.syncup.ai.config.AiAgentProperties;
import com.mikle.syncup.ai.memory.PersistentChatMemoryStore;
import com.mikle.syncup.ai.model.AiChatResponse;
import com.mikle.syncup.ai.model.AiToolResult;
import com.mikle.syncup.ai.model.TeamIntent;
import com.mikle.syncup.model.domain.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LangChain4jAiAssistantAgentServiceImpl implements AiAssistantAgentService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String SYSTEM_PROMPT = """
            你是 Sync Up 的组队助手。你可以根据用户需求选择后端工具，但必须遵守：
            1. 查询队伍、推荐搭子、查看个人公开资料、查看我创建/加入的队伍可以直接调用工具。
            2. 用户明确说“这是我的自我介绍/帮我更新资料/根据这段更新我的信息”时，可以调用 updateMyProfile；该工具只能更新当前用户的自我介绍和结构化画像。
            3. 用户明确要求加入或退出某个队伍，且能确定 teamId 时，可以调用 joinTeam 或 quitTeam；如果缺少 teamId 或加密队伍密码，先追问。
            4. 创建队伍只能调用 createTeamDraft 生成草稿，不能承诺已经正式创建。
            5. 不能要求或推断用户 id，当前用户身份由后端提供。
            6. 不要输出账号、手机号、邮箱、密码、登录 token 或 API key。
            7. 创建或查询需求中已经包含活动、城市和人数时，优先调用工具，不要因为表达方式不同而追问。
            8. 你负责理解语义并填写工具参数，例如“踢足球”填 activityType=足球，“无需支付/免费”填 budgetMax=0，“持续3小时”填 durationMinutes=180。
            9. 如果用户提供相对时间，结合用户消息里的当前时间上下文转换为 yyyy-MM-dd HH:mm:ss。
            10. 如果信息不足，直接用中文追问缺少的信息。
            11. 回复要简洁，并说明已调用的工具结果。
            """;

    @Resource
    private AiAgentProperties aiAgentProperties;

    @Resource
    private AiAssistantTools aiAssistantTools;

    @Resource
    private AiAgentToolContext aiAgentToolContext;

    @Resource
    private PersistentChatMemoryStore persistentChatMemoryStore;

    @Override
    public Optional<AiChatResponse> chat(String message, String sessionId, User loginUser) {
        if (!aiAgentProperties.available()) {
            return Optional.empty();
        }
        if (StringUtils.isBlank(message) || message.length() > aiAgentProperties.getMaxInputLength()) {
            return Optional.empty();
        }
        aiAgentToolContext.start(sessionId, loginUser);
        try {
            Assistant assistant = buildAssistant();
            String reply = assistant.chat(buildMemoryId(loginUser, sessionId), buildModelMessage(message));
            AiAgentToolContext.State state = aiAgentToolContext.snapshot();

            AiChatResponse response = new AiChatResponse();
            response.setSessionId(sessionId);
            response.setReply(normalizeAgentReply(reply, state.getToolResults()));
            response.setDraft(state.getDraft());
            response.getToolResults().addAll(state.getToolResults());
            response.setIntent(buildResponseIntent(message, state));
            return Optional.of(response);
        } catch (RuntimeException e) {
            log.warn("AI agent failed, fallback to deterministic flow. provider={}, model={}, error={}",
                    aiAgentProperties.getProvider(), aiAgentProperties.getModel(), e.getMessage());
            return Optional.empty();
        } finally {
            aiAgentToolContext.clear();
        }
    }

    private String buildMemoryId(User loginUser, String sessionId) {
        return loginUser.getId() + ":" + sessionId;
    }

    private String buildModelMessage(String message) {
        return "当前服务端时间：" + LocalDateTime.now().format(DATE_TIME_FORMATTER)
                + "\n用户原始需求：" + message;
    }

    private TeamIntent buildResponseIntent(String message, AiAgentToolContext.State state) {
        TeamIntent intent = state.getIntent();
        if (intent == null) {
            intent = new TeamIntent();
        }
        intent.setSourceText(message);
        intent.setTeamRelated(intent.isTeamRelated() || !state.getToolResults().isEmpty());
        return intent;
    }

    private String normalizeAgentReply(String reply, List<AiToolResult> toolResults) {
        if (hasToolResult(toolResults, "getMyProfile")) {
            return "这是你的个人资料。";
        }
        if (hasToolResult(toolResults, "updateMyProfile")) {
            return "我已经帮你更新个人资料。";
        }
        if (hasToolResult(toolResults, "listMyJoinedTeams")) {
            return "这是你加入的队伍。";
        }
        if (hasToolResult(toolResults, "listMyCreatedTeams")) {
            return "这是你创建的队伍。";
        }
        if (hasToolResult(toolResults, "joinTeam")) {
            return "已帮你加入队伍。";
        }
        if (hasToolResult(toolResults, "quitTeam")) {
            return "已帮你退出队伍。";
        }
        return StringUtils.defaultIfBlank(cleanMarkdown(reply), "我已经处理了你的请求。");
    }

    private boolean hasToolResult(List<AiToolResult> toolResults, String toolName) {
        return toolResults.stream().anyMatch(result -> toolName.equals(result.getToolName()));
    }

    private String cleanMarkdown(String reply) {
        if (StringUtils.isBlank(reply)) {
            return reply;
        }
        return reply.replace("**", "")
                .replace("*", "")
                .trim();
    }

    private Assistant buildAssistant() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(aiAgentProperties.getBaseUrl())
                .apiKey(aiAgentProperties.getApiKey())
                .modelName(aiAgentProperties.getModel())
                .temperature(aiAgentProperties.getTemperature())
                .timeout(Duration.ofMillis(aiAgentProperties.getTimeoutMs()))
                .maxRetries(1)
                .build();
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .tools(aiAssistantTools)
                .maxSequentialToolsInvocations(Math.max(1, aiAgentProperties.getMaxToolCalls()));
        if (aiAgentProperties.getMemory().isEnabled()) {
            builder.chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(Math.max(2, aiAgentProperties.getMemory().getMaxMessages()))
                    .chatMemoryStore(persistentChatMemoryStore)
                    .build());
        }
        return builder.build();
    }

    interface Assistant {

        @SystemMessage(SYSTEM_PROMPT)
        String chat(@MemoryId String memoryId, @UserMessage String message);
    }
}
