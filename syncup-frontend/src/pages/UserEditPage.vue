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
          <div class="tag-editor__chips" v-if="tagList.length">
            <van-tag
                v-for="tag in tagList"
                :key="tag"
                closeable
                round
                size="medium"
                @close="removeTag(tag)"
            >
              {{ tag }}
            </van-tag>
          </div>
          <div class="tag-editor__empty" v-else>还没有标签，添加几个让别人更容易找到你。</div>
          <div class="tag-editor__input">
            <van-field
                v-model="tagInput"
                name="tagInput"
                label="新增"
                placeholder="输入标签，支持空格或逗号分隔"
                maxlength="16"
                clearable
                @keydown.enter.prevent="addTagsFromInput"
            />
            <van-button size="small" round type="primary" plain native-type="button" @click="addTagsFromInput">
              添加
            </van-button>
          </div>
          <div class="tag-editor__suggestions">
            <button
                v-for="tag in suggestedTags"
                :key="tag"
                type="button"
                :disabled="tagList.includes(tag)"
                @click="addTag(tag)"
            >
              {{ tag }}
            </button>
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
import {computed, ref} from "vue";
import myAxios from "../plugins/myAxios";
import {showFailToast, showSuccessToast} from "vant";
import {getCurrentUser} from "../services/user";
import {genderOptions} from "../constants/user";
import {parseUserTags} from "../utils/user";

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
const isTagsEdit = computed(() => editUser.value.editKey === 'tags');
const tagInput = ref('');
const tagList = ref<string[]>(parseUserTags(String(editUser.value.currentValue)));
const suggestedTags = ['羽毛球', '足球', '篮球', '跑步', '健身', '徒步', '桌游', '电影', '探店', '编程'];

if (isGenderEdit.value && !['0', '1', '2'].includes(String(editUser.value.currentValue))) {
  editUser.value.currentValue = '2';
}

const getSubmitValue = () => {
  if (isGenderEdit.value) {
    return Number(editUser.value.currentValue);
  }
  if (isTagsEdit.value) {
    return JSON.stringify(tagList.value);
  }
  return editUser.value.currentValue;
}

const normalizeTag = (tag: string) => tag.trim().replace(/^#/, '');

const addTag = (tag: string) => {
  const normalized = normalizeTag(tag);
  if (!normalized) {
    return;
  }
  if (normalized.length > 12) {
    showFailToast('单个标签最多 12 个字符');
    return;
  }
  if (tagList.value.includes(normalized)) {
    return;
  }
  if (tagList.value.length >= 12) {
    showFailToast('最多设置 12 个标签');
    return;
  }
  tagList.value.push(normalized);
};

const addTagsFromInput = () => {
  const tags = tagInput.value
      .split(/[\s,，、]+/)
      .map(normalizeTag)
      .filter(Boolean);
  tags.forEach(addTag);
  tagInput.value = '';
};

const removeTag = (tag: string) => {
  tagList.value = tagList.value.filter(item => item !== tag);
};

const onSubmit = async () => {
  submitting.value = true;
  try {
    if (isTagsEdit.value && tagInput.value.trim()) {
      addTagsFromInput();
    }
    const currentUser = await getCurrentUser();

    if (!currentUser) {
      showFailToast('用户未登录');
      return;
    }

    console.log(currentUser, '当前用户')

    const res = await myAxios.post('/user/update', {
      'id': currentUser.id,
      [editUser.value.editKey as string]: getSubmitValue(),
    })
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

.tag-editor__chips,
.tag-editor__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-editor__chips :deep(.van-tag) {
  padding: 5px 9px;
  color: var(--app-primary-deep);
  font-weight: 800;
  background: rgba(24, 165, 143, 0.1);
  border: 0;
}

.tag-editor__empty {
  padding: 14px;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.45;
  background: rgba(245, 247, 243, 0.86);
  border: 1px dashed rgba(28, 61, 58, 0.14);
  border-radius: 12px;
}

.tag-editor__input {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  margin-top: 14px;
}

.tag-editor__input :deep(.van-cell) {
  padding: 0;
  background: transparent;
}

.tag-editor__input :deep(.van-field__body) {
  min-height: 38px;
  padding: 0 12px;
  background: rgba(245, 247, 243, 0.9);
  border: 1px solid rgba(28, 61, 58, 0.08);
  border-radius: 999px;
}

.tag-editor__input :deep(.van-field__label) {
  display: none;
}

.tag-editor__suggestions {
  margin-top: 12px;
}

.tag-editor__suggestions button {
  height: 30px;
  padding: 0 11px;
  color: #2d5b53;
  font-size: 12px;
  font-weight: 800;
  background: rgba(24, 165, 143, 0.08);
  border: 1px solid rgba(24, 165, 143, 0.12);
  border-radius: 999px;
}

.tag-editor__suggestions button:disabled {
  color: var(--app-text-muted);
  background: rgba(102, 119, 117, 0.08);
}
</style>
