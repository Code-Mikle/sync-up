<template>
  <div class="app-shell">
    <van-nav-bar
        class="app-nav"
        :title="title"
        :left-arrow="canGoBack"
        @click-left="onClickLeft"
        @click-right="onClickRight"
    >
      <template #right>
        <button class="nav-search-button" type="button" aria-label="搜索">
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

const onClickLeft = () => {
  if (canGoBack.value) {
    router.back();
  }
};

const onClickRight = () => {
  router.push('/search');
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

.nav-search-button {
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
