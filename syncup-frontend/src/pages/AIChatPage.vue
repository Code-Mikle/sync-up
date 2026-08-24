<template>
  <div class="ai-chat-page" :style="{'--composer-height': `${composerHeight}px`}">
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
              <AssistantMarkdownContent
                  v-if="message.role === 'assistant'"
                  :content="message.content"
              />
              <template v-else>{{ message.content }}</template>
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
                v-for="(uiBlock, index) in getContentUiBlocks(message.response)"
                :key="`${message.id}-${uiBlock.type}-${uiBlock.variant || index}`"
                class="tool-card"
                :class="`tool-card--${uiBlock.type}`"
            >
              <header>
                <van-icon :name="getUiBlockIcon(uiBlock)" size="18" />
                <h2>{{ getUiBlockTitle(uiBlock) }}</h2>
              </header>

              <div class="profile-result" v-if="uiBlock.type === 'profile_card'">
                <div class="profile-result__avatar">
                  <img v-if="getProfileData(uiBlock)?.avatarUrl" :src="getProfileData(uiBlock)?.avatarUrl" alt="" />
                  <van-icon v-else name="contact-o" size="22" />
                </div>
                <div class="profile-result__main">
                  <h3>{{ getProfileData(uiBlock)?.username || "未命名用户" }}</h3>
                  <p>{{ getProfileData(uiBlock)?.profile || "还没有填写自我介绍" }}</p>
                  <div class="profile-result__meta">
                    <span v-if="getProfileData(uiBlock)?.city">{{ getProfileData(uiBlock)?.city }}</span>
                  </div>
                  <div class="profile-result__tags" v-if="formatUserTagNames(getProfileData(uiBlock)?.tagNames).length">
                    <van-tag
                        v-for="tag in formatUserTagNames(getProfileData(uiBlock)?.tagNames)"
                        :key="`profile-${tag}`"
                        round
                    >
                      {{ tag }}
                    </van-tag>
                  </div>
                </div>
              </div>

              <div class="team-result-list" v-else-if="uiBlock.type === 'team_list'">
                <article
                    v-for="team in getTeams(uiBlock)"
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
                    v-if="getTeams(uiBlock).length === 0"
                    image-size="64"
                    description="暂时没有找到符合条件的队伍"
                />
              </div>

              <div class="user-recommend-list" v-else-if="uiBlock.type === 'user_recommendations'">
                <article
                    v-for="user in getRecommendedUsers(uiBlock)"
                    :key="user.id"
                    class="user-recommend-card"
                >
                  <div class="user-recommend-card__avatar">
                    <img v-if="user.avatarUrl" :src="user.avatarUrl" alt="" />
                    <van-icon v-else name="contact-o" size="20" />
                  </div>
                  <div class="user-recommend-card__main">
                    <h3>{{ user.username || "未命名用户" }}</h3>
                    <div class="user-recommend-card__tags">
                      <van-tag
                          v-for="tag in formatUserTagNames(user.tagNames)"
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
                    v-if="getRecommendedUsers(uiBlock).length === 0"
                    image-size="64"
                    description="暂时没有推荐到合适用户"
                />
              </div>
            </article>

            <article class="draft-card" v-if="getTeamDraft(message.response)">
              <header>
                <van-icon name="records-o" size="18" />
                <h2>队伍草稿</h2>
              </header>
              <div class="draft-card__body">
                <h3>{{ getTeamDraft(message.response)?.name || "未命名队伍" }}</h3>
                <p>{{ getTeamDraft(message.response)?.description || "确认前不会写入业务表。" }}</p>
                <div class="draft-card__grid">
                  <span>大类：{{ formatActivityCategory(getTeamDraft(message.response)?.activityCategory) || "待补充" }}</span>
                  <span>活动：{{ getTeamDraft(message.response)?.activityType || "待补充" }}</span>
                  <span>城市：{{ getTeamDraft(message.response)?.city || "待补充" }}</span>
                  <span>人数：{{ formatCount(getTeamDraft(message.response)?.maxNum) }}</span>
                  <span>预算：{{ formatBudget(getTeamDraft(message.response)?.budgetPerPerson) }}</span>
                  <span>时间：{{ formatDate(getTeamDraft(message.response)?.startTime) }}</span>
                  <span>有效期：{{ formatDate(getTeamDraft(message.response)?.expiresAt) }}</span>
                </div>
              </div>
              <div class="draft-card__status" v-if="getConfirmedTeamId(getTeamDraft(message.response)?.draftId)">
                <van-icon name="checked" />
                <span>已创建队伍 #{{ getConfirmedTeamId(getTeamDraft(message.response)?.draftId) }}</span>
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
                    :loading="isConfirmingDraft(getTeamDraft(message.response)?.draftId)"
                    @click="confirmDraft(getTeamDraft(message.response)?.draftId)"
                >
                  确认创建
                </van-button>
              </div>
            </article>

            <article class="delete-card" v-if="getDeleteConfirmation(message.response)">
              <header>
                <van-icon name="delete-o" size="18" />
                <h2>删除队伍确认</h2>
              </header>
              <div class="draft-card__body">
                <h3>{{ getDeleteConfirmation(message.response)?.name || `队伍 #${getDeleteConfirmation(message.response)?.teamId}` }}</h3>
                <p>{{ getDeleteConfirmation(message.response)?.description || "确认后会删除该队伍。" }}</p>
                <div class="draft-card__grid">
                  <span>编号：#{{ getDeleteConfirmation(message.response)?.teamId }}</span>
                  <span>大类：{{ formatActivityCategory(getDeleteConfirmation(message.response)?.activityCategory) || "待补充" }}</span>
                  <span>活动：{{ getDeleteConfirmation(message.response)?.activityType || "待补充" }}</span>
                  <span>地点：{{ formatDeleteLocation(getDeleteConfirmation(message.response)!) }}</span>
                  <span>人数：{{ formatCount(getDeleteConfirmation(message.response)?.maxNum) }}</span>
                  <span>已加入：{{ getDeleteConfirmation(message.response)?.hasJoinNum ?? 0 }} 人</span>
                  <span>时间：{{ formatDate(getDeleteConfirmation(message.response)?.startTime) }}</span>
                </div>
              </div>
              <div class="draft-card__status delete-card__status" v-if="isTeamDeleted(getDeleteConfirmation(message.response)!.teamId)">
                <van-icon name="checked" />
                <span>已删除队伍 #{{ getDeleteConfirmation(message.response)?.teamId }}</span>
              </div>
              <div class="draft-card__actions delete-card__actions" v-else>
                <p>
                  <van-icon name="warning-o" />
                  {{ getDeleteConfirmation(message.response)?.warning || "确认后会删除该队伍，并移除已有成员关系。" }}
                </p>
                <van-button
                    size="small"
                    round
                    type="danger"
                    :loading="isDeletingTeam(getDeleteConfirmation(message.response)!.teamId)"
                    @click="confirmDeleteTeam(getDeleteConfirmation(message.response)!.teamId)"
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

    <form ref="composerRef" class="chat-composer" @submit.prevent="sendMessage">
      <button type="button" aria-label="语音输入" disabled>
        <van-icon name="volume-o" size="21" />
      </button>
      <textarea
          ref="inputRef"
          v-model="inputText"
          :disabled="loading"
          maxlength="500"
          rows="1"
          aria-label="输入消息"
          placeholder="例如：我想周末在西安找羽毛球搭子..."
          @input="resizeComposerInput"
          @keydown="handleComposerKeydown"
      />
      <button class="chat-composer__send" type="submit" aria-label="发送" :disabled="loading || !inputText.trim()">
        <van-icon name="arrow-up" size="20" />
      </button>
    </form>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue';
