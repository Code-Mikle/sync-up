<template>
  <div class="app-page">
    <section class="app-panel-heading">
      <p>编辑资料</p>
      <h1>{{ editUser.editName || '个人信息' }}</h1>
      <span>修改后会同步到你的个人资料中。</span>
    </section>

    <van-form class="app-form" @submit="onSubmit">
      <van-cell-group inset>
        <van-field v-if="isGenderEdit" name="gender" label="性别">
          <template #input>
            <van-radio-group v-model="editUser.currentValue" direction="horizontal">
              <van-radio v-for="option in genderOptions" :key="option.value" :name="option.value">
                {{ option.text }}
              </van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <div v-else-if="isTagsEdit" class="tag-editor">
          <div class="tag-editor__label">我的标签</div>
          <p class="tag-editor__hint">从系统标签中选择，最多 10 个。标签会帮助 AI 为你匹配更合适的搭子。</p>
          <div v-if="tagLoading" class="tag-editor__empty">正在加载标签…</div>
          <div v-else-if="tagCategories.length === 0" class="tag-editor__empty">标签暂时不可用，请稍后重试。</div>
          <div v-else class="tag-editor__categories">
            <section v-for="category in tagCategories" :key="category.id" class="tag-editor__category">
              <div class="tag-editor__category-name">{{ category.name }}</div>
              <div class="tag-editor__suggestions">
                <button
                    v-for="tag in category.tags"
                    :key="tag.id"
                    type="button"
                    :class="{ 'tag-editor__option--selected': selectedTagIds.includes(tag.id) }"
                    :disabled="!selectedTagIds.includes(tag.id) && selectedTagIds.length >= MAX_TAGS"
                    @click="toggleTag(tag.id)"
                >
                  {{ tag.name }}
                </button>
              </div>
            </section>
          </div>
        </div>
        <van-field
            v-else
            v-model="editUser.currentValue"
            :name="editUser.editKey"
            :label="editUser.editName"
            :placeholder="`请输入${editUser.editName}`"
            :type="isProfileEdit ? 'textarea' : 'text'"
            :rows="isProfileEdit ? 4 : 1"
            :maxlength="isProfileEdit ? 500 : undefined"
            :show-word-limit="isProfileEdit"
            clearable
        />
      </van-cell-group>
      <div class="app-form__submit">
        <van-button round block type="primary" native-type="submit" :loading="submitting">
          保存修改
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup lang="ts">
import {useRoute, useRouter} from "vue-router";
import {computed, onMounted, ref} from "vue";
import myAxios from "../plugins/myAxios";
import {showFailToast, showSuccessToast} from "vant";
import {getCurrentUser} from "../services/user";
import {genderOptions} from "../constants/user";

type TagOption = {
  id: number;
  name: string;
  description?: string;
};

type TagCategory = {
  id: number;
  name: string;
  tags: TagOption[];
};

const MAX_TAGS = 10;

const route = useRoute();
const router = useRouter();
const submitting = ref(false);

const getQueryValue = (value: unknown) => {
  if (Array.isArray(value)) {
    return value[0] ?? '';
  }
  return value ?? '';
};

const editUser = ref({
  editKey: String(getQueryValue(route.query.editKey)),
  currentValue: getQueryValue(route.query.currentValue),
  editName: String(getQueryValue(route.query.editName)),
})

const isGenderEdit = computed(() => editUser.value.editKey === 'gender');
const isProfileEdit = computed(() => editUser.value.editKey === 'profile');
const isTagsEdit = computed(() => editUser.value.editKey === 'tagIds');
const tagCategories = ref<TagCategory[]>([]);
const selectedTagIds = ref<number[]>(parseTagIds(editUser.value.currentValue));
const tagLoading = ref(false);

if (isGenderEdit.value && !['0', '1', '2'].includes(String(editUser.value.currentValue))) {
  editUser.value.currentValue = '2';
}

const getSubmitValue = () => {
  if (isGenderEdit.value) {
    return Number(editUser.value.currentValue);
  }
  return editUser.value.currentValue;
}

function parseTagIds(value: unknown): number[] {
  if (Array.isArray(value)) {
    return value.map(Number).filter(id => Number.isInteger(id) && id > 0);
  }
  if (typeof value !== 'string' || !value.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed)
        ? parsed.map(Number).filter(id => Number.isInteger(id) && id > 0)
        : [];
  } catch {
    return [];
  }
}

const toggleTag = (tagId: number) => {
  if (selectedTagIds.value.includes(tagId)) {
    selectedTagIds.value = selectedTagIds.value.filter(id => id !== tagId);
    return;
  }
  if (selectedTagIds.value.length >= MAX_TAGS) {
    showFailToast(`最多选择 ${MAX_TAGS} 个标签`);
    return;
  }
  selectedTagIds.value.push(tagId);
};

const loadTags = async () => {
  if (!isTagsEdit.value) {
    return;
  }
  tagLoading.value = true;
  try {
    const response = await myAxios.get<TagCategory[]>('/tag/list');
    tagCategories.value = response.code === 0 ? response.data ?? [] : [];
  } catch (error) {
    console.error('/tag/list error', error);
    showFailToast('标签加载失败');
  } finally {
    tagLoading.value = false;
  }
};

const onSubmit = async () => {
  submitting.value = true;
  try {
    const currentUser = await getCurrentUser();

    if (!currentUser) {
      showFailToast('用户未登录');
      return;
    }

    console.log(currentUser, '当前用户')

    const payload: Record<string, unknown> = { id: currentUser.id };
    if (isTagsEdit.value) {
      payload.tagIds = selectedTagIds.value;
    } else {
      payload[editUser.value.editKey as string] = getSubmitValue();
    }
    const res = await myAxios.post('/user/update', payload)
    console.log(res, '更新请求');
    if (res.code === 0 && res.data > 0) {
      showSuccessToast('修改成功');
      router.back();
    } else {
      showFailToast('修改错误');
    }
  } catch (error) {
    console.error('/user/update error', error);
    showFailToast('修改失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
};

onMounted(loadTags);

</script>

<style scoped>
.tag-editor {
  padding: 16px;
}

.tag-editor__label {
  margin-bottom: 10px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 900;
}

.tag-editor__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-editor__empty {
  padding: 14px;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.45;
  background: rgba(244, 245, 252, 0.88);
  border: 1px dashed rgba(40, 38, 101, 0.14);
  border-radius: 12px;
}

.tag-editor__hint {
  margin: -2px 0 14px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.tag-editor__categories {
  display: grid;
  gap: 14px;
}

.tag-editor__category-name {
  margin-bottom: 8px;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 900;
}

.tag-editor__suggestions {
  margin-top: 12px;
}

.tag-editor__suggestions button {
  height: 30px;
  padding: 0 11px;
  color: #50516f;
  font-size: 12px;
  font-weight: 800;
  background: rgba(var(--app-primary-rgb), 0.08);
  border: 1px solid rgba(var(--app-primary-rgb), 0.12);
  border-radius: 999px;
}

.tag-editor__suggestions button:disabled {
  color: var(--app-text-muted);
  background: rgba(109, 111, 139, 0.08);
}

.tag-editor__suggestions button.tag-editor__option--selected {
  color: #fff;
  background: var(--app-primary);
  border-color: var(--app-primary);
}
</style>
