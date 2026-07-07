<template>
  <div class="app-page">
    <section class="app-panel-heading">
      <p>标签筛选</p>
      <h1>找到同频的搭子</h1>
      <span>选择几个你关心的标签，系统会按标签搜索用户。</span>
    </section>

    <section class="search-panel">
      <van-search
          v-model="searchText"
          show-action
          placeholder="搜索标签"
          shape="round"
          @search="onSearch"
          @cancel="onCancel"
          @clear="onCancel"
      />

      <div class="selected-tags">
        <div class="selected-tags__header">
          <h2>已选标签</h2>
          <span>{{ activeIds.length }} 个</span>
        </div>
        <div class="app-tags" v-if="activeIds.length > 0">
          <van-tag
              v-for="tag in activeIds"
              :key="tag"
              closeable
              round
              class="app-tag"
              @close="doClose(tag)"
          >
            {{ tag }}
          </van-tag>
        </div>
        <p class="selected-tags__empty" v-else>先选择一个标签，再开始搜索。</p>
      </div>

      <div class="tag-picker">
        <div class="tag-picker__header">
          <h2>选择标签</h2>
          <span>{{ searchText ? '筛选结果' : '全部标签' }}</span>
        </div>
        <van-tree-select
            v-model:active-id="activeIds"
            v-model:main-active-index="activeIndex"
            :items="tagList"
            height="320"
        />
      </div>
    </section>

    <div class="search-submit">
      <van-button block type="primary" round :disabled="activeIds.length === 0" @click="doSearchResult">
        搜索搭子
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import {useRouter} from "vue-router";

const router = useRouter()

const searchText = ref('');

const originTagList = [{
  text: '性别',
  children: [
    {text: '男', id: '男'},
    {text: '女', id: '女'},
  ],
},
  {
    text: '年级',
    children: [
      {text: '大一', id: '大一'},
      {text: '大二', id: '大二'},
      {text: '大3', id: '大3'},
      {text: '大4', id: '大4'},
      {text: '大5', id: '大5aaaaaaa'},
      {text: '大6', id: '大6aaaaaaa'},
    ],
  },
]

// 标签列表
let tagList = ref(originTagList);

/**
 * 搜索过滤
 * @param val
 */
const onSearch = (val: string) => {
  tagList.value = originTagList.map(parentTag => {
    const tempChildren = [...parentTag.children];
    const tempParentTag = {...parentTag};
    tempParentTag.children = tempChildren.filter(item => item.text.includes(searchText.value));
    return tempParentTag;
  });

}
const onCancel = () => {
  searchText.value = '';
  tagList.value = originTagList;
};

// 已选中的标签
const activeIds = ref<string[]>([]);
const activeIndex = ref(0);

// 移除标签
const doClose = (tag: string) => {
  activeIds.value = activeIds.value.filter(item => {
    return item !== tag;
  })
}

/**
 * 执行搜索
 */
const doSearchResult = () => {
  router.push({
    path: '/user/list',
    query: {
      tags: activeIds.value
    }
  })
}

</script>

<style scoped>
.search-panel {
  margin-top: 14px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow);
}

.search-panel :deep(.van-search) {
  padding: 10px;
}

.selected-tags,
.tag-picker {
  padding: 14px;
  border-top: 1px solid var(--app-border);
}

.selected-tags__header,
.tag-picker__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 11px;
}

.selected-tags__header h2,
.tag-picker__header h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 900;
  letter-spacing: 0;
}

.selected-tags__header span,
.tag-picker__header span {
  font-size: 12px;
  font-weight: 700;
  color: var(--app-text-muted);
}

.selected-tags__empty {
  margin: 0;
  font-size: 13px;
  color: var(--app-text-muted);
}

.tag-picker {
  padding-bottom: 0;
}

.tag-picker :deep(.van-tree-select) {
  border-radius: 14px 14px 0 0;
}

.tag-picker :deep(.van-tree-select__nav),
.tag-picker :deep(.van-tree-select__content) {
  background: rgba(245, 247, 243, 0.8);
}

.tag-picker :deep(.van-sidebar-item--select) {
  color: var(--app-primary-deep);
  font-weight: 800;
}

.tag-picker :deep(.van-tree-select__item--active) {
  color: var(--app-primary-deep);
  font-weight: 800;
}

.search-submit {
  margin: 18px 0 0;
}

.search-submit :deep(.van-button) {
  height: 44px;
  box-shadow: 0 12px 24px rgba(24, 165, 143, 0.22);
}
</style>
