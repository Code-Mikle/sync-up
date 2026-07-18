<template>
  <div class="team-list">
    <article class="team-card team-card--loading" v-if="props.loading" v-for="item in 3" :key="item">
      <van-skeleton title :row="4" />
    </article>

    <article class="team-card" v-else v-for="team in props.teamList" :key="team.id">
      <div class="team-card__cover">
        <img :src="ikun" alt="队伍封面"/>
      </div>

      <div class="team-card__body">
        <div class="team-card__header">
          <div class="team-card__title-block">
            <h3>{{ team.name || '未命名队伍' }}</h3>
            <span>{{ getCreatorName(team) }}</span>
          </div>
          <van-tag class="team-card__status" :class="getStatusClass(team.status)" round>
            {{ teamStatusEnum[team.status] || '未知' }}
          </van-tag>
        </div>

        <p class="team-card__desc">{{ team.description || '这个队伍暂时还没有描述。' }}</p>

        <div class="team-card__chips" v-if="hasStructuredInfo(team)">
          <van-tag v-if="team.activityType" plain round>{{ team.activityType }}</van-tag>
          <van-tag v-if="team.city || team.district" plain round>{{ formatLocation(team) }}</van-tag>
          <van-tag v-if="team.skillLevel" plain round>{{ team.skillLevel }}</van-tag>
        </div>

        <div class="team-card__meta">
          <div class="team-card__meta-item">
            <strong>{{ team.hasJoinNum ?? 0 }}/{{ team.maxNum }}</strong>
            <span>人数</span>
          </div>
          <div class="team-card__meta-item">
            <strong>{{ formatDate(team.startTime) }}</strong>
            <span>活动</span>
          </div>
          <div class="team-card__meta-item">
            <strong>{{ formatBudget(team.budgetPerPerson) }}</strong>
            <span>预算</span>
          </div>
        </div>

        <div class="team-card__actions">
          <van-button
              v-if="team.userId !== currentUser?.id && !team.hasJoin"
              class="team-card__button"
              size="small"
              type="primary"
              round
              @click="preJoinTeam(team)"
          >
            加入队伍
          </van-button>
          <van-button
              v-if="team.userId === currentUser?.id"
              class="team-card__button"
              size="small"
              plain
              round
              @click="doUpdateTeam(team.id)"
          >
            更新
          </van-button>
          <van-button
              v-if="team.userId !== currentUser?.id && team.hasJoin"
              class="team-card__button"
              size="small"
              plain
              round
              @click="doQuitTeam(team.id)"
          >
            退出
          </van-button>
          <van-button
              v-if="team.userId === currentUser?.id"
              class="team-card__button"
              size="small"
              type="danger"
              plain
              round
              @click="doDeleteTeam(team.id)"
          >
            解散
          </van-button>
        </div>
      </div>
    </article>

    <van-dialog
        v-model:show="showPasswordDialog"
        title="请输入队伍密码"
        show-cancel-button
        @confirm="doJoinTeam"
        @cancel="doJoinCancel"
    >
      <van-field v-model="password" type="password" placeholder="请输入密码"/>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import {TeamType} from "../models/team";
import {teamStatusEnum} from "../constants/team";
import ikun from "../assets/ikun.png";
import myAxios from "../plugins/myAxios";
import {showConfirmDialog, showFailToast, showSuccessToast} from "vant";
import {onMounted, ref} from "vue";
import {getCurrentUser} from "../services/user";
import {useRouter} from "vue-router";

interface TeamCardListProps {
  loading?: boolean;
  teamList: TeamType[];
}

const props = withDefaults(defineProps<TeamCardListProps>(), {
  loading: false,
  teamList: () => [],
});

const emit = defineEmits<{
  (event: "action-success"): void;
}>();

const showPasswordDialog = ref(false);
const password = ref("");
const joinTeamId = ref(0);
const currentUser = ref();
const router = useRouter();

onMounted(async () => {
  currentUser.value = await getCurrentUser();
});

const getCreatorName = (team: TeamType) => {
  return team.createUser?.username ? `由 ${team.createUser.username} 创建` : "等待更多伙伴加入";
};

const getStatusClass = (status: number) => {
  return {
    "team-card__status--public": status === 0,
    "team-card__status--private": status === 1,
    "team-card__status--locked": status === 2,
  };
};

const formatDate = (value?: Date | string) => {
  if (!value) {
    return "未定";
  }
  const dateText = String(value);
  return dateText.length > 10 ? dateText.slice(0, 10) : dateText;
};

const hasStructuredInfo = (team: TeamType) => {
  return Boolean(team.activityType || team.city || team.district || team.skillLevel);
};

