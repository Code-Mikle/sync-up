<template>
  <div class="ai-chat-page">
    <section class="chat-stream" ref="streamRef">
      <article
          v-for="message in messages"
          :key="message.id"
          class="chat-message"
          :class="`chat-message--${message.role}`"
      >
        <div class="chat-message__row">
          <div class="chat-message__avatar">
            <template v-if="message.role === 'assistant'">
              <van-icon name="service-o" size="20" />
            </template>
            <template v-else>
              <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" :alt="currentUser.username || '我'" />
              <span v-else>{{ userAvatarText }}</span>
            </template>
          </div>
          <div class="chat-message__content">
            <div class="chat-message__bubble">
              {{ message.content }}
              <span>{{ message.time }}</span>
            </div>
          </div>
        </div>

        <div class="chat-message__results" v-if="message.response">
            <article class="intent-card" v-if="shouldShowIntentCard(message.response)">
              <header>
                <van-icon name="description-o" size="18" />
                <h2>需求识别</h2>
              </header>
              <div class="intent-card__grid">
                <span>大类：{{ formatActivityCategory(message.response.intent?.activityCategory) || "待补充" }}</span>
                <span>活动：{{ message.response.intent?.activityType || "待补充" }}</span>
                <span>城市：{{ message.response.intent?.city || "待补充" }}</span>
                <span>人数：{{ formatCount(message.response.intent?.memberCount) }}</span>
                <span>预算：{{ formatBudget(message.response.intent?.budgetMax) }}</span>
                <span>时间：{{ formatDate(message.response.intent?.startTime) }}</span>
                <span>水平：{{ message.response.intent?.skillLevel || "不限" }}</span>
              </div>
              <div class="intent-card__missing" v-if="message.response.intent?.missingFields?.length">
                <van-icon name="warning-o" />
                <span>还缺：{{ formatMissingFields(message.response.intent?.missingFields ?? []) }}</span>
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
                v-for="toolResult in getVisibleToolResults(message.response)"
                :key="`${message.id}-${toolResult.toolName}`"
                class="tool-card"
                :class="`tool-card--${toolResult.toolName}`"
            >
              <header>
                <van-icon :name="getToolIcon(toolResult.toolName)" size="18" />
                <h2>{{ getToolTitle(toolResult.toolName) }}</h2>
                <van-tag round :type="toolResult.success ? 'success' : 'danger'">
                  {{ toolResult.success ? "完成" : "失败" }}
                </van-tag>
              </header>
              <p v-if="shouldShowToolSummary(toolResult)">{{ getToolSummary(toolResult) }}</p>

              <div class="profile-result" v-if="toolResult.toolName === 'getMyProfile'">
                <div class="profile-result__avatar">
                  <img v-if="getProfileData(toolResult)?.avatarUrl" :src="getProfileData(toolResult)?.avatarUrl" alt="" />
                  <van-icon v-else name="contact-o" size="22" />
                </div>
                <div class="profile-result__main">
                  <h3>{{ getProfileData(toolResult)?.username || "未命名用户" }}</h3>
                  <p>{{ getProfileData(toolResult)?.profile || "还没有填写自我介绍" }}</p>
                  <div class="profile-result__meta">
                    <span v-if="getProfileData(toolResult)?.planetCode">星球编号 {{ getProfileData(toolResult)?.planetCode }}</span>
                    <span v-if="getProfileData(toolResult)?.structuredProfile?.city">{{ getProfileData(toolResult)?.structuredProfile?.city }}</span>
                    <span v-if="getProfileActivities(toolResult).length">{{ getProfileActivities(toolResult).join("、") }}</span>
                  </div>
                  <div class="profile-result__tags" v-if="formatUserTags(getProfileData(toolResult)?.tags).length">
                    <van-tag
                        v-for="tag in formatUserTags(getProfileData(toolResult)?.tags)"
                        :key="`profile-${tag}`"
                        round
                    >
                      {{ tag }}
                    </van-tag>
                  </div>
                </div>
              </div>

              <div class="operation-result" v-else-if="isOperationTool(toolResult.toolName)">
                <van-icon :name="toolResult.success ? 'checked' : 'warning-o'" size="22" />
                <div>
                  <h3>{{ getOperationTitle(toolResult) }}</h3>
                  <p>{{ getOperationDescription(toolResult) }}</p>
                  <div class="profile-draft-actions" v-if="isPendingProfileDraft(toolResult)">
                    <van-button
                        size="small"
                        round
                        type="primary"
                        :loading="processingProfileDraftId === getProfileDraftId(toolResult)"
                        @click="confirmProfileDraft(toolResult)"
                    >确认更新</van-button>
                    <van-button
                        size="small"
                        round
                        plain
                        :disabled="processingProfileDraftId === getProfileDraftId(toolResult)"
                        @click="rejectProfileDraft(toolResult)"
                    >拒绝</van-button>
                  </div>
                </div>
              </div>

              <div class="team-result-list" v-else-if="isTeamListTool(toolResult.toolName)">
                <article
                    v-for="team in getTeams(toolResult)"
                    :key="team.id"
                    class="team-result-card"
                >
                  <div class="team-result-card__main">
                    <h3>{{ team.name || "未命名队伍" }}</h3>
                    <p>{{ team.description || "这个队伍暂时没有描述。" }}</p>
                    <div class="team-result-card__tags">
                      <van-tag v-if="formatTeamActivityCategory(team)" round>{{ formatTeamActivityCategory(team) }}</van-tag>
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

              <div class="user-recommend-list" v-else-if="toolResult.toolName === 'recommendUsers'">
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
                  <span>大类：{{ formatActivityCategory(message.response.draft.activityCategory) || "待补充" }}</span>
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

            <article class="delete-card" v-if="message.response.deleteConfirmation">
              <header>
                <van-icon name="delete-o" size="18" />
                <h2>删除队伍确认</h2>
              </header>
              <div class="draft-card__body">
                <h3>{{ message.response.deleteConfirmation.name || `队伍 #${message.response.deleteConfirmation.teamId}` }}</h3>
                <p>{{ message.response.deleteConfirmation.description || "确认后会删除该队伍。" }}</p>
                <div class="draft-card__grid">
                  <span>编号：#{{ message.response.deleteConfirmation.teamId }}</span>
                  <span>大类：{{ formatActivityCategory(message.response.deleteConfirmation.activityCategory) || "待补充" }}</span>
                  <span>活动：{{ message.response.deleteConfirmation.activityType || "待补充" }}</span>
                  <span>地点：{{ formatDeleteLocation(message.response.deleteConfirmation) }}</span>
                  <span>人数：{{ formatCount(message.response.deleteConfirmation.maxNum) }}</span>
                  <span>已加入：{{ message.response.deleteConfirmation.hasJoinNum ?? 0 }} 人</span>
                  <span>时间：{{ formatDate(message.response.deleteConfirmation.startTime) }}</span>
                </div>
              </div>
              <div class="draft-card__status delete-card__status" v-if="isTeamDeleted(message.response.deleteConfirmation.teamId)">
                <van-icon name="checked" />
                <span>已删除队伍 #{{ message.response.deleteConfirmation.teamId }}</span>
              </div>
              <div class="draft-card__actions delete-card__actions" v-else>
                <p>
                  <van-icon name="warning-o" />
                  {{ message.response.deleteConfirmation.warning || "确认后会删除该队伍，并移除已有成员关系。" }}
                </p>
                <van-button
                    size="small"
                    round
                    type="danger"
                    :loading="isDeletingTeam(message.response.deleteConfirmation.teamId)"
                    @click="confirmDeleteTeam(message.response.deleteConfirmation.teamId)"
                >
                  确认删除
                </van-button>
              </div>
            </article>
        </div>
      </article>

      <article class="chat-message chat-message--assistant" v-if="loading">
        <div class="chat-message__row">
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
import {computed, nextTick, onMounted, ref} from 'vue';
import {useRouter} from "vue-router";
import {showFailToast, showSuccessToast} from "vant";
import myAxios from "../plugins/myAxios";
import {
  AiChatHistory,
  AiChatMessage,
  AiChatResponse,
  AiProfileResponse,
  AiTeamDeleteConfirmation,
  AiTeamDraftConfirmResponse,
  AiToolResult,
  AiUserProfileData,
  AiUserRecommendation
} from "../models/ai";
import {TeamType} from "../models/team";
import {UserType} from "../models/user";
import {getCurrentUser} from "../services/user";
import {getTeamActivityCategoryName} from "../constants/team";

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
const deletingTeamId = ref<number>();
const deletedTeams = ref<Record<number, boolean>>({});
const processingProfileDraftId = ref<string>();
const profileDraftStatus = ref<Record<string, 'confirmed' | 'rejected'>>({});
const loadingTeamDetailsId = ref<number>();
const teamDetails = ref<Record<number, TeamType>>({});
const currentUser = ref<UserType | null>(null);
const welcomeMessage = (): ChatMessage => ({
  id: 1,
  role: 'assistant',
  content: '你可以直接告诉我想找队伍、查看资料，或生成待确认的队伍和画像草稿。',
  time: '现在',
});
const messages = ref<ChatMessage[]>([welcomeMessage()]);