import {useRouter} from "vue-router";
import {showFailToast, showSuccessToast} from "vant";
import myAxios from "../plugins/myAxios";
import {
  AiChatHistory,
  AiChatMessage,
  AiChatResponse,
  AiTeamDeleteConfirmation,
  AiTeamDraftConfirmResponse,
  AiToolResult,
  AiUiBlock,
  AiUserProfileData,
  AiUserRecommendation,
  TeamDraft
} from "../models/ai";
import {TeamType} from "../models/team";
import {UserType} from "../models/user";
import {getCurrentUser} from "../services/user";
import {getTeamActivityCategoryName} from "../constants/team";
import AssistantMarkdownContent from "../components/AssistantMarkdownContent.vue";

type ChatMessage = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  time: string;
  response?: AiChatResponse;
};

const router = useRouter();
const streamRef = ref<HTMLElement | null>(null);
const composerRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLTextAreaElement | null>(null);
const inputText = ref('');
const sessionId = ref<string>();
const loading = ref(false);
const composerHeight = ref(76);
const confirmingDraftId = ref<string>();
const confirmedDraftTeams = ref<Record<string, number>>({});
const deletingTeamId = ref<number>();
const deletedTeams = ref<Record<number, boolean>>({});
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

let composerResizeObserver: ResizeObserver | undefined;

