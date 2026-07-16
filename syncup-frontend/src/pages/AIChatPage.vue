<template>
  <div class="ai-chat-page">
    <section class="ai-hero">
      <div class="ai-hero__copy">
        <span>AI 智能匹配</span>
        <h1>直接告诉我你想找什么</h1>
        <p>我会先整理需求，再把可操作的结果放进对话里。</p>
      </div>
      <div class="ai-hero__bot">
        <van-icon name="service-o" size="36" />
      </div>
    </section>

    <section class="chat-stream" ref="streamRef">
      <article
          v-for="message in messages"
          :key="message.id"
          class="chat-message"
          :class="`chat-message--${message.role}`"
      >
        <div class="chat-message__avatar" v-if="message.role === 'assistant'">
          <van-icon name="service-o" size="20" />
        </div>
        <div class="chat-message__content">
          <div class="chat-message__bubble">
            {{ message.content }}
            <span>{{ message.time }}</span>
          </div>

          <template v-if="message.response">
            <article class="intent-card" v-if="message.response.intent?.teamRelated">
              <header>
                <van-icon name="description-o" size="18" />
                <h2>需求识别</h2>
              </header>
              <div class="intent-card__grid">
                <span>活动：{{ message.response.intent.activityType || "待补充" }}</span>
                <span>城市：{{ message.response.intent.city || "待补充" }}</span>
                <span>人数：{{ formatCount(message.response.intent.memberCount) }}</span>
                <span>预算：{{ formatBudget(message.response.intent.budgetMax) }}</span>
                <span>时间：{{ formatDate(message.response.intent.startTime) }}</span>
                <span>水平：{{ message.response.intent.skillLevel || "不限" }}</span>
              </div>
              <div class="intent-card__missing" v-if="message.response.intent.missingFields?.length">
                <van-icon name="warning-o" />
                <span>还缺：{{ formatMissingFields(message.response.intent.missingFields) }}</span>
              </div>
            </article>

            <article class="clarify-card" v-if="message.response.needClarification && message.response.clarificationQuestions?.length">
              <header>
                <van-icon name="chat-o" size="18" />
                <h2>需要你补充</h2>
              </header>
              <button
                  v-for="question in message.response.clarificationQuestions"
                  :key="question"
                  type="button"
                  @click="fillQuestion(question)"
              >
                {{ question }}
              </button>
            </article>

            <article
                v-for="toolResult in message.response.toolResults"
                :key="`${message.id}-${toolResult.toolName}`"
                class="tool-card"
            >
              <header>
                <van-icon :name="getToolIcon(toolResult.toolName)" size="18" />
                <h2>{{ getToolTitle(toolResult.toolName) }}</h2>
                <van-tag round :type="toolResult.success ? 'success' : 'danger'">
                  {{ toolResult.success ? "完成" : "失败" }}
                </van-tag>
              </header>
              <p>{{ toolResult.summary || "工具已执行" }}</p>

              <div class="team-result-list" v-if="toolResult.toolName === 'searchTeams'">
                <article
                    v-for="team in getTeams(toolResult)"
                    :key="team.id"
                    class="team-result-card"
                >
                  <div class="team-result-card__main">
                    <h3>{{ team.name || "未命名队伍" }}</h3>
                    <p>{{ team.description || "这个队伍暂时没有描述。" }}</p>
                    <div class="team-result-card__tags">
                      <van-tag v-if="team.activityType" round>{{ team.activityType }}</van-tag>
                      <van-tag v-if="team.city || team.district" round>{{ formatLocation(team) }}</van-tag>
                      <van-tag v-if="team.budgetPerPerson !== undefined" round>{{ formatBudget(team.budgetPerPerson) }}</van-tag>
                      <van-tag round>{{ team.hasJoinNum ?? 0 }}/{{ team.maxNum }} 人</van-tag>
                    </div>
                  </div>
                  <div class="team-result-card__actions">
                    <van-button
                        size="small"
                        round
                        plain
                        :loading="isLoadingTeamDetails(team.id)"
                        @click="loadTeamDetails(team.id)"
                    >
                      详情
                    </van-button>
                    <van-button size="small" round plain @click="goTeamPage">
                      队伍页
                    </van-button>
                  </div>
                  <div class="team-detail-panel" v-if="getTeamDetails(team.id)">
                    <div>
                      <span>状态</span>
                      <strong>{{ formatAvailability(getTeamDetails(team.id)!) }}</strong>
                    </div>
                    <div>
                      <span>时间</span>
                      <strong>{{ formatDate(getTeamDetails(team.id)!.startTime) }}</strong>
                    </div>
                    <div>
                      <span>地点</span>
                      <strong>{{ formatLocation(getTeamDetails(team.id)!) }}</strong>
                    </div>
                    <div>
                      <span>创建者</span>
                      <strong>{{ getTeamDetails(team.id)!.createUser?.username || "未知" }}</strong>
                    </div>
                  </div>
                </article>
                <van-empty
                    v-if="getTeams(toolResult).length === 0"
                    image-size="64"
                    description="暂时没有找到符合条件的队伍"
                />
              </div>

              <div class="user-recommend-list" v-if="toolResult.toolName === 'recommendUsers'">
                <article
                    v-for="user in getRecommendedUsers(toolResult)"
                    :key="user.id"
                    class="user-recommend-card"
                >
                  <div class="user-recommend-card__avatar">
                    <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
                    <van-icon v-else name="contact-o" size="20" />
                  </div>
                  <div class="user-recommend-card__main">
                    <h3>{{ user.username || "未命名用户" }}</h3>
                    <p>{{ user.planetCode ? `星球编号 ${user.planetCode}` : "公开资料较少" }}</p>
                    <div class="user-recommend-card__tags">
                      <van-tag
                          v-for="tag in formatUserTags(user.tags)"
                          :key="`${user.id}-${tag}`"
                          round
                      >
                        {{ tag }}
                      </van-tag>
                    </div>
                    <div class="user-recommend-card__reasons" v-if="user.reasons?.length">
                      <span v-for="reason in user.reasons" :key="`${user.id}-${reason}`">
                        {{ reason }}
                      </span>
                    </div>
                  </div>
                </article>
                <van-empty
                    v-if="getRecommendedUsers(toolResult).length === 0"
                    image-size="64"
                    description="暂时没有推荐到合适用户"
                />
              </div>
            </article>

            <article class="draft-card" v-if="message.response.draft">
              <header>
                <van-icon name="records-o" size="18" />
                <h2>队伍草稿</h2>
              </header>
              <div class="draft-card__body">
                <h3>{{ message.response.draft.name || "未命名队伍" }}</h3>
                <p>{{ message.response.draft.description || "确认前不会写入业务表。" }}</p>
                <div class="draft-card__grid">
                  <span>活动：{{ message.response.draft.activityType || "待补充" }}</span>
                  <span>城市：{{ message.response.draft.city || "待补充" }}</span>
                  <span>人数：{{ formatCount(message.response.draft.maxNum) }}</span>
                  <span>预算：{{ formatBudget(message.response.draft.budgetPerPerson) }}</span>
                  <span>时间：{{ formatDate(message.response.draft.startTime) }}</span>
                  <span>有效期：{{ formatDate(message.response.draft.expiresAt) }}</span>
                </div>
              </div>
              <div class="draft-card__status" v-if="getConfirmedTeamId(message.response.draft.draftId)">
                <van-icon name="checked" />
                <span>已创建队伍 #{{ getConfirmedTeamId(message.response.draft.draftId) }}</span>
                <van-button size="small" round plain @click="goTeamPage">
                  查看
                </van-button>
              </div>
              <div class="draft-card__actions" v-else>
                <p>
                  <van-icon name="lock" />
                  确认前不会写入业务表，确认后会创建公开队伍并自动加入。
                </p>
                <van-button
                    size="small"
                    round
                    type="primary"
                    :loading="isConfirmingDraft(message.response.draft.draftId)"
                    @click="confirmDraft(message.response.draft.draftId)"
                >
                  确认创建
                </van-button>
              </div>
            </article>
          </template>
        </div>
      </article>

      <article class="chat-message chat-message--assistant" v-if="loading">
        <div class="chat-message__avatar">
          <van-icon name="service-o" size="20" />
        </div>
        <div class="chat-message__content">
          <div class="typing-card">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </article>
    </section>

    <form class="chat-composer" @submit.prevent="sendMessage">
      <button type="button" aria-label="语音输入" disabled>
        <van-icon name="volume-o" size="21" />
      </button>
      <input
          v-model="inputText"
          :disabled="loading"
          placeholder="例如：我想周末在西安找羽毛球搭子..."
      />
      <button type="button" aria-label="清空" @click="inputText = ''">
        <van-icon name="cross" size="19" />
      </button>
      <button class="chat-composer__send" type="submit" aria-label="发送" :disabled="loading">
        <van-icon name="guide-o" size="23" />
      </button>
    </form>
  </div>