const userAvatarText = computed(() => {
  const name = currentUser.value?.username || currentUser.value?.userAccount || '我';
  return name.trim().slice(0, 1).toUpperCase();
});

onMounted(async () => {
  try {
    currentUser.value = await getCurrentUser();
  } catch (error) {
    console.warn('load current user failed', error);
  }
  await loadChatHistory();
});

const currentTime = () => {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
};

const formatMessageTime = (value?: string | Date) => {
  if (!value) {
    return currentTime();
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return currentTime();
  }
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};

const loadChatHistory = async () => {
  try {
    const response = await myAxios.get<AiChatHistory>('/ai/chat/history');
    if (response?.code !== 0 || !response.data) {
      return;
    }
    const history = response.data;
    sessionId.value = history.sessionId || sessionId.value;
    restoreConfirmedDraftTeams(history.messages ?? []);
    restoreDeletedTeams(history.messages ?? []);
    const visibleMessages = (history.messages ?? [])
        .filter(item => item.visible !== 0)
        .filter(item => item.role === 'user' || item.role === 'assistant')
        .map((item, index) => toChatMessage(item, index));
    messages.value = visibleMessages.length ? visibleMessages : [welcomeMessage()];
    await scrollToBottom();
  } catch (error) {
    console.warn('/ai/chat/history error', error);
  }
};

