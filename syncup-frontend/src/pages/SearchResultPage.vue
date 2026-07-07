<template>
  <div class="app-page">
    <section class="app-panel-heading app-panel-heading--split">
      <div>
        <p>搜索结果</p>
        <h1>匹配到的搭子</h1>
        <span>{{ loading ? '正在搜索' : `共 ${userList.length} 人` }}</span>
      </div>
      <van-button size="small" round plain icon="replay" @click="loadData">
        刷新
      </van-button>
    </section>

    <div class="app-tags result-tags" v-if="selectedTags.length > 0">
      <van-tag v-for="tag in selectedTags" :key="tag" round class="app-tag">
        {{ tag }}
      </van-tag>
    </div>

    <user-card-list :user-list="userList" :loading="loading" />
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
const {tags} = route.query;

const userList = ref<UserType[]>([]);
const loading = ref(false);

const selectedTags = computed(() => {
  if (Array.isArray(tags)) {
    return tags.map(String);
  }
  return tags ? [String(tags)] : [];
});

const loadData = async () => {
  loading.value = true;
  try {
    const response = await myAxios.get<UserType[]>('/user/search/tags', {
      params: {
        tagNameList: tags
      },
      paramsSerializer: params => {
        return qs.stringify(params, {indices: false})
      }
    });
    console.log('/user/search/tags succeed', response);
    userList.value = normalizeUserList(response?.data ?? []);
  } catch (error) {
    console.error('/user/search/tags error', error);
    userList.value = [];
    showFailToast('请求失败');
  } finally {
    loading.value = false;
  }
}

onMounted(loadData)



</script>

<style scoped>
.result-tags {
  margin: 14px 0 12px;
}
</style>
