<template>
  <div class="app-page">
    <section class="app-panel-heading app-panel-heading--split">
      <div>
        <p>我的队伍</p>
        <h1>我加入的队伍</h1>
        <span>{{ loading ? '正在加载队伍' : `共 ${teamList.length} 个队伍` }}</span>
      </div>
      <van-button size="small" round plain icon="replay" @click="refreshTeamList">
        刷新
      </van-button>
    </section>

    <section class="app-list-toolbar">
      <van-search
          v-model="searchText"
          placeholder="搜索队伍"
          shape="round"
          @search="onSearch"
          @clear="onSearch('')"
      />
    </section>

    <team-card-list :team-list="teamList" :loading="loading" @action-success="refreshTeamList" />
    <van-empty v-if="!loading && teamList.length < 1" image-size="88" description="还没有加入队伍"/>
  </div>
</template>

<script setup lang="ts">

import TeamCardList from "../components/TeamCardList.vue";
import {onMounted, ref} from "vue";
import myAxios from "../plugins/myAxios";
import {showFailToast} from "vant";
import {TeamType} from "../models/team";

const searchText = ref('');
const loading = ref(false);

const teamList = ref<TeamType[]>([]);

/**
 * 搜索队伍
 * @param val
 * @returns {Promise<void>}
 */
const listTeam = async (val = '') => {
  loading.value = true;
  try {
    const res = await myAxios.get<TeamType[]>("/team/list/my/join", {
      params: {
        searchText: val,
        pageNum: 1,
      },
    });
    if (res?.code === 0) {
      teamList.value = res.data ?? [];
    } else {
      teamList.value = [];
      showFailToast('加载队伍失败，请刷新重试');
    }
  } catch (error) {
    console.error('/team/list/my/join error', error);
    teamList.value = [];
    showFailToast('加载队伍失败，请刷新重试');
  } finally {
    loading.value = false;
  }
}


// 页面加载时只触发一次
onMounted( () => {
  refreshTeamList();
})

const refreshTeamList = () => {
  listTeam(searchText.value);
}

const onSearch = (val: string) => {
  listTeam(val);
};

</script>

<style scoped>
</style>
