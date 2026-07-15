<template>
  <div class="search-page">
    <section class="search-head">
      <van-search
          v-model="searchText"
          show-action
          placeholder="输入关键词，例如：羽毛球 研二 健身"
          shape="round"
          clearable
          @search="resetAndSearch"
          @clear="clearSearch"
      >
        <template #action>
          <button class="search-head__action" type="button" @click="resetAndSearch">搜索</button>
        </template>
      </van-search>

      <div class="search-keywords" v-if="keywords.length > 0">
        <van-tag
            v-for="keyword in keywords"
            :key="keyword"
            round
            class="search-keywords__tag"
        >
          {{ keyword }}
        </van-tag>
      </div>
    </section>

    <section class="search-summary" v-if="hasSearched">
      <div>
        <h1>{{ loading && userList.length === 0 ? '正在搜索' : '搜索结果' }}</h1>
        <p>{{ resultSummary }}</p>
      </div>
      <van-button size="small" round plain icon="replay" :disabled="keywords.length === 0" @click="resetAndSearch">
        刷新
      </van-button>
    </section>

    <van-list
        v-if="hasSearched"
        v-model:loading="loadingMore"
        :finished="finished"
        :immediate-check="false"
        finished-text="没有更多结果了"
        @load="loadMore"
    >
      <user-card-list :user-list="userList" :loading="loading && userList.length === 0" />
    </van-list>

    <van-empty
        v-if="hasSearched && !loading && userList.length < 1"
        image-size="88"
        description="没有找到匹配的搭子"
    />

    <section class="search-empty-guide" v-if="!hasSearched">
      <van-icon name="search" size="30" />
      <h1>搜你想找的搭子</h1>
      <p>可以输入多个关键词，中间用空格隔开。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import qs from 'qs';
import myAxios from "../plugins/myAxios";
import UserCardList from "../components/UserCardList.vue";
import {UserType} from "../models/user";
import {normalizeUserList} from "../utils/user";
import {showFailToast} from "vant";

type UserPageResponse = {
  records: UserType[];
  total: number;
  current: number;
  size: number;
};

const FIRST_PAGE_SIZE = 5;
const MAX_VISIBLE_USERS = 30;

const searchText = ref('');
const userList = ref<UserType[]>([]);
const hasSearched = ref(false);
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(true);
const nextPageNum = ref(1);
const total = ref(0);
let debounceTimer: number | undefined;

const keywords = computed(() => splitKeywords(searchText.value));

const resultSummary = computed(() => {
  if (keywords.value.length === 0) {
    return '输入关键词后开始搜索';
  }
  if (loading.value && userList.value.length === 0) {
    return `正在查找和「${keywords.value.join('、')}」相关的用户`;
  }
  return `已展示 ${userList.value.length} 人，最多展示 ${MAX_VISIBLE_USERS} 人`;
});

const splitKeywords = (value: string) => {
  return value
      .trim()
      .split(/\s+/)
      .map(item => item.trim())
      .filter(Boolean)
      .slice(0, 5);
};

const clearSearch = () => {
  searchText.value = '';
  userList.value = [];
  hasSearched.value = false;
  loading.value = false;
  loadingMore.value = false;
  finished.value = true;
  nextPageNum.value = 1;
  total.value = 0;
};

const fetchPage = async (pageNum: number) => {
  const response = await myAxios.get<UserPageResponse>('/user/search/keywords', {
    params: {
      keywords: keywords.value,
      pageNum,
      pageSize: FIRST_PAGE_SIZE,
    },
    paramsSerializer: params => qs.stringify(params, {indices: false}),
  });
  return response?.data;
};

const appendRecords = (records: UserType[]) => {
  const existingIds = new Set(userList.value.map(user => user.id));
  const nextRecords = normalizeUserList(records)
      .filter(user => !existingIds.has(user.id));
  userList.value = [...userList.value, ...nextRecords].slice(0, MAX_VISIBLE_USERS);
};

const updateFinished = () => {
  finished.value = keywords.value.length === 0
      || userList.value.length >= MAX_VISIBLE_USERS
      || (total.value > 0 && userList.value.length >= total.value);
};

const resetAndSearch = async () => {
  window.clearTimeout(debounceTimer);
  if (keywords.value.length === 0) {
    clearSearch();
    return;
  }
  hasSearched.value = true;
  loading.value = true;
  loadingMore.value = false;
  finished.value = false;
  userList.value = [];
  nextPageNum.value = 1;
  total.value = 0;
  try {
    const page = await fetchPage(nextPageNum.value);
    total.value = page?.total ?? 0;
    appendRecords(page?.records ?? []);
    nextPageNum.value += 1;
    updateFinished();
  } catch (error) {
    console.error('/user/search/keywords error', error);
    showFailToast('搜索失败');
    finished.value = true;
  } finally {
    loading.value = false;
  }
};

const loadMore = async () => {
  if (loading.value || finished.value || keywords.value.length === 0) {
    loadingMore.value = false;
    return;
  }
  loadingMore.value = true;
  try {
    for (let i = 0; i < 2 && !finished.value; i += 1) {
      const page = await fetchPage(nextPageNum.value);
      total.value = page?.total ?? total.value;
      appendRecords(page?.records ?? []);
      nextPageNum.value += 1;
      if (!page?.records || page.records.length < FIRST_PAGE_SIZE) {
        finished.value = true;
      }
      updateFinished();
    }
  } catch (error) {
    console.error('/user/search/keywords load more error', error);
    showFailToast('加载失败');
    finished.value = true;
  } finally {
    loadingMore.value = false;
  }
};

watch(searchText, () => {
  window.clearTimeout(debounceTimer);
  if (searchText.value.trim().length === 0) {
    clearSearch();
    return;
  }
  debounceTimer = window.setTimeout(() => {
    resetAndSearch();
  }, 500);
});
</script>

<style scoped>
.search-page {
  min-height: 100%;
  padding: 12px var(--app-page-x) 0;
}

.search-head {
  position: sticky;
  top: var(--van-nav-bar-height);
  z-index: 12;
  padding: 8px 0 10px;
  background: linear-gradient(180deg, rgba(247, 250, 247, 0.98), rgba(247, 250, 247, 0.88));
  backdrop-filter: blur(14px);
}

.search-head :deep(.van-search) {
  padding: 0;
  background: transparent;
}

.search-head :deep(.van-search__content) {
  min-height: 44px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid var(--app-border);
  box-shadow: 0 10px 24px rgba(22, 80, 73, 0.08);
}

.search-head__action {
  height: 36px;
  padding: 0 10px;
  color: var(--app-primary-deep);
  font-size: 14px;
  font-weight: 800;
  background: transparent;
  border: 0;
}

.search-keywords {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding: 10px 2px 0;
}

.search-keywords__tag {
  color: var(--app-primary-deep);
  font-weight: 700;
  background: rgba(24, 165, 143, 0.1);
  border: 0;
}

.search-summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 16px 2px 12px;
}

.search-summary h1,
.search-empty-guide h1 {
  margin: 0;
  color: var(--app-text);
  font-size: 20px;
  font-weight: 900;
  line-height: 1.2;
  letter-spacing: 0;
}

.search-summary p,
.search-empty-guide p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.search-empty-guide {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 360px;
  padding: 26px;
  color: var(--app-primary-deep);
  text-align: center;
}

.search-empty-guide :deep(.van-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 70px;
  height: 70px;
  margin-bottom: 16px;
  background: rgba(24, 165, 143, 0.1);
  border-radius: 22px;
}
</style>
