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
        <div class="chat-message__bubble">
          {{ message.content }}
          <span>{{ message.time }}</span>
        </div>
      </article>

      <article class="confirm-card">
        <header>
          <van-icon name="contact-o" size="18" />
          <h2>个人信息更新确认</h2>
        </header>
        <div class="confirm-card__grid">
          <span>昵称：凯瑞</span>
          <span>性别：男</span>
          <span>年级：研二</span>
          <span>兴趣：羽毛球 / 健身</span>
        </div>
        <p><van-icon name="lock" /> 仅在你确认后才会更新</p>
        <div class="confirm-card__actions">
          <van-button round plain>稍后再说</van-button>
          <van-button round type="primary">确认更新</van-button>
        </div>
      </article>

      <article class="team-suggestion">
        <img src="https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=300&q=80" alt="羽毛球场地" />
        <div class="team-suggestion__body">
          <div class="team-suggestion__header">
            <div>
              <h2>羽锋俱乐部 · 周训队</h2>
              <span>由 帝娜 创建</span>
            </div>
            <van-tag round>公开</van-tag>
          </div>
          <div class="team-suggestion__tags">
            <van-tag round>羽毛球</van-tag>
            <van-tag round>5/8 人</van-tag>
            <van-tag round>下周有空位</van-tag>
          </div>
          <p>匹配原因：下周有空位、偏羽毛球、符合你的偏好。</p>
          <div class="team-suggestion__actions">
            <van-button round plain icon="replay">换一批</van-button>
            <van-button round plain>查看详情</van-button>
            <van-button round type="primary">申请加入</van-button>
          </div>
        </div>
      </article>
    </section>

    <form class="chat-composer" @submit.prevent="sendMessage">
      <button type="button" aria-label="语音输入">
        <van-icon name="volume-o" size="21" />
      </button>
      <input v-model="inputText" placeholder="告诉我你的需求，例如：帮我找周末健身搭子..." />
      <button type="button" aria-label="添加">
        <van-icon name="plus" size="21" />
      </button>
      <button class="chat-composer__send" type="submit" aria-label="发送">
        <van-icon name="guide-o" size="23" />
      </button>
    </form>
  </div>
</template>

<script setup lang="ts">
import {nextTick, ref} from 'vue';

type ChatMessage = {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  time: string;
};

const streamRef = ref<HTMLElement | null>(null);
const inputText = ref('');
const messages = ref<ChatMessage[]>([
  {
    id: 1,
    role: 'user',
    content: '我叫凯瑞，男，研二，平时喜欢羽毛球和健身，想完善一下个人资料。',
    time: '10:21',
  },
  {
    id: 2,
    role: 'assistant',
    content: '好的，已为你提取到以下信息，请确认是否更新你的个人资料。',
    time: '10:22',
  },
  {
    id: 3,
    role: 'user',
    content: '帮我找一个下周的羽毛球队伍，不要女队。',
    time: '10:26',
  },
  {
    id: 4,
    role: 'assistant',
    content: '为你找到一个合适的队伍，符合你的时间与偏好。',
    time: '10:26',
  },
]);

const currentTime = () => {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
};

const scrollToBottom = async () => {
  await nextTick();
  if (streamRef.value) {
    streamRef.value.scrollTop = streamRef.value.scrollHeight;
  }
};

const sendMessage = async () => {
  const content = inputText.value.trim();
  if (!content) {
    return;
  }
  messages.value.push({
    id: Date.now(),
    role: 'user',
    content,
    time: currentTime(),
  });
  inputText.value = '';
  await scrollToBottom();
  window.setTimeout(async () => {
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: '收到。真实 AI 接口接入后，我会根据这条需求抽取条件，并返回可确认的搭子或队伍推荐。',
      time: currentTime(),
    });
    await scrollToBottom();
  }, 450);
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
  min-height: 168px;
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
  font-size: 27px;
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

.chat-message__bubble {
  max-width: min(78%, 520px);
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

.confirm-card,
.team-suggestion {
  margin-left: 44px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.confirm-card {
  padding: 14px;
}

.confirm-card header {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.confirm-card h2,
.team-suggestion h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 16px;
  font-weight: 900;
  line-height: 1.25;
  letter-spacing: 0;
}

.confirm-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.confirm-card__grid span {
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

.confirm-card p,
.team-suggestion p {
  margin: 12px 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.confirm-card__actions,
.team-suggestion__actions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.team-suggestion {
  display: flex;
  gap: 12px;
  padding: 12px;
}

.team-suggestion img {
  flex: 0 0 88px;
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: 14px;
}

.team-suggestion__body {
  min-width: 0;
  flex: 1;
}

.team-suggestion__header {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
}

.team-suggestion__header span {
  display: block;
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.team-suggestion__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.team-suggestion__tags :deep(.van-tag) {
  color: var(--app-primary-deep);
  font-weight: 700;
  background: rgba(24, 165, 143, 0.1);
  border: 0;
}

.team-suggestion__actions {
  grid-template-columns: 0.85fr 1fr 1.1fr;
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
</style>
