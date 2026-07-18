<template>
  <div class="user-list">
    <article class="user-card user-card--loading" v-if="props.loading" v-for="item in 4" :key="item">
      <van-skeleton avatar title :row="3" />
    </article>

    <article class="user-card" v-else v-for="user in props.userList" :key="user.id">
      <div class="user-card__avatar">
        <img v-if="user.avatarUrl" :src="user.avatarUrl" :alt="user.username"/>
        <span v-else>{{ getAvatarText(user) }}</span>
      </div>

      <div class="user-card__body">
        <div class="user-card__header">
          <div class="user-card__name-block">
            <h3>{{ user.username || '未命名用户' }}</h3>
            <span>星球编号 {{ user.planetCode || '-' }}</span>
          </div>
          <van-button class="user-card__action" size="small" type="primary" plain>
            联系我
          </van-button>
        </div>

        <p class="user-card__profile">{{ user.profile || '这个人还没有写简介，先从标签了解一下。' }}</p>

        <div class="user-card__tags" v-if="getUserTags(user).length > 0">
          <van-tag
              v-for="tag in getUserTags(user).slice(0, 5)"
              :key="tag"
              class="user-card__tag"
              round
          >
            {{ tag }}
          </van-tag>
        </div>
        <div class="user-card__tags user-card__tags--empty" v-else>
          <van-tag class="user-card__tag" round>未设置标签</van-tag>
        </div>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import {UserType} from "../models/user";
import {parseUserTags} from "../utils/user";

interface UserCardListProps {
  loading: boolean;
  userList: UserType[];
}

const props = withDefaults(defineProps<UserCardListProps>(), {
  loading: true,
  userList: () => [],
});

const getUserTags = (user: UserType) => parseUserTags(user.tags);

const getAvatarText = (user: UserType) => {
  const name = user.username || user.userAccount || '?';
  return name.trim().slice(0, 1).toUpperCase();
};

</script>

<style scoped>
.user-list {
  display: grid;
  gap: 12px;
}

.user-card {
  display: flex;
  gap: 13px;
  padding: 14px;
  overflow: hidden;
  background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(255, 255, 255, 0.9)),
      var(--app-card);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.user-card--loading {
  display: block;
  min-height: 122px;
}

.user-card__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 58px;
  width: 58px;
  height: 58px;
  overflow: hidden;
  color: #ffffff;
  font-size: 21px;
  font-weight: 800;
  background: var(--app-brand-gradient);
  border: 3px solid rgba(255, 255, 255, 0.82);
  border-radius: 18px;
  box-shadow: 0 10px 18px rgba(var(--app-primary-rgb), 0.18);
}

.user-card__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-card__body {
  min-width: 0;
  flex: 1;
}

.user-card__header {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
}

.user-card__name-block {
  min-width: 0;
}

.user-card__name-block h3 {
  max-width: 150px;
  margin: 1px 0 2px;
  overflow: hidden;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.25;
  color: var(--app-text);
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0;
}

.user-card__name-block span {
  display: block;
  overflow: hidden;
  font-size: 12px;
  color: var(--app-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card__action {
  flex: 0 0 auto;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
}

.user-card__profile {
  display: -webkit-box;
  margin: 9px 0 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.55;
  color: #4b4c69;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.user-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 11px;
}

.user-card__tag {
  max-width: 96px;
  padding: 4px 8px;
  overflow: hidden;
  color: var(--app-primary-deep);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: rgba(var(--app-primary-rgb), 0.1);
  border: 0;
}

.user-card__tags--empty .user-card__tag {
  color: var(--app-text-muted);
  background: rgba(109, 111, 139, 0.1);
}
</style>