</template>

<script setup lang="ts">
import {nextTick, ref} from 'vue';
import {useRouter} from "vue-router";
import {showFailToast, showSuccessToast} from "vant";
import myAxios from "../plugins/myAxios";
import {AiChatResponse, AiTeamDraftConfirmResponse, AiToolResult, AiUserRecommendation} from "../models/ai";
import {TeamType} from "../models/team";

type ChatMessage = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  time: string;
  response?: AiChatResponse;
};

const router = useRouter();
const streamRef = ref<HTMLElement | null>(null);
const inputText = ref('');
const sessionId = ref<string>();
const loading = ref(false);
const confirmingDraftId = ref<string>();
const confirmedDraftTeams = ref<Record<string, number>>({});
const loadingTeamDetailsId = ref<number>();
const teamDetails = ref<Record<number, TeamType>>({});
const messages = ref<ChatMessage[]>([
  {
    id: 1,
    role: 'assistant',
    content: '告诉我你想找的活动、城市、时间或预算。我会先识别需求，再调用受控工具查询队伍。',
    time: '现在',
  },
]);

const currentTime = () => {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
};

const scrollToBottom = async () => {
  await nextTick();
  window.scrollTo({
    top: document.documentElement.scrollHeight,
    behavior: 'smooth',
  });
};