const formatLocation = (team: TeamType) => {
  return [team.city, team.district].filter(Boolean).join("·");
};

const formatBudget = (value?: number) => {
  return value === undefined || value === null ? "未定" : `${value}元`;
};

const preJoinTeam = (team: TeamType) => {
  joinTeamId.value = team.id;
  if (team.status === 0) {
    doJoinTeam();
  } else {
    showPasswordDialog.value = true;
  }
};

const doJoinCancel = () => {
  joinTeamId.value = 0;
  password.value = "";
};

const doJoinTeam = async () => {
  if (!joinTeamId.value) {
    return;
  }
  const res = await myAxios.post("/team/join", {
    teamId: joinTeamId.value,
    password: password.value,
  });
  if (res?.code === 0) {
    showSuccessToast("加入成功");
    doJoinCancel();
    emit("action-success");
  } else {
    showFailToast("加入失败" + (res.description ? `：${res.description}` : ""));
  }
};

const doUpdateTeam = (id: number) => {
  router.push({
    path: "/team/update",
    query: {id},
  });
};

const doQuitTeam = async (id: number) => {
  const res = await myAxios.post("/team/quit", {
    teamId: id,
  });
  if (res?.code === 0) {
    showSuccessToast("操作成功");
    emit("action-success");
  } else {
    showFailToast("操作失败" + (res.description ? `：${res.description}` : ""));
  }
};

const doDeleteTeam = async (id: number) => {
  try {
    await showConfirmDialog({
      title: "确认解散队伍",
      message: "解散后队伍成员将无法继续加入，确定要继续吗？",
    });
  } catch (error) {
    return;
  }

  const res = await myAxios.post("/team/delete", {
    id,
  });
  if (res?.code === 0) {
    showSuccessToast("操作成功");
    emit("action-success");
  } else {
    showFailToast("操作失败" + (res.description ? `：${res.description}` : ""));
  }
};
</script>

<style scoped>
.team-list {
  display: grid;
  gap: 12px;
}

.team-card {
  display: flex;
  gap: 13px;
  padding: 13px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.team-card--loading {
  display: block;
  min-height: 144px;
}

.team-card__cover {
  position: relative;
  flex: 0 0 72px;
  width: 72px;
  height: 88px;
  overflow: hidden;
  background:
      radial-gradient(circle at 35% 20%, rgba(var(--app-accent-rgb), 0.32), transparent 3rem),
      linear-gradient(145deg, rgba(var(--app-secondary-rgb), 0.2), rgba(var(--app-primary-rgb), 0.09));
  border-radius: 16px;
}

.team-card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.team-card__body {
  min-width: 0;
  flex: 1;
}

.team-card__header {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
}

.team-card__title-block {
  min-width: 0;
}

.team-card__title-block h3 {
  max-width: 150px;
  margin: 1px 0 3px;
  overflow: hidden;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.25;
  color: var(--app-text);
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: 0;
}

.team-card__title-block span {
  display: block;
  overflow: hidden;
  font-size: 12px;
  color: var(--app-text-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-card__status {
  flex: 0 0 auto;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
  border: 0;
}

.team-card__status--public {
  color: var(--app-primary-deep);
  background: rgba(var(--app-primary-rgb), 0.1);
}

.team-card__status--private {
  color: #7b5b18;
  background: rgba(var(--app-accent-rgb), 0.18);
}

.team-card__status--locked {
  color: #6b4b12;
  background: rgba(var(--app-accent-rgb), 0.22);
}

.team-card__desc {
  display: -webkit-box;
  margin: 9px 0 0;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.55;
  color: #4b4c69;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.team-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.team-card__chips :deep(.van-tag) {
  max-width: 100%;
  color: var(--app-primary-deep);
  background: rgba(var(--app-primary-rgb), 0.06);
}

.team-card__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
  margin-top: 11px;
}

.team-card__meta-item {
  min-width: 0;
  padding: 7px 6px;
  background: rgba(244, 245, 252, 0.88);
  border: 1px solid rgba(40, 38, 101, 0.05);
  border-radius: 12px;
}

.team-card__meta-item strong,
.team-card__meta-item span {
  display: block;
  overflow: hidden;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.team-card__meta-item strong {
  font-size: 12px;
  font-weight: 800;
  color: var(--app-text);
}

.team-card__meta-item span {
  margin-top: 2px;
  font-size: 11px;
  color: var(--app-text-muted);
}

.team-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 12px;
}

.team-card__button {
  height: 30px;
  padding: 0 12px;
  font-weight: 700;
}
</style>