const toChatMessage = (item: AiChatMessage, index: number): ChatMessage => {
  return {
    id: item.id ?? Date.now() + index,
    role: item.role === 'user' ? 'user' : 'assistant',
    content: item.content || '',
    time: formatMessageTime(item.createTime),
    response: item.response,
  };
};

const restoreConfirmedDraftTeams = (historyMessages: AiChatMessage[]) => {
  const restored: Record<string, number> = {};
  historyMessages.forEach(item => {
    if (item.role === 'event'
        && (item.eventType === 'TEAM_DRAFT_CONFIRMED' || item.eventType === 'TEAM_CREATED')
        && item.relatedDraftId
        && item.relatedTeamId) {
      restored[item.relatedDraftId] = item.relatedTeamId;
    }
  });
  confirmedDraftTeams.value = restored;
};

const restoreDeletedTeams = (historyMessages: AiChatMessage[]) => {
  const restored: Record<number, boolean> = {};
  historyMessages.forEach(item => {
    if (item.role === 'event'
        && item.eventType === 'TEAM_DELETED'
        && item.relatedTeamId) {
      restored[item.relatedTeamId] = true;
    }
  });
  deletedTeams.value = restored;
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
      content: normalizeAssistantReply(response.data),
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

const isDeletingTeam = (teamId?: number) => {
  return !!teamId && deletingTeamId.value === teamId;
};

const isTeamDeleted = (teamId?: number) => {
  return !!teamId && Boolean(deletedTeams.value[teamId]);
};

const confirmDeleteTeam = async (teamId?: number) => {
  if (!teamId || deletingTeamId.value || isTeamDeleted(teamId)) {
    return;
  }
  deletingTeamId.value = teamId;
  try {
    const response = await myAxios.post<AiToolResult>(`/ai/team/${teamId}/delete/confirm`, {
      sessionId: sessionId.value,
    });
    if (response?.code !== 0 || !response.data?.success) {
      showFailToast(response?.description || response?.message || response.data?.summary || '删除队伍失败');
      return;
    }
    deletedTeams.value = {
      ...deletedTeams.value,
      [teamId]: true,
    };
    showSuccessToast('队伍已删除');
  } catch (error) {
    console.error('/ai/team delete confirm error', error);
    showFailToast('删除队伍失败');
  } finally {
    deletingTeamId.value = undefined;
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

const getVisibleToolResults = (response: AiChatResponse) => {
  return (response.toolResults ?? []).filter(toolResult => {
    if (toolResult.toolName === 'createTeamDraft' && toolResult.success && response.draft) {
      return false;
    }
    if (toolResult.toolName === 'prepareDeleteTeam' && toolResult.success && response.deleteConfirmation) {
      return false;
    }
    return true;
  });
};

const normalizeAssistantReply = (response: AiChatResponse) => {
  const toolNames = (response.toolResults ?? []).map(tool => tool.toolName);
  if (response.deleteConfirmation) {
    return '我找到了要删除的队伍，请确认后再删除。';
  }
  if (toolNames.includes('getMyProfile')) {
    return '这是你的个人资料。';
  }
  if (toolNames.includes('updateMyProfile')) {
    return '我整理了一份个人画像草稿，确认后才会更新资料。';
  }
  if (toolNames.includes('listMyJoinedTeams')) {
    const teams = getTeamsByToolName(response, 'listMyJoinedTeams');
    return teams.length ? `你目前加入了 ${teams.length} 个队伍。` : '你暂时还没有加入队伍。';
  }
  if (toolNames.includes('listMyCreatedTeams')) {
    const teams = getTeamsByToolName(response, 'listMyCreatedTeams');
    return teams.length ? `你目前创建了 ${teams.length} 个队伍。` : '你暂时还没有创建队伍。';
  }
  if (toolNames.includes('joinTeam')) {
    return '已帮你加入队伍。';
  }
  if (toolNames.includes('quitTeam')) {
    return '已帮你退出队伍。';
  }
  if (response.draft) {
    return '我整理了一份队伍草稿，确认后才会正式创建。';
  }
  if (response.needClarification) {
    return cleanAssistantText(response.reply) || '我还需要你补充一点信息。';
  }
  return cleanAssistantText(response.reply) || '我已经处理好了。';
};

const cleanAssistantText = (text?: string) => {
  if (!text) {
    return '';
  }
  return text
      .replace(/\*\*([^*]+)\*\*/g, '$1')
      .replace(/\*/g, '')
      .replace(/[ \t]*(\d+)[.．、][ \t]*/g, '\n$1. ')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
};

const shouldShowIntentCard = (response: AiChatResponse) => {
  if (!response.intent?.teamRelated) {
    return false;
  }
  const toolNames = (response.toolResults ?? []).map(tool => tool.toolName);
  const cardWorthyTools = ['searchTeams', 'createTeamDraft', 'prepareDeleteTeam'];
  return Boolean(response.needClarification)
      || Boolean(response.draft)
      || Boolean(response.deleteConfirmation)
      || toolNames.some(toolName => cardWorthyTools.includes(toolName));
};

const isTeamListTool = (toolName: string) => {
  return ['searchTeams', 'listMyJoinedTeams', 'listMyCreatedTeams'].includes(toolName);
};

const isOperationTool = (toolName: string) => {
  return ['joinTeam', 'quitTeam', 'updateMyProfile'].includes(toolName);
};

const shouldShowToolSummary = (toolResult: AiToolResult) => {
  if (toolResult.toolName === 'createTeamDraft') {
    return !toolResult.success;
  }
  return !['getMyProfile', 'joinTeam', 'quitTeam', 'updateMyProfile'].includes(toolResult.toolName);
};

const getToolSummary = (toolResult: AiToolResult) => {
  if (!toolResult.success) {
    return toolResult.summary || '操作没有完成';
  }
  const teams = getTeams(toolResult);
  if (toolResult.toolName === 'listMyJoinedTeams') {
    return teams.length ? `你当前加入了 ${teams.length} 个队伍。` : '你暂时还没有加入队伍。';
  }
  if (toolResult.toolName === 'listMyCreatedTeams') {
    return teams.length ? `你当前创建了 ${teams.length} 个队伍。` : '你暂时还没有创建队伍。';
  }
  return toolResult.summary || '工具已执行';
};

const getToolTitle = (toolName: string) => {
  const titleMap: Record<string, string> = {
    searchTeams: '队伍查询',
    getTeamDetails: '队伍详情',
    recommendUsers: '搭子推荐',
    createTeamDraft: '草稿生成',
    prepareDeleteTeam: '删除确认',
    getMyProfile: '我的资料',
    updateMyProfile: '画像更新草稿',
    listMyJoinedTeams: '我加入的队伍',
    listMyCreatedTeams: '我创建的队伍',
    joinTeam: '已加入队伍',
    quitTeam: '已退出队伍',
  };
  return titleMap[toolName] || toolName;
};

const getToolIcon = (toolName: string) => {
  const iconMap: Record<string, string> = {
    searchTeams: 'friends-o',
    getTeamDetails: 'notes-o',
    recommendUsers: 'contact-o',
    createTeamDraft: 'records-o',
    prepareDeleteTeam: 'delete-o',
    getMyProfile: 'manager-o',
    updateMyProfile: 'edit',
    listMyJoinedTeams: 'friends-o',
    listMyCreatedTeams: 'cluster-o',
    joinTeam: 'add-o',
    quitTeam: 'close',
  };
  return iconMap[toolName] || 'setting-o';
};

const getTeams = (toolResult: AiToolResult): TeamType[] => {
  return Array.isArray(toolResult.data) ? toolResult.data as TeamType[] : [];
};

const getTeamsByToolName = (response: AiChatResponse, toolName: string): TeamType[] => {
  const toolResult = (response.toolResults ?? []).find(item => item.toolName === toolName);
  return toolResult ? getTeams(toolResult) : [];
};

const getRecommendedUsers = (toolResult: AiToolResult): AiUserRecommendation[] => {
  return Array.isArray(toolResult.data) ? toolResult.data as AiUserRecommendation[] : [];
};

const getProfileData = (toolResult: AiToolResult): AiUserProfileData | undefined => {
  if (!toolResult.data || Array.isArray(toolResult.data) || typeof toolResult.data !== 'object') {
    return undefined;
  }
  return toolResult.data as AiUserProfileData;
};

const getProfileActivities = (toolResult: AiToolResult) => {
  const profile = getProfileData(toolResult)?.structuredProfile;
  return [
    ...(profile?.activityTypes ?? []),
    ...(profile?.interests ?? []),
  ].filter((item, index, array) => item && array.indexOf(item) === index).slice(0, 4);
};

const getProfileResponse = (toolResult: AiToolResult): AiProfileResponse | undefined => {
  if (!toolResult.data || Array.isArray(toolResult.data) || typeof toolResult.data !== 'object') {
    return undefined;
  }
  return toolResult.data as AiProfileResponse;
};

const getOperationTitle = (toolResult: AiToolResult) => {
  if (!toolResult.success) {
    return '操作失败';
  }
  const titleMap: Record<string, string> = {
    updateMyProfile: getProfileDraftStatus(toolResult) === 'confirmed'
        ? '个人资料已更新'
        : getProfileDraftStatus(toolResult) === 'rejected'
            ? '已拒绝画像草稿'
            : '请确认画像草稿',
    joinTeam: '已加入队伍',
    quitTeam: '已退出队伍',
  };
  return titleMap[toolResult.toolName] || '操作已完成';
};

const getOperationDescription = (toolResult: AiToolResult) => {
  if (!toolResult.success) {
    return toolResult.summary || '你可以稍后再试，或补充必要信息。';
  }
  if (toolResult.toolName === 'updateMyProfile') {
    const profile = getProfileResponse(toolResult)?.profile;
    const city = profile?.city ? `，城市偏好：${profile.city}` : '';
    const activities = profile?.activityTypes?.length ? `，活动：${profile.activityTypes.join('、')}` : '';
    if (getProfileDraftStatus(toolResult) === 'confirmed') {
      return `已更新你的自我介绍和结构化画像${city}${activities}。`;
    }
    if (getProfileDraftStatus(toolResult) === 'rejected') {
      return '这份画像草稿已拒绝，不会修改个人资料。';
    }
    return `请检查这份结构化画像${city}${activities}，确认后才会写入个人资料。`;
  }
  if (toolResult.toolName === 'joinTeam') {
    return '我已经帮你加入该队伍，后续可以在“我加入的队伍”里查看。';
  }
  if (toolResult.toolName === 'quitTeam') {
    return '我已经帮你退出该队伍。';
  }
  return toolResult.summary || '操作已完成。';
};

const getProfileDraftId = (toolResult: AiToolResult) => {
  const profileResponse = getProfileResponse(toolResult);
  return profileResponse?.draftId;
};

const getProfileDraftStatus = (toolResult: AiToolResult) => {
  const draftId = getProfileDraftId(toolResult);
  return draftId ? profileDraftStatus.value[draftId] : undefined;
};

const isPendingProfileDraft = (toolResult: AiToolResult) => {
  return toolResult.toolName === 'updateMyProfile'
      && !!getProfileDraftId(toolResult)
      && !getProfileDraftStatus(toolResult);
};

const confirmProfileDraft = async (toolResult: AiToolResult) => {
  const draftId = getProfileDraftId(toolResult);
  if (!draftId || processingProfileDraftId.value) {
    return;
  }
  processingProfileDraftId.value = draftId;
  try {
    const response = await myAxios.post<AiProfileResponse>(`/ai/profile-draft/${draftId}/confirm`, {});
    if (response?.code !== 0) {
      showFailToast(response?.description || response?.message || '画像确认失败');
      return;
    }
    profileDraftStatus.value = {...profileDraftStatus.value, [draftId]: 'confirmed'};
    showSuccessToast('个人资料已更新');
  } catch (error) {
    console.error('/ai/profile-draft confirm error', error);
    showFailToast('画像确认失败');
  } finally {
    processingProfileDraftId.value = undefined;
  }
};

const rejectProfileDraft = async (toolResult: AiToolResult) => {
  const draftId = getProfileDraftId(toolResult);
  if (!draftId || processingProfileDraftId.value) {
    return;
  }
  processingProfileDraftId.value = draftId;
  try {
    const response = await myAxios.post<AiProfileResponse>(`/ai/profile-draft/${draftId}/reject`);
    if (response?.code !== 0) {
      showFailToast(response?.description || response?.message || '拒绝画像失败');
      return;
    }
    profileDraftStatus.value = {...profileDraftStatus.value, [draftId]: 'rejected'};
    showSuccessToast('已拒绝画像草稿');
  } catch (error) {
    console.error('/ai/profile-draft reject error', error);
    showFailToast('拒绝画像失败');
  } finally {
    processingProfileDraftId.value = undefined;
  }
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

const formatDeleteLocation = (confirmation: AiTeamDeleteConfirmation) => {
  return [confirmation.city, confirmation.district].filter(Boolean).join(' · ') || '地点待定';
};

const formatActivityCategory = (code?: number) => {
  return getTeamActivityCategoryName(code);
};

const formatTeamActivityCategory = (team: TeamType) => {
  return team.activityCategoryName || formatActivityCategory(team.activityCategory);
};

const formatMissingFields = (fields: string[]) => {
  const labelMap: Record<string, string> = {
    activityCategory: '活动大类',
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

.chat-stream {
  display: grid;
  gap: 12px;
  padding: 0;
}

.chat-message {
  display: grid;
  gap: 8px;
}

.chat-message--user {
  justify-items: end;
}

.chat-message__row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  width: 100%;
}

.chat-message--user .chat-message__row {
  flex-direction: row-reverse;
  justify-content: flex-start;
}

.chat-message__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  overflow: hidden;
  color: #ffffff;
  background: var(--app-brand-gradient);
  border-radius: 50%;
}

.chat-message--user .chat-message__avatar {
  color: var(--app-primary-deep);
  font-size: 13px;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.96);
  border: 2px solid rgba(var(--app-primary-rgb), 0.18);
}

.chat-message__avatar img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.chat-message__content {
  display: grid;
  gap: 10px;
  max-width: calc(100% - 44px);
}

.chat-message--user .chat-message__content {
  justify-items: end;
}

.chat-message__results {
  display: grid;
  gap: 10px;
  width: min(calc(100% - 44px), 620px);
  margin-left: 44px;
}

.chat-message--user .chat-message__results {
  margin-right: 44px;
  margin-left: 0;
}

.chat-message__bubble {
  padding: 12px 14px;
  color: var(--app-text);
  font-size: 15px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--app-border);
  border-radius: 18px 18px 18px 6px;
  box-shadow: 0 10px 22px rgba(52, 48, 139, 0.09);
}

.chat-message--user .chat-message__bubble {
  color: var(--app-primary-deep);
  background: linear-gradient(135deg, rgba(235, 241, 255, 0.98), rgba(246, 241, 255, 0.96));
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
.draft-card,
.delete-card {
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.intent-card,
.clarify-card,
.tool-card,
.draft-card,
.delete-card {
  padding: 14px;
}

.intent-card header,
.clarify-card header,
.tool-card header,
.draft-card header,
.delete-card header {
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
.draft-card h2,
.delete-card h2 {
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
  color: #484967;
  font-size: 13px;
  background: #f3f4fb;
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
  color: var(--app-primary-deep);
  font-weight: 800;
  background: rgba(var(--app-primary-rgb), 0.08);
  border: 1px solid rgba(var(--app-primary-rgb), 0.12);
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
  background: rgba(var(--app-primary-rgb), 0.08);
  border: 1px solid rgba(var(--app-primary-rgb), 0.12);
  border-radius: 12px;
}

.tool-card p,
.draft-card p,
.delete-card p {
  margin: 0 0 12px;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.profile-result,
.operation-result {
  display: flex;
  gap: 11px;
  align-items: flex-start;
  padding: 11px;
  background: rgba(244, 245, 252, 0.86);
  border: 1px solid rgba(40, 38, 101, 0.06);
  border-radius: 14px;
}

.profile-result__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 42px;
  width: 42px;
  height: 42px;
  overflow: hidden;
  color: var(--app-primary-deep);
  background: rgba(var(--app-primary-rgb), 0.1);
  border-radius: 50%;
}

.profile-result__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-result__main,
.operation-result div {
  min-width: 0;
  flex: 1;
}

.profile-result h3,
.operation-result h3 {
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

.profile-result p,
.operation-result p {
  margin: 5px 0 0;
  color: #4b4c69;
  font-size: 12px;
  line-height: 1.45;
}

.profile-draft-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 10px;
}

.profile-result__meta,
.profile-result__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.profile-result__meta span {
  padding: 4px 7px;
  color: #50516f;
  font-size: 11px;
  font-weight: 800;
  background: rgba(var(--app-primary-rgb), 0.1);
  border-radius: 999px;
}

.profile-result__tags :deep(.van-tag) {
  color: var(--app-primary-deep);
  font-weight: 700;
  background: rgba(var(--app-primary-rgb), 0.1);
  border: 0;
}

.operation-result {
  align-items: center;
  color: var(--app-primary-deep);
  background: rgba(var(--app-primary-rgb), 0.08);
  border-color: rgba(var(--app-primary-rgb), 0.14);
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
  background: rgba(244, 245, 252, 0.86);
  border: 1px solid rgba(40, 38, 101, 0.06);
  border-radius: 14px;
}

.team-result-card__main {
  min-width: 0;
  flex: 1;
}

.team-result-card h3,
.draft-card h3,
.delete-card h3 {
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

.delete-card__actions p {
  color: #a33737;
}

.delete-card__status {
  color: #2f7d48;
  background: rgba(34, 150, 92, 0.08);
  border-color: rgba(34, 150, 92, 0.14);
}

.team-result-card p {
  display: -webkit-box;
  margin: 6px 0 0;
  overflow: hidden;
  color: #4b4c69;
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
  background: rgba(var(--app-primary-rgb), 0.1);
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
  border-top: 1px solid rgba(40, 38, 101, 0.08);
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
  color: #3f405e;
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
  background: rgba(244, 245, 252, 0.86);
  border: 1px solid rgba(40, 38, 101, 0.06);
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
  background: rgba(var(--app-primary-rgb), 0.1);
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
  color: #4b4c69;
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
  background: rgba(var(--app-primary-rgb), 0.1);
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
  border-top: 1px solid rgba(40, 38, 101, 0.08);
  backdrop-filter: blur(18px);
}

.chat-composer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  color: var(--app-text);
  background: #f3f4fb;
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
  background: var(--app-brand-gradient) !important;
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
