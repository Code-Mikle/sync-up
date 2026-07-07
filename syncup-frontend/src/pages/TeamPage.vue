<template>
  <div class="team-page">
    <section class="team-hero">
      <div>
        <p class="team-hero__eyebrow">组队大厅</p>
        <h1>找到一起行动的队伍</h1>
        <p class="team-hero__desc">按状态筛选队伍，公开队伍可直接加入，加密队伍需要口令。</p>
      </div>
      <van-button class="team-hero__button" type="primary" icon="plus" round @click="toAddTeam">
        创建
      </van-button>
    </section>

    <section class="team-toolbar">
      <van-search
          v-model="searchText"
          placeholder="搜索队伍名称或描述"
          shape="round"
          @search="onSearch"
          @clear="onSearch('')"
      />
      <van-tabs v-model:active="active" class="team-tabs" @change="onTabChange">
        <van-tab title="公开队伍" name="public" />
        <van-tab title="加密队伍" name="private" />
      </van-tabs>
    </section>

    <section class="team-summary">
      <div>
        <h2>{{ active === 'public' ? '公开队伍' : '加密队伍' }}</h2>
        <p>{{ loading ? '正在加载队伍' : `共 ${teamList.length} 个队伍` }}</p>
      </div>
      <van-button size="small" plain round icon="replay" @click="refreshTeamList">
        刷新
      </van-button>
    </section>

    <team-card-list
        :team-list="teamList"
        :loading="loading"
        @action-success="refreshTeamList"
    />
    <van-empty
        v-if="!loading && teamList.length < 1"
        image-size="88"
        description="暂时没有找到队伍"
    />

    <van-button class="add-button" type="primary" icon="plus" @click="toAddTeam" />
  </div>
</template>

<script setup lang="ts">

import {useRouter} from "vue-router";
import TeamCardList from "../components/TeamCardList.vue";
import {computed, onMounted, ref} from "vue";
import myAxios from "../plugins/myAxios";
import {showFailToast} from "vant";
import {TeamType} from "../models/team";

const active = ref('public')
const router = useRouter();
const searchText = ref('');
const loading = ref(false);

const currentStatus = computed(() => active.value === 'public' ? 0 : 2);

/**
 * 切换查询状态
 */
const onTabChange = () => {
  listTeam(searchText.value, currentStatus.value);
}

// 跳转到创建队伍页
const toAddTeam = () => {
  router.push({
    path: "/team/add"
  })
}

const teamList = ref<TeamType[]>([]);

/**
 * 搜索队伍
 */
const listTeam = async (val = '', status = 0) => {
  loading.value = true;
  try {
    const res = await myAxios.get<TeamType[]>("/team/list", {
      params: {
        searchText: val,
        pageNum: 1,
        status,
      },
    });
    if (res?.code === 0) {
      teamList.value = res.data ?? [];
    } else {
      teamList.value = [];
      showFailToast('加载队伍失败，请刷新重试');
    }
  } catch (error) {
    console.error('/team/list error', error);
    teamList.value = [];
    showFailToast('加载队伍失败，请刷新重试');
  } finally {
    loading.value = false;
  }
}

const refreshTeamList = () => {
  listTeam(searchText.value, currentStatus.value);
}

// 页面加载时只触发一次
onMounted( () => {
  refreshTeamList();
})

const onSearch = (val: string) => {
  listTeam(val, currentStatus.value);
};

</script>

<style scoped>
.team-page {
  padding: 14px var(--app-page-x) 0;
}

.team-hero {
  display: flex;
  gap: 14px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 18px;
  color: var(--app-text);
  background:
      radial-gradient(circle at right top, rgba(255, 184, 77, 0.2), transparent 8rem),
      linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(238, 247, 242, 0.94));
  border: 1px solid var(--app-border);
  border-radius: 22px;
  box-shadow: var(--app-shadow);
}

.team-hero__eyebrow {
  margin: 0 0 7px;
  font-size: 12px;
  font-weight: 800;
  color: var(--app-primary-deep);
}

.team-hero h1 {
  max-width: 210px;
  margin: 0;
  font-size: 23px;
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: 0;
}

.team-hero__desc {
  max-width: 230px;
  margin: 9px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--app-text-muted);
}

.team-hero__button {
  flex: 0 0 auto;
  height: 38px;
  padding: 0 15px;
  box-shadow: 0 10px 20px rgba(24, 165, 143, 0.18);
}

.team-toolbar {
  margin-top: 12px;
  padding: 4px 0 0;
  background: rgba(255, 255, 255, 0.52);
  border: 1px solid rgba(28, 61, 58, 0.05);
  border-radius: 18px;
}

.team-toolbar :deep(.van-search) {
  padding: 8px;
}

.team-tabs {
  padding: 0 8px 4px;
}

.team-tabs :deep(.van-tabs__wrap) {
  height: 42px;
}

.team-tabs :deep(.van-tab) {
  font-weight: 700;
  color: var(--app-text-muted);
}

.team-tabs :deep(.van-tab--active) {
  color: var(--app-primary-deep);
}

.team-summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 20px 2px 10px;
}

.team-summary h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: 0;
}

.team-summary p {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
}
</style>
