<template>
  <div class="home-page">
    <section class="match-panel">
      <div class="match-panel__content">
        <div class="match-panel__eyebrow">
          <van-icon name="like-o"/>
          <span>今日推荐</span>
        </div>
        <h1 class="match-panel__title">{{ modeTitle }}</h1>
        <p class="match-panel__desc">{{ modeDescription }}</p>
      </div>
      <div class="match-panel__switch">
        <span>{{ isMatchMode ? '匹配中' : '普通' }}</span>
        <van-switch v-model="isMatchMode" size="25" />
      </div>
    </section>

    <section class="section-heading">
      <div>
        <h2>{{ isMatchMode ? '心动搭子' : '推荐搭子' }}</h2>
        <p>{{ isMatchMode ? '根据你的标签偏好筛选' : '先认识一些活跃用户' }}</p>
      </div>
      <span class="section-heading__count" v-if="!loading">{{ userList.length }} 人</span>
    </section>

    <user-card-list :user-list="userList" :loading="loading"/>
    <van-empty
        v-if="!loading && userList.length < 1"
        image-size="88"
        description="暂时没有找到合适的搭子"
    />
  </div>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import myAxios from "../plugins/myAxios";
import {showFailToast} from "vant";
import UserCardList from "../components/UserCardList.vue";
import {UserType} from "../models/user";
import {normalizeUserList} from "../utils/user";

const isMatchMode = ref<boolean>(false);
const userList = ref<UserType[]>([]);
const loading = ref(true);

const modeTitle = computed(() => isMatchMode.value ? '开启心动匹配' : '发现新的搭子');
const modeDescription = computed(() => (
    isMatchMode.value
        ? '系统会优先推荐标签更接近的用户，适合快速找到同频的人。'
        : '浏览最近活跃的用户，先从一个简单的招呼开始。'
));

/**
 * 加载数据
 */
const loadData = async () => {
  loading.value = true;
  try {
    let userListData: UserType[] = [];
    // 心动模式，根据标签匹配用户
    if (isMatchMode.value) {
      const num = 10;
      const response = await myAxios.get<UserType[]>('/user/match', {
        params: {
          num,
        },
      });
      console.log('/user/match succeed', response);
      userListData = response?.data ?? [];
    } else {
      // 普通模式，直接分页查询用户
      const response = await myAxios.get<{ records: UserType[] }>('/user/recommend', {
        params: {
          pageSize: 8,
          pageNum: 1,
        },
      });
      console.log('/user/recommend succeed', response);
      userListData = response?.data?.records ?? [];
    }
    userList.value = normalizeUserList(userListData);
  } catch (error) {
    console.error('/user list error', error);
    userList.value = [];
    showFailToast('请求失败');
  } finally {
    loading.value = false;
  }
}

watch(isMatchMode, loadData, {immediate: true});

</script>

<style scoped>
.home-page {
  padding: 14px var(--app-page-x) 0;
}

.match-panel {
  position: relative;
  display: flex;
  gap: 14px;
  align-items: flex-end;
  justify-content: space-between;
  min-height: 148px;
  padding: 20px;
  overflow: hidden;
  color: #ffffff;
  background:
      radial-gradient(circle at 86% 16%, rgba(var(--app-accent-rgb), 0.38), transparent 7rem),
      var(--app-brand-gradient);
  border-radius: 22px;
  box-shadow: var(--app-brand-shadow);
}

.match-panel::after {
  position: absolute;
  right: -42px;
  bottom: -64px;
  width: 150px;
  height: 150px;
  content: "";
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 50%;
  transform: rotate(-18deg) scaleY(0.46);
}

.match-panel__content,
.match-panel__switch {
  position: relative;
  z-index: 1;
}

.match-panel__eyebrow {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 5px 9px;
  margin-bottom: 14px;
  font-size: 12px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.88);
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
}

.match-panel__title {
  margin: 0;
  font-size: 25px;
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: 0;
}

.match-panel__desc {
  max-width: 220px;
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: rgba(255, 255, 255, 0.82);
}

.match-panel__switch {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  flex: 0 0 auto;
  padding: 10px;
  font-size: 12px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 16px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 22px 2px 10px;
}

.section-heading h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 800;
  line-height: 1.2;
  color: var(--app-text);
  letter-spacing: 0;
}

.section-heading p {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
}

.section-heading__count {
  flex: 0 0 auto;
  padding: 4px 9px;
  font-size: 12px;
  font-weight: 700;
  color: var(--app-primary-deep);
  background: rgba(var(--app-primary-rgb), 0.1);
  border-radius: 999px;
}
</style>
