<template>
  <div class="profile-page">
    <template v-if="user">
      <section class="profile-card">
        <div class="profile-card__avatar">
          <img v-if="user.avatarUrl" :src="user.avatarUrl" :alt="user.username"/>
          <span v-else>{{ avatarText }}</span>
        </div>
        <div class="profile-card__body">
          <p class="profile-card__eyebrow">用户昵称</p>
          <h1>{{ user.username || '未命名用户' }}</h1>
          <p>账号 {{ user.userAccount || '-' }}</p>
        </div>
      </section>

      <section class="profile-stats">
        <div>
          <strong>{{ user.planetCode || '-' }}</strong>
          <span>星球编号</span>
        </div>
        <div>
          <strong>{{ getGenderText(user.gender) }}</strong>
          <span>性别</span>
        </div>
      </section>

      <section class="profile-menu">
        <van-cell title="修改信息" label="昵称、头像、联系方式" is-link to="/user/update">
          <template #icon>
            <van-icon class="profile-menu__icon" name="edit"/>
          </template>
        </van-cell>
        <van-cell title="我创建的队伍" label="管理自己发起的队伍" is-link to="/user/team/create">
          <template #icon>
            <van-icon class="profile-menu__icon" name="cluster-o"/>
          </template>
        </van-cell>
        <van-cell title="我加入的队伍" label="查看已经加入的队伍" is-link to="/user/team/join">
          <template #icon>
            <van-icon class="profile-menu__icon" name="friends-o"/>
          </template>
        </van-cell>
      </section>

      <div class="profile-actions">
        <van-button block round plain type="danger" :loading="loggingOut" @click="confirmLogout">
          退出登录
        </van-button>
      </div>
    </template>

    <template v-else-if="loading">
      <section class="profile-card profile-card--loading">
        <van-skeleton avatar title :row="3"/>
      </section>
    </template>

    <van-empty v-else image-size="88" description="暂未获取到用户信息"/>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {showConfirmDialog, showFailToast, showSuccessToast} from "vant";
import {getCurrentUser, logout} from "../services/user";
import {UserType} from "../models/user";
import {getGenderText} from "../constants/user";

const user = ref<UserType | null>(null);
const loading = ref(true);
const loggingOut = ref(false);
const router = useRouter();

const avatarText = computed(() => {
  const name = user.value?.username || user.value?.userAccount || '?';
  return name.trim().slice(0, 1).toUpperCase();
});

onMounted(async () => {
  try {
    user.value = await getCurrentUser();
  } finally {
    loading.value = false;
  }
})

const confirmLogout = async () => {
  try {
    await showConfirmDialog({
      title: '确认退出登录',
      message: '退出后需要重新登录才能继续使用账号相关功能。',
    });
  } catch (error) {
    return;
  }

  loggingOut.value = true;
  try {
    await logout();
    showSuccessToast('已退出登录');
    router.replace('/user/login');
  } catch (error) {
    console.error('/user/logout error', error);
    showFailToast('退出登录失败');
    router.replace('/user/login');
  } finally {
    loggingOut.value = false;
  }
}
</script>

<style scoped>
.profile-page {
  padding: 14px var(--app-page-x) 0;
}

.profile-card {
  display: flex;
  gap: 15px;
  align-items: center;
  padding: 18px;
  overflow: hidden;
  color: #ffffff;
  background:
      radial-gradient(circle at 86% 18%, rgba(var(--app-accent-rgb), 0.36), transparent 7rem),
      var(--app-brand-gradient);
  border-radius: 24px;
  box-shadow: var(--app-brand-shadow);
}

.profile-card--loading {
  display: block;
  min-height: 132px;
  color: var(--app-text);
  background: rgba(255, 255, 255, 0.94);
}

.profile-card__avatar {
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

.profile-card__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-card__body {
  min-width: 0;
}

.profile-card__eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.76);
}

.profile-card h1 {
  max-width: 210px;
  margin: 0;
  overflow: hidden;
  font-size: 24px;
  font-weight: 900;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0;
}

.profile-card p:last-child {
  margin: 7px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.82);
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.profile-stats div {
  min-width: 0;
  padding: 13px;
  text-align: center;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--app-border);
  border-radius: 16px;
  box-shadow: var(--app-shadow);
}

.profile-stats strong,
.profile-stats span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-stats strong {
  font-size: 17px;
  font-weight: 900;
  color: var(--app-text);
}

.profile-stats span {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-muted);
}

.profile-menu {
  margin-top: 14px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.profile-menu :deep(.van-cell) {
  align-items: center;
  padding: 15px;
}

.profile-menu__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin-right: 10px;
  color: var(--app-primary-deep);
  background: rgba(var(--app-primary-rgb), 0.1);
  border-radius: 10px;
}

.profile-actions {
  margin-top: 18px;
}

.profile-actions :deep(.van-button) {
  height: 42px;
  background: rgba(255, 255, 255, 0.94);
}
</style>
