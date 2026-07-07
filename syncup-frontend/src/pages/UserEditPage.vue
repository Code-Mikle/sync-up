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
        <van-field
            v-else
            v-model="editUser.currentValue"
            :name="editUser.editKey"
            :label="editUser.editName"
            :placeholder="`请输入${editUser.editName}`"
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

if (isGenderEdit.value && !['0', '1', '2'].includes(String(editUser.value.currentValue))) {
  editUser.value.currentValue = '2';
}

const getSubmitValue = () => {
  if (isGenderEdit.value) {
    return Number(editUser.value.currentValue);
  }
  return editUser.value.currentValue;
}

const onSubmit = async () => {
  submitting.value = true;
  try {
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
</style>