const sendMessage = async () => {
  const content = inputText.value.trim();
  if (!content || loading.value) {
    return;
  }
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content,
    time: currentTime(),
  });
  inputText.value = '';
  loading.value = true;
  await scrollToBottom();
  try {
    const response = await myAxios.post<AiChatResponse>('/ai/chat', {
      sessionId: sessionId.value,
      message: content,
    });
    if (response?.code !== 0 || !response.data) {
      showFailToast(response?.description || response?.message || 'AI 助手暂时不可用');
      return;
    }
    sessionId.value = response.data.sessionId;
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: response.data.reply || '我已经整理好了这次需求。',
      time: currentTime(),
      response: response.data,
    });
  } catch (error) {
    console.error('/ai/chat error', error);
    showFailToast('AI 助手暂时不可用');
  } finally {
    loading.value = false;
    await scrollToBottom();
  }
};

const fillQuestion = (question: string) => {
  inputText.value = question;
};

const isConfirmingDraft = (draftId?: string) => {
  return !!draftId && confirmingDraftId.value === draftId;
};

const getConfirmedTeamId = (draftId?: string) => {
  if (!draftId) {
    return undefined;
  }
  return confirmedDraftTeams.value[draftId];
};

const confirmDraft = async (draftId?: string) => {
  if (!draftId || confirmingDraftId.value || getConfirmedTeamId(draftId)) {
    return;
  }
  confirmingDraftId.value = draftId;
  try {
    const response = await myAxios.post<AiTeamDraftConfirmResponse>(`/ai/team-draft/${draftId}/confirm`);
    if (response?.code !== 0 || !response.data) {
      showFailToast(response?.description || response?.message || '草稿确认失败');
      return;
    }
    confirmedDraftTeams.value = {
      ...confirmedDraftTeams.value,
      [draftId]: response.data.teamId,
    };
    showSuccessToast('队伍已创建');
  } catch (error) {
    console.error('/ai/team-draft confirm error', error);
    showFailToast('草稿确认失败');
  } finally {
    confirmingDraftId.value = undefined;
  }
};

