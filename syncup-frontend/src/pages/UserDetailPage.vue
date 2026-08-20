<template>
  <div class="user-detail-page">
    <template v-if="user">
      <section class="user-detail-card">
        <div class="user-detail-card__avatar">
          <img v-if="user.avatarUrl" :src="user.avatarUrl" :alt="user.username" />
          <span v-else>{{ avatarText }}</span>
        </div>
        <div class="user-detail-card__body">
          <p>搭子资料</p>
          <h1>{{ user.username || '未命名用户' }}</h1>
          <span v-if="user.city">常驻 {{ user.city }}</span>
        </div>
      </section>

      <section class="user-detail-section">
        <h2>自我介绍</h2>
        <p :class="{ 'user-detail-section__content--empty': !user.profile }">
          {{ user.profile || '这个人还没有写自我介绍。' }}
        </p>
      </section>

      <section class="user-detail-section" v-if="tags.length > 0">
        <h2>兴趣标签</h2>
        <div class="user-detail-tags">
          <van-tag v-for="tag in tags" :key="tag" round>{{ tag }}</van-tag>
        </div>
      </section>
    </template>

    <section v-else-if="loading" class="user-detail-card user-detail-card--loading">
      <van-skeleton avatar title :row="3" />
    </section>

    <van-empty v-else image-size="88" description="暂未找到该用户" />
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import {showFailToast} from 'vant';
import {getPublicUserById} from '../services/user';
import type {UserType} from '../models/user';
import {parseUserTags} from '../utils/user';

const route = useRoute();
const user = ref<UserType | null>(null);
const loading = ref(true);

const avatarText = computed(() => {
  const name = user.value?.username || '?';
  return name.trim().slice(0, 1).toUpperCase();
});

const tags = computed(() => user.value ? parseUserTags(user.value.tags) : []);

onMounted(async () => {
  const userId = Number(route.params.id);
  if (!Number.isSafeInteger(userId) || userId <= 0) {
    loading.value = false;
    return;
  }
  try {
    user.value = await getPublicUserById(userId);
  } catch (error) {
    console.error('/user/:id error', error);
    showFailToast('获取用户资料失败');
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.user-detail-page {
  padding: 14px var(--app-page-x) 0;
}

.user-detail-card {
  display: flex;
  gap: 15px;
  align-items: center;
  min-height: 100px;
  padding: 18px;
  color: #ffffff;
  background: var(--app-brand-gradient);
  border-radius: 24px;
  box-shadow: var(--app-brand-shadow);
}

.user-detail-card--loading {
  display: block;
  color: var(--app-text);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: var(--app-shadow);
}

.user-detail-card__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 68px;
  width: 68px;
  height: 68px;
  overflow: hidden;
  color: var(--app-primary-deep);
  font-size: 26px;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.9);
  border: 3px solid rgba(255, 255, 255, 0.36);
  border-radius: 22px;
}

.user-detail-card__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-detail-card__body {
  min-width: 0;
}

.user-detail-card__body p,
.user-detail-card__body h1,
.user-detail-card__body span {
  display: block;
  margin: 0;
}

.user-detail-card__body p,
.user-detail-card__body span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.78);
}

.user-detail-card__body h1 {
  max-width: 220px;
  margin: 5px 0;
  overflow: hidden;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-detail-section {
  padding: 15px;
  margin-top: 12px;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.user-detail-section h2 {
  margin: 0 0 8px;
  color: var(--app-text);
  font-size: 15px;
  font-weight: 800;
}

.user-detail-section > p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.user-detail-section__content--empty {
  color: var(--app-text-muted);
}

.user-detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.user-detail-tags :deep(.van-tag) {
  padding: 5px 9px;
  color: var(--app-primary-deep);
  font-weight: 700;
  background: rgba(var(--app-primary-rgb), 0.1);
  border: 0;
}
</style>
