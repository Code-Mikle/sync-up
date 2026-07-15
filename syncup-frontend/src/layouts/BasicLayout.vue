<template>
  <div class="app-shell">
    <van-nav-bar
        class="app-nav"
        :title="title"
        @click-right="onClickRight"
    >
      <template #left>
        <button
            v-if="showAiEntry"
            class="nav-icon-button nav-icon-button--ai"
            type="button"
            aria-label="AI 搭子助手"
            @click="goAIChat"
        >
          <van-icon name="service-o" size="19"/>
        </button>
        <button
            v-else-if="canGoBack"
            class="nav-icon-button"
            type="button"
            aria-label="返回"
            @click="onClickLeft"
        >
          <van-icon name="arrow-left" size="20"/>
        </button>
      </template>

      <template #right>
        <button class="nav-icon-button" type="button" aria-label="搜索" @click="onClickRight">
          <van-icon name="search" size="19"/>
        </button>
      </template>
    </van-nav-bar>

    <main class="app-content">
      <router-view/>
    </main>

    <van-tabbar class="app-tabbar" route @change="onChange">
      <van-tabbar-item to="/" icon="home-o" name="index">主页</van-tabbar-item>
      <van-tabbar-item to="/team" icon="friends-o" name="team">队伍</van-tabbar-item>
      <van-tabbar-item to="/user" icon="manager-o" name="user">个人</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {useRoute, useRouter} from "vue-router";
import routes from "../config/route";

const router = useRouter();
const route = useRoute();
const DEFAULT_TITLE = '搭子星球';

const title = computed(() => {
  const matchedRoute = routes.find((item) => item.path === route.path);
  return matchedRoute?.title ?? DEFAULT_TITLE;
});

const canGoBack = computed(() => route.path !== '/');
const showAiEntry = computed(() => route.path === '/');

const onClickLeft = () => {
  if (canGoBack.value) {
    router.back();
  }
};

const onClickRight = () => {
  router.push('/search');
};

const goAIChat = () => {
  router.push('/ai/chat');
};

const onChange = () => {};
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: transparent;
}

.app-nav {
  position: sticky;
  top: 0;
  z-index: 30;
  overflow: hidden;
  border-bottom: 1px solid rgba(28, 61, 58, 0.06);
  backdrop-filter: blur(16px);
}

.app-nav :deep(.van-nav-bar__title) {
  font-size: 17px;
  font-weight: 700;
}

.app-nav :deep(.van-icon) {
  color: var(--app-primary-deep);
}

.nav-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--app-primary-deep);
  cursor: pointer;
  background: rgba(24, 165, 143, 0.1);
  border: 1px solid rgba(24, 165, 143, 0.1);
  border-radius: 50%;
}

.nav-icon-button--ai {
  background:
      radial-gradient(circle at 70% 22%, rgba(255, 232, 186, 0.9), transparent 0.7rem),
      rgba(24, 165, 143, 0.12);
}

.app-content {
  min-height: calc(100vh - var(--van-nav-bar-height) - var(--van-tabbar-height));
  padding-bottom: calc(var(--van-tabbar-height) + 18px + env(safe-area-inset-bottom));
}

.app-tabbar {
  overflow: hidden;
  border-top: 1px solid rgba(28, 61, 58, 0.06);
  box-shadow: 0 -10px 26px rgba(19, 69, 61, 0.08);
  backdrop-filter: blur(16px);
}

.app-tabbar :deep(.van-tabbar-item) {
  font-weight: 600;
}

.app-tabbar :deep(.van-tabbar-item__icon) {
  margin-bottom: 3px;
}
</style>
