<template>
  <div class="user-update-page">
    <template v-if="user">
      <section class="user-update-card">
        <div class="user-update-card__avatar">
          <img v-if="user.avatarUrl" :src="user.avatarUrl" :alt="user.username"/>
          <span v-else>{{ avatarText }}</span>
        </div>
        <div>
          <p>个人资料</p>
          <h1>{{ user.username || '未命名用户' }}</h1>
          <span>星球编号 {{ user.planetCode || '-' }}</span>
        </div>
      </section>

      <section class="user-field-group">
        <van-cell title="昵称" is-link :value="user.username || '-'" @click="toEdit('username', '昵称', user.username)"/>
        <van-cell title="账号" :value="user.userAccount || '-'"/>
        <van-cell title="头像" is-link @click="toEdit('avatarUrl', '头像', user.avatarUrl)">
          <template #value>
            <span class="avatar-value">{{ user.avatarUrl ? '已设置' : '未设置' }}</span>
          </template>
        </van-cell>
      </section>

      <section class="user-field-group">
        <van-cell title="性别" is-link :value="getGenderText(user.gender)" @click="toEdit('gender', '性别', user.gender)"/>
        <van-cell title="电话" is-link :value="user.phone || '-'" @click="toEdit('phone', '电话', user.phone)"/>
        <van-cell title="邮箱" is-link :value="user.email || '-'" @click="toEdit('email', '邮箱', user.email)"/>
      </section>

      <section class="user-field-group">
        <van-cell title="注册时间" :value="formatDate(user.createTime)"/>
      </section>
    </template>

    <section v-else-if="loading" class="user-update-card user-update-card--loading">
      <van-skeleton avatar title :row="4"/>
    </section>

    <van-empty v-else image-size="88" description="暂未获取到用户信息"/>
  </div>
</template>

<script setup lang="ts">
import {useRouter} from "vue-router";
import {computed, onMounted, ref} from "vue";
import {getCurrentUser} from "../services/user";
import {UserType} from "../models/user";
import {getGenderText} from "../constants/user";

const user = ref<UserType | null>(null);
const loading = ref(true);

onMounted(async () => {
  try {
    user.value = await getCurrentUser();
  } finally {
    loading.value = false;
  }
})

const router = useRouter();

const avatarText = computed(() => {
  const name = user.value?.username || user.value?.userAccount || '?';
  return name.trim().slice(0, 1).toUpperCase();
});

const formatValue = (value: unknown) => {
  return value === undefined || value === null || value === '' ? '-' : String(value);
}

const getEditValue = (value: unknown) => {
  return value === undefined || value === null ? '' : String(value);
}

const formatDate = (value?: Date | string) => {
  if (!value) {
    return '-';
  }
  const dateText = String(value);
  return dateText.length > 10 ? dateText.slice(0, 10) : dateText;
}

const toEdit = (editKey: string, editName: string, currentValue: unknown) => {
  router.push({
    path: '/user/edit',
    query: {
      editKey,
      editName,
      currentValue: getEditValue(currentValue),
    }
  })
}
</script>

<style scoped>
.user-update-page {
  padding: 14px var(--app-page-x) 0;
}

.user-update-card {
  display: flex;
  gap: 15px;
  align-items: center;
  padding: 18px;
  color: #ffffff;
  background:
      radial-gradient(circle at 88% 18%, rgba(255, 184, 77, 0.34), transparent 7rem),
      linear-gradient(135deg, #0b7d72 0%, #18a58f 58%, #70c69d 100%);
  border-radius: 24px;
  box-shadow: 0 18px 34px rgba(16, 113, 101, 0.22);
}

.user-update-card--loading {
  display: block;
  min-height: 136px;
  color: var(--app-text);
  background: rgba(255, 255, 255, 0.94);
}

.user-update-card__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 66px;
  width: 66px;
  height: 66px;
  overflow: hidden;
  color: var(--app-primary-deep);
  font-size: 25px;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.9);
  border: 3px solid rgba(255, 255, 255, 0.36);
  border-radius: 22px;
}

.user-update-card__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-update-card p {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 800;
  color: rgba(255, 255, 255, 0.76);
}

.user-update-card h1 {
  max-width: 210px;
  margin: 0;
  overflow: hidden;
  font-size: 23px;
  font-weight: 900;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0;
}

.user-update-card span {
  display: block;
  margin-top: 7px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.82);
}

.user-field-group {
  margin-top: 14px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.user-field-group :deep(.van-cell) {
  align-items: center;
  padding: 15px;
}

.avatar-value {
  color: var(--app-text-muted);
}
</style>