const isLoadingTeamDetails = (teamId?: number) => {
  return !!teamId && loadingTeamDetailsId.value === teamId;
};

const getTeamDetails = (teamId?: number) => {
  if (!teamId) {
    return undefined;
  }
  return teamDetails.value[teamId];
};

const loadTeamDetails = async (teamId?: number) => {
  if (!teamId || loadingTeamDetailsId.value) {
    return;
  }
  const cached = getTeamDetails(teamId);
  if (cached) {
    return;
  }
  loadingTeamDetailsId.value = teamId;
  try {
    const response = await myAxios.post<AiToolResult>(`/ai/team/${teamId}/details`, {
      sessionId: sessionId.value,
    });
    if (response?.code !== 0 || !response.data?.success) {
      showFailToast(response?.description || response?.message || response.data?.summary || '队伍详情获取失败');
      return;
    }
    teamDetails.value = {
      ...teamDetails.value,
      [teamId]: response.data.data as TeamType,
    };
    await scrollToBottom();
  } catch (error) {
    console.error('/ai/team details error', error);
    showFailToast('队伍详情获取失败');
  } finally {
    loadingTeamDetailsId.value = undefined;
  }
};

const getToolTitle = (toolName: string) => {
  const titleMap: Record<string, string> = {
    searchTeams: '队伍查询',
    getTeamDetails: '队伍详情',
    recommendUsers: '搭子推荐',
    createTeamDraft: '草稿生成',
  };
  return titleMap[toolName] || toolName;
};

const getToolIcon = (toolName: string) => {
  const iconMap: Record<string, string> = {
    searchTeams: 'friends-o',
    getTeamDetails: 'notes-o',
    recommendUsers: 'contact-o',
    createTeamDraft: 'records-o',
  };
  return iconMap[toolName] || 'setting-o';
};

const getTeams = (toolResult: AiToolResult): TeamType[] => {
  return Array.isArray(toolResult.data) ? toolResult.data as TeamType[] : [];
};

const getRecommendedUsers = (toolResult: AiToolResult): AiUserRecommendation[] => {
  return Array.isArray(toolResult.data) ? toolResult.data as AiUserRecommendation[] : [];
};

const formatUserTags = (tags?: string) => {
  if (!tags) {
    return [];
  }
  try {
    const parsed = JSON.parse(tags);
    if (Array.isArray(parsed)) {
      return parsed.map(item => String(item)).filter(Boolean).slice(0, 4);
    }
  } catch (error) {
    // Keep compatibility with comma-separated legacy tags.
  }
  return tags.split(/[,，]/).map(tag => tag.trim()).filter(Boolean).slice(0, 4);
};

const formatCount = (value?: number) => {
  return value === undefined || value === null ? '待补充' : `${value} 人`;
};

const formatBudget = (value?: number) => {
  return value === undefined || value === null ? '不限' : `${value} 元`;
};

const formatDate = (value?: string | Date) => {
  if (!value) {
    return '待定';
  }
  const text = String(value);
  return text.length > 16 ? text.slice(0, 16).replace('T', ' ') : text;
};

const formatLocation = (team: TeamType) => {
  return [team.city, team.district].filter(Boolean).join(' · ') || '地点待定';
};

const formatMissingFields = (fields: string[]) => {
  const labelMap: Record<string, string> = {
    activityType: '活动类型',
    city: '城市',
    memberCount: '人数',
    message: '需求内容',
  };
  return fields.map(field => labelMap[field] || field).join('、');
};

const formatAvailability = (team: TeamType) => {
  if (team.hasJoin) {
    return '你已加入';
  }
  const joined = team.hasJoinNum ?? 0;
  if (team.maxNum !== undefined && joined >= team.maxNum) {
    return '已满员';
  }
  return `可加入 ${joined}/${team.maxNum} 人`;
};

const goTeamPage = () => {
  router.push('/team');
};
</script>

<style scoped>
.ai-chat-page {
  min-height: 100%;
  padding: 16px var(--app-page-x) 92px;
}

