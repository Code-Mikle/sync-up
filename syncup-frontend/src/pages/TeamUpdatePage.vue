<template>
  <div class="app-page">
    <section class="app-panel-heading">
      <p>更新队伍</p>
      <h1>调整队伍信息</h1>
      <span>保持描述和状态准确，能减少无效加入和沟通成本。</span>
    </section>

    <section v-if="loading" class="app-panel-loading">
      <van-skeleton title :row="5"/>
    </section>

    <van-form v-else-if="loaded" class="app-form" @submit="onSubmit">
      <van-cell-group inset>
        <van-field
            v-model="addTeamData.name"
            name="name"
            label="队伍名"
            placeholder="请输入队伍名"
            clearable
            :rules="[{ required: true, message: '请输入队伍名' }]"
        />
        <van-field
            v-model="addTeamData.description"
            rows="4"
            autosize
            label="队伍描述"
            type="textarea"
            maxlength="200"
            show-word-limit
            placeholder="请输入队伍描述"
        />
      </van-cell-group>

      <van-cell-group inset class="app-form__group">
        <van-field
            is-link
            readonly
            name="datetimePicker"
            label="过期时间"
            :placeholder="formatDate(addTeamData.expireTime) || '点击选择过期时间'"
            @click="showPicker = true"
        />
        <van-popup v-model:show="showPicker" position="bottom">
          <van-datetime-picker
              v-model="addTeamData.expireTime"
              @confirm="showPicker = false"
              @cancel="showPicker = false"
              type="datetime"
              title="请选择过期时间"
              :min-date="minDate"
          />
        </van-popup>
        <van-field name="radio" label="队伍状态">
          <template #input>
            <van-radio-group v-model="addTeamData.status" direction="horizontal">
              <van-radio name="0">公开</van-radio>
              <van-radio name="1">私有</van-radio>
              <van-radio name="2">加密</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field
            v-if="Number(addTeamData.status) === 2"
            v-model="addTeamData.password"
            type="password"
            name="password"
            label="密码"
            clearable
            placeholder="请输入队伍密码"
            :rules="[{ required: true, message: '请填写密码' }]"
        />
      </van-cell-group>

      <div class="app-form__submit">
        <van-button round block type="primary" native-type="submit" :loading="submitting">
          保存修改
        </van-button>
      </div>
    </van-form>

    <van-empty v-else image-size="88" description="队伍信息加载失败"/>
  </div>
</template>

<script setup lang="ts">

import {useRoute, useRouter} from "vue-router";
import {onMounted, ref} from "vue";
import myAxios from "../plugins/myAxios";
import {showFailToast, showSuccessToast} from "vant";
import {TeamType} from "../models/team";

const router = useRouter();
const route = useRoute();

// 展示日期选择器
const showPicker = ref(false);

const minDate = new Date();
const loading = ref(true);
const loaded = ref(false);
const submitting = ref(false);

const id = Number(route.query.id ?? 0);

// 需要用户填写的表单数据
const addTeamData = ref<Partial<TeamType>>({})

// 获取之前的队伍信息
onMounted(async () => {
  if (id <= 0) {
    showFailToast('加载队伍失败');
    loading.value = false;
    return;
  }
  try {
    const res = await myAxios.get<TeamType>("/team/get", {
      params: {
        id,
      }
    });
    if (res?.code === 0) {
      addTeamData.value = res.data;
      loaded.value = true;
    } else {
      showFailToast('加载队伍失败，请刷新重试');
    }
  } catch (error) {
    console.error('/team/get error', error);
    showFailToast('加载队伍失败，请刷新重试');
  } finally {
    loading.value = false;
  }
})

const formatDate = (value: unknown) => {
  if (!value) {
    return '';
  }
  const dateText = String(value);
  return dateText.length > 16 ? dateText.slice(0, 16).replace('T', ' ') : dateText;
}

// 提交
const onSubmit = async () => {
  submitting.value = true;
  try {
    const postData = {
      ...addTeamData.value,
      status: Number(addTeamData.value.status)
    }
    const res = await myAxios.post("/team/update", postData);
    if (res?.code === 0 && res.data){
      showSuccessToast('更新成功');
      router.push({
        path: '/team',
        replace: true,
      });
    } else {
      showFailToast('更新失败');
    }
  } catch (error) {
    console.error('/team/update error', error);
    showFailToast('更新失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
</style>
