<template>
  <div class="app-page">
    <section class="app-panel-heading app-panel-heading--split">
      <div>
        <p>搜索结果</p>
        <h1>匹配到的搭子</h1>
        <span>{{ loading ? '正在搜索' : `共 ${total} 人` }}</span>
      </div>
      <van-button size="small" round plain icon="replay" :disabled="loading" @click="loadData">
        刷新
      </van-button>
    </section>

    <div class="app-tags result-tags" v-if="selectedTags.length > 0">
      <van-tag v-for="tag in selectedTags" :key="tag" round class="app-tag">
        {{ tag }}
      </van-tag>
    </div>

    <van-list
        v-model:loading="loadingMore"
        :finished="finished"
        :immediate-check="false"
        finished-text="没有更多结果了"
        @load="loadMore"
    >
      <user-card-list :user-list="userList" :loading="loading" />
    </van-list>
    <van-empty v-if="!loading && userList.length < 1" image-size="88" description="搜索结果为空" />
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {useRoute} from "vue-router";
import myAxios from "../plugins/myAxios";
import {showFailToast} from "vant";
import qs from 'qs';
import UserCardList from "../components/UserCardList.vue";
import {UserType} from "../models/user";
import {normalizeUserList} from "../utils/user";

const route = useRoute();
const {tagIds, tagNames} = route.query;

const userList = ref<UserType[]>([]);
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(true);
const nextPageNum = ref(1);
const total = ref(0);

const PAGE_SIZE = 5;

type UserPageResponse = {
  records: UserType[];
  total: number;
  current: number;
  size: number;
};

const selectedTags = computed(() => {
  if (Array.isArray(tagNames)) {
    return tagNames.map(String);
  }
  return tagNames ? [String(tagNames)] : [];
});

const fetchPage = async (pageNum: number) => {
  const response = await myAxios.get<UserPageResponse>('/user/search/tags', {
    params: {
      tagIds,
      pageNum,
      pageSize: PAGE_SIZE,
    },
    paramsSerializer: params => qs.stringify(params, {indices: false}),
  });
  return response?.data;
};

const appendRecords = (records: UserType[]) => {
  const existingIds = new Set(userList.value.map(user => user.id));
  const nextRecords = normalizeUserList(records)
      .filter(user => !existingIds.has(user.id));
  userList.value = [...userList.value, ...nextRecords];
};

const updateFinished = (recordCount: number) => {
  finished.value = recordCount < PAGE_SIZE || userList.value.length >= total.value;
};

const loadData = async () => {
  loading.value = true;
  loadingMore.value = false;
  finished.value = false;
  nextPageNum.value = 1;
  total.value = 0;
  userList.value = [];
  try {
    const page = await fetchPage(nextPageNum.value);
    const records = page?.records ?? [];
    total.value = page?.total ?? 0;
    appendRecords(records);
    nextPageNum.value += 1;
    updateFinished(records.length);
    console.log('/user/search/tags succeed', page);
  } catch (error) {
    console.error('/user/search/tags error', error);
    userList.value = [];
    showFailToast('请求失败');
  } finally {
    loading.value = false;
  }
}

const loadMore = async () => {
  if (loading.value || finished.value) {
    loadingMore.value = false;
    return;
  }
  loadingMore.value = true;
  try {
    const page = await fetchPage(nextPageNum.value);
    const records = page?.records ?? [];
    total.value = page?.total ?? total.value;
    appendRecords(records);
    nextPageNum.value += 1;
    updateFinished(records.length);
  } catch (error) {
    console.error('/user/search/tags load more error', error);
    showFailToast('加载失败');
  } finally {
    loadingMore.value = false;
  }
};

onMounted(loadData)



</script>

<style scoped>
.result-tags {
  margin: 14px 0 12px;
}
</style>