.ai-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 160px;
  padding: 22px;
  overflow: hidden;
  color: #ffffff;
  background:
      radial-gradient(circle at 82% 15%, rgba(255, 232, 186, 0.5), transparent 6rem),
      linear-gradient(135deg, #078f80 0%, #0ba992 58%, #92d0ad 100%);
  border-radius: 24px;
  box-shadow: 0 18px 34px rgba(16, 113, 101, 0.22);
}

.ai-hero__copy span {
  display: inline-flex;
  padding: 5px 10px;
  margin-bottom: 14px;
  color: rgba(255, 255, 255, 0.92);
  font-size: 12px;
  font-weight: 800;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
}

.ai-hero__copy h1 {
  max-width: 240px;
  margin: 0;
  font-size: 26px;
  font-weight: 900;
  line-height: 1.16;
  letter-spacing: 0;
}

.ai-hero__copy p {
  max-width: 250px;
  margin: 9px 0 0;
  color: rgba(255, 255, 255, 0.86);
  font-size: 14px;
  line-height: 1.55;
}

.ai-hero__bot {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 72px;
  width: 72px;
  height: 72px;
  color: var(--app-primary-deep);
  background: rgba(255, 255, 255, 0.92);
  border: 8px solid rgba(255, 255, 255, 0.42);
  border-radius: 50%;
}

.chat-stream {
  display: grid;
  gap: 12px;
  padding: 18px 0 0;
}

.chat-message {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.chat-message--user {
  justify-content: flex-end;
}

.chat-message__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  color: #ffffff;
  background: linear-gradient(135deg, var(--app-primary), var(--app-accent));
  border-radius: 50%;
}

.chat-message__content {
  display: grid;
  gap: 10px;
  max-width: min(86%, 620px);
}

.chat-message--user .chat-message__content {
  justify-items: end;
}

.chat-message__bubble {
  padding: 12px 14px;
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.55;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--app-border);
  border-radius: 18px 18px 18px 6px;
  box-shadow: 0 10px 22px rgba(22, 80, 73, 0.08);
}

.chat-message--user .chat-message__bubble {
  color: #102825;
  background: linear-gradient(135deg, rgba(230, 249, 244, 0.98), rgba(244, 255, 251, 0.96));
  border-radius: 18px 18px 6px 18px;
}

.chat-message__bubble span {
  display: block;
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 11px;
  text-align: right;
}