onMounted(async () => {
  try {
    currentUser.value = await getCurrentUser();
  } catch (error) {
    console.warn('load current user failed', error);
  }
  await loadChatHistory();
  await nextTick();
  resizeComposerInput();
  if (composerRef.value) {
    composerResizeObserver = new ResizeObserver(syncComposerHeight);
    composerResizeObserver.observe(composerRef.value);
  }
});

onBeforeUnmount(() => {
  composerResizeObserver?.disconnect();
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

const syncComposerHeight = () => {
  composerHeight.value = composerRef.value?.offsetHeight ?? 76;
};

const resizeComposerInput = () => {
  const input = inputRef.value;
  if (!input) {
    syncComposerHeight();
    return;
  }
  input.style.height = 'auto';
  const contentHeight = input.scrollHeight;
  const nextHeight = Math.min(Math.max(contentHeight, 42), 136);
  input.style.height = `${nextHeight}px`;
  input.style.overflowY = contentHeight > 136 ? 'auto' : 'hidden';
  syncComposerHeight();
};

const handleComposerKeydown = (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
    return;
  }
  event.preventDefault();
  void sendMessage();
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
  await nextTick();
  resizeComposerInput();
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

const fillQuestion = async (question: string) => {
  inputText.value = question;
  await nextTick();
  resizeComposerInput();
  inputRef.value?.focus();
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

const CONTENT_UI_BLOCK_TYPES = new Set<AiUiBlock['type']>([
  'team_list',
  'user_recommendations',
  'profile_card',
]);

const getUiBlocks = (response: AiChatResponse): AiUiBlock[] => {
  return response.uiBlocks ?? [];
};

const getContentUiBlocks = (response: AiChatResponse) => {
  return getUiBlocks(response).filter(block => {
    if (!CONTENT_UI_BLOCK_TYPES.has(block.type)) {
      return false;
    }
    if (block.type === 'team_list' || block.type === 'user_recommendations') {
      return Array.isArray(block.data) && block.data.length > 0;
    }
    return true;
  });
};

const findUiBlock = (response: AiChatResponse, type: AiUiBlock['type']) => {
  return getUiBlocks(response).find(block => block.type === type);
};

const getTeamDraft = (response: AiChatResponse): TeamDraft | undefined => {
  const block = findUiBlock(response, 'team_draft_confirmation');
  if (block?.data && typeof block.data === 'object' && !Array.isArray(block.data)) {
    return block.data as TeamDraft;
  }
  return undefined;
};

const getDeleteConfirmation = (response: AiChatResponse): AiTeamDeleteConfirmation | undefined => {
  const block = findUiBlock(response, 'team_delete_confirmation');
  if (block?.data && typeof block.data === 'object' && !Array.isArray(block.data)) {
    return block.data as AiTeamDeleteConfirmation;
  }
  return undefined;
};

const normalizeAssistantReply = (response: AiChatResponse) => {
  const reply = response.reply?.trim();
  if (reply) {
    return reply;
  }
  if (getDeleteConfirmation(response)) {
    return '我找到了要删除的队伍，请确认后再删除。';
  }
  if (getTeamDraft(response)) {
    return '我整理了一份队伍草稿，确认后才会正式创建。';
  }
  if (response.needClarification) {
    return '我还需要你补充一点信息。';
  }
  return '我已经处理好了。';
};

const shouldShowIntentCard = (response: AiChatResponse) => {
  if (!response.intent?.teamRelated) {
    return false;
  }
  const uiBlocks = getUiBlocks(response);
  return Boolean(response.needClarification)
      || uiBlocks.some(block => [
        'team_list',
        'team_draft_confirmation',
        'team_delete_confirmation',
      ].includes(block.type));
};

const getUiBlockTitle = (uiBlock: AiUiBlock) => {
  if (uiBlock.type === 'team_list') {
    return {
      joined: '我加入的队伍',
      created: '我创建的队伍',
      search: '队伍查询',
    }[uiBlock.variant || 'search'] || '队伍列表';
  }
  const titleMap: Record<AiUiBlock['type'], string> = {
    team_list: '队伍列表',
    user_recommendations: '搭子推荐',
    profile_card: '我的资料',
    team_draft_confirmation: '队伍草稿',
    team_delete_confirmation: '删除队伍确认',
  };
  return titleMap[uiBlock.type];
};

const getUiBlockIcon = (uiBlock: AiUiBlock) => {
  if (uiBlock.type === 'team_list' && uiBlock.variant === 'created') {
    return 'cluster-o';
  }
  const iconMap: Record<AiUiBlock['type'], string> = {
    team_list: 'friends-o',
    user_recommendations: 'contact-o',
    profile_card: 'manager-o',
    team_draft_confirmation: 'records-o',
    team_delete_confirmation: 'delete-o',
  };
  return iconMap[uiBlock.type];
};

const getTeams = (uiBlock: AiUiBlock): TeamType[] => {
  return Array.isArray(uiBlock.data) ? uiBlock.data as TeamType[] : [];
};

const getRecommendedUsers = (uiBlock: AiUiBlock): AiUserRecommendation[] => {
  return Array.isArray(uiBlock.data) ? uiBlock.data as AiUserRecommendation[] : [];
};

const getProfileData = (uiBlock: AiUiBlock): AiUserProfileData | undefined => {
  if (!uiBlock.data || Array.isArray(uiBlock.data) || typeof uiBlock.data !== 'object') {
    return undefined;
  }
  return uiBlock.data as AiUserProfileData;
};

const formatUserTagNames = (tagNames?: string[]) => {
  return (tagNames ?? []).filter(Boolean).slice(0, 4);
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
  padding: 16px var(--app-page-x) calc(var(--composer-height, 76px) + var(--van-tabbar-height) + 18px);
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
  right: var(--app-page-x);
  bottom: calc(var(--van-tabbar-height) + env(safe-area-inset-bottom));
  left: var(--app-page-x);
  z-index: 20;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 42px;
  gap: 4px;
  align-items: end;
  padding: 6px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(40, 38, 101, 0.12);
  border-radius: 24px;
  box-shadow: 0 10px 26px rgba(52, 48, 139, 0.13);
  backdrop-filter: blur(18px);
  transition: box-shadow 0.16s ease, border-color 0.16s ease;
}

.chat-composer:focus-within {
  border-color: rgba(var(--app-primary-rgb), 0.32);
  box-shadow: 0 12px 30px rgba(52, 48, 139, 0.16), 0 0 0 3px rgba(var(--app-primary-rgb), 0.07);
}

.chat-composer button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  color: #777894;
  background: transparent;
  border: 0;
  border-radius: 16px;
}

.chat-composer button:disabled {
  opacity: 0.5;
}

.chat-composer textarea {
  min-width: 0;
  min-height: 42px;
  max-height: 136px;
  padding: 10px 4px;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.5;
  background: transparent;
  border: 0;
  border-radius: 14px;
  outline: 0;
  resize: none;
  transition: height 0.16s ease;
}

.chat-composer textarea::placeholder {
  color: #9899ae;
}

.chat-composer__send {
  color: #898aa2 !important;
  background: rgba(66, 65, 113, 0.07) !important;
  border-radius: 50% !important;
  opacity: 1 !important;
  transition: transform 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.chat-composer__send:not(:disabled) {
  color: #ffffff !important;
  background: var(--app-brand-gradient) !important;
  box-shadow: 0 6px 14px rgba(var(--app-primary-rgb), 0.27);
}

.chat-composer__send:not(:disabled):active {
  transform: scale(0.94);
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
