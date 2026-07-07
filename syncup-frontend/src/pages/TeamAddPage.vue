<template>
  <div class="app-page">
    <section class="app-panel-heading">
      <p>创建队伍</p>
      <h1>发起一次新的组队</h1>
      <span>写清楚目标、人数和有效时间，能更快找到合适的伙伴。</span>
    </section>

    <van-form class="app-form" @submit="onSubmit">
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
        <van-field name="stepper" label="最大人数">
          <template #input>
            <van-stepper v-model="addTeamData.maxNum" max="10" min="3"/>
          </template>
        </van-field>
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
          创建队伍
        </van-button>
      </div>
    </van-form>
  </div>
</template>

<script setup lang="ts">

import {useRouter} from "vue-router";
import {ref} from "vue";
import myAxios from "../plugins/myAxios";
import {showFailToast, showSuccessToast} from "vant";

const router = useRouter();
// 展示日期选择器
const showPicker = ref(false);

const initFormData = {
  "name": "",
  "description": "",
  "expireTime": null,
  "maxNum": 3,
  "password": "",
  "status": 0,
}

const minDate = new Date();
const submitting = ref(false);

// 需要用户填写的表单数据
const addTeamData = ref({...initFormData})

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
    const res = await myAxios.post("/team/add", postData);
    if (res?.code === 0 && res.data){
      showSuccessToast('添加成功');
      router.push({
        path: '/team',
        replace: true,
      });
    } else {
      showFailToast('添加失败');
    }
  } catch (error) {
    console.error('/team/add error', error);
    showFailToast('添加失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
</style>