.intent-card,
.clarify-card,
.tool-card,
.draft-card {
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.intent-card,
.clarify-card,
.tool-card,
.draft-card {
  padding: 14px;
}

.intent-card header,
.clarify-card header,
.tool-card header,
.draft-card header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.tool-card header {
  justify-content: space-between;
}

.intent-card h2,
.clarify-card h2,
.tool-card h2,
.draft-card h2 {
  flex: 1;
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 900;
  line-height: 1.25;
  letter-spacing: 0;
}

.intent-card__grid,
.draft-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.intent-card__grid span,
.draft-card__grid span {
  min-width: 0;
  padding: 9px 10px;
  overflow: hidden;
  color: #263f3b;
  font-size: 13px;
  background: #f3f6f4;
  border-radius: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.intent-card__missing,
.draft-card__actions p,
.draft-card__status {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 12px;
  color: #7b5b18;
  font-size: 13px;
}

.draft-card__actions {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.draft-card__actions p {
  flex: 1;
  min-width: 0;
  margin: 0;
  line-height: 1.45;
}

.draft-card__actions :deep(.van-button),
.draft-card__status :deep(.van-button) {
  flex: 0 0 auto;
}

.draft-card__status {
  justify-content: space-between;
  padding: 10px 11px;
  color: #116456;
  font-weight: 800;
  background: rgba(24, 165, 143, 0.08);
  border: 1px solid rgba(24, 165, 143, 0.12);
  border-radius: 12px;
}

.draft-card__status span {
  flex: 1;
  min-width: 0;
}

.clarify-card {
  display: grid;
  gap: 8px;
}

.clarify-card button {
  padding: 9px 11px;
  color: var(--app-primary-deep);
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  background: rgba(24, 165, 143, 0.08);
  border: 1px solid rgba(24, 165, 143, 0.12);
  border-radius: 12px;
}

.tool-card p,
.draft-card p {
  margin: 0 0 12px;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.team-result-list,
.user-recommend-list {
  display: grid;
  gap: 10px;
}

.team-result-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: flex-start;
  padding: 11px;
  background: rgba(245, 247, 243, 0.82);
  border: 1px solid rgba(28, 61, 58, 0.06);
  border-radius: 14px;
}

.team-result-card__main {
  min-width: 0;
  flex: 1;
}

.team-result-card h3,
.draft-card h3 {
  margin: 0;
  overflow: hidden;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 900;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0;
}

.team-result-card p {
  display: -webkit-box;
  margin: 6px 0 0;
  overflow: hidden;
  color: #40504e;
  font-size: 12px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.team-result-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.team-result-card__tags :deep(.van-tag) {
  color: var(--app-primary-deep);
  font-weight: 700;
  background: rgba(24, 165, 143, 0.1);
  border: 0;
}

.team-result-card__actions {
  display: grid;
  gap: 7px;
}

.team-detail-panel {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid rgba(28, 61, 58, 0.08);
}

.team-detail-panel div {
  min-width: 0;
}

.team-detail-panel span {
  display: block;
  color: var(--app-text-muted);
  font-size: 11px;
  line-height: 1.3;
}

.team-detail-panel strong {
  display: block;
  margin-top: 3px;
  overflow: hidden;
  color: #203935;
  font-size: 13px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-recommend-card {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 11px;
  background: rgba(245, 247, 243, 0.82);
  border: 1px solid rgba(28, 61, 58, 0.06);
  border-radius: 14px;
}

.user-recommend-card__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 38px;
  width: 38px;
  height: 38px;
  overflow: hidden;
  color: var(--app-primary-deep);
  background: rgba(24, 165, 143, 0.1);
  border-radius: 50%;
}

.user-recommend-card__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-recommend-card__main {
  min-width: 0;
  flex: 1;
}

.user-recommend-card h3 {
  margin: 0;
  overflow: hidden;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 900;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0;
}

.user-recommend-card p {
  margin: 5px 0 0;
  color: #40504e;
  font-size: 12px;
  line-height: 1.4;
}

.user-recommend-card__tags,
.user-recommend-card__reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.user-recommend-card__tags :deep(.van-tag) {
  color: var(--app-primary-deep);
  font-weight: 700;
  background: rgba(24, 165, 143, 0.1);
  border: 0;
}

.user-recommend-card__reasons span {
  padding: 4px 7px;
  color: #5f4a17;
  font-size: 11px;
  font-weight: 700;
  background: rgba(255, 219, 128, 0.24);
  border-radius: 999px;
}

.typing-card {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  width: fit-content;
  padding: 13px 15px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--app-border);
  border-radius: 18px 18px 18px 6px;
  box-shadow: var(--app-shadow);
}

.typing-card span {
  width: 7px;
  height: 7px;
  background: var(--app-primary);
  border-radius: 50%;
  animation: typing-pulse 0.9s infinite ease-in-out;
}

.typing-card span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing-card span:nth-child(3) {
  animation-delay: 0.3s;
}

.chat-composer {
  position: fixed;
  right: 0;
  bottom: calc(var(--van-tabbar-height) + env(safe-area-inset-bottom));
  left: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: 42px 1fr 38px 46px;
  gap: 8px;
  align-items: center;
  padding: 10px var(--app-page-x);
  background: rgba(255, 255, 255, 0.9);
  border-top: 1px solid rgba(28, 61, 58, 0.08);
  backdrop-filter: blur(18px);
}

.chat-composer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: var(--app-text);
  background: #f4f6f4;
  border: 1px solid var(--app-border);
  border-radius: 50%;
}

.chat-composer button:disabled {
  opacity: 0.55;
}

.chat-composer input {
  min-width: 0;
  height: 40px;
  padding: 0 14px;
  color: var(--app-text);
  font-size: 14px;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  outline: 0;
}

.chat-composer__send {
  color: #ffffff !important;
  background: linear-gradient(135deg, var(--app-primary), var(--app-accent)) !important;
  border: 0 !important;
}

@keyframes typing-pulse {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.45;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}
</style>
