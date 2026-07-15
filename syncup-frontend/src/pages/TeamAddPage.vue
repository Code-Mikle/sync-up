<template>
  <div class="app-page">
    <section class="app-panel-heading">
      <p>创建队伍</p>
      <h1>发起一次新的组队</h1>
      <span>把活动、地点和时间写清楚，能更快找到合适的伙伴。</span>
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
            label="描述"
            type="textarea"
            maxlength="200"
            show-word-limit
            placeholder="说明活动安排、集合方式或偏好"
        />
      </van-cell-group>

      <van-cell-group inset class="app-form__group">
        <van-field v-model="addTeamData.activityType" label="活动类型" placeholder="如 羽毛球、徒步、探店" clearable />
        <van-field v-model="addTeamData.city" label="城市" placeholder="如 西安" clearable />
        <van-field v-model="addTeamData.district" label="区域" placeholder="区县或商圈，可不填" clearable />
        <van-field
            is-link
            readonly
            label="活动时间"
            :model-value="formatDateTime(addTeamData.startTime)"
            placeholder="点击选择活动开始时间"
            @click="openStartTimePicker"
        />
        <van-popup v-model:show="showStartPicker" position="bottom">
          <van-picker-group
              title="请选择活动开始时间"
              :tabs="['选择日期', '选择时间']"
              next-step-text="下一步"
              @confirm="confirmStartTime"
              @cancel="showStartPicker = false"
          >
            <van-date-picker v-model="startDatePickerValue" :show-toolbar="false" :min-date="minDate" />
            <van-time-picker v-model="startTimePickerValue" :show-toolbar="false" />
          </van-picker-group>
        </van-popup>
        <van-field name="durationMinutes" label="预计时长">
          <template #input>
            <van-stepper v-model="addTeamData.durationMinutes" min="30" max="720" step="30"/>
            <span class="app-form__unit">分钟</span>
          </template>
        </van-field>
        <van-field
            v-model.number="addTeamData.budgetPerPerson"
            label="人均预算"
            type="number"
            placeholder="可不填"
            clearable
        />
        <van-field v-model="addTeamData.skillLevel" label="水平" placeholder="如 入门、中等、熟练" clearable />
      </van-cell-group>

      <van-cell-group inset class="app-form__group">
        <van-field
            is-link
            readonly
            label="停止加入"
            :model-value="formatDateTime(addTeamData.expireTime)"
            placeholder="点击选择停止加入时间"
            @click="openExpireTimePicker"
        />
        <van-popup v-model:show="showExpirePicker" position="bottom">
          <van-picker-group
              title="请选择停止加入时间"
              :tabs="['选择日期', '选择时间']"
              next-step-text="下一步"
              @confirm="confirmExpireTime"
              @cancel="showExpirePicker = false"
          >
            <van-date-picker v-model="expireDatePickerValue" :show-toolbar="false" :min-date="minDate" />
            <van-time-picker v-model="expireTimePickerValue" :show-toolbar="false" />
          </van-picker-group>
        </van-popup>
        <van-field name="stepper" label="最大人数">
          <template #input>
            <van-stepper v-model="addTeamData.maxNum" max="20" min="1"/>
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
import {composeDateTime, formatDateTime, toDatePickerValue, toTimePickerValue} from "../utils/date";

const router = useRouter();
const showExpirePicker = ref(false);
const showStartPicker = ref(false);

type TeamAddFormData = {
  name: string;
  description: string;
  expireTime: Date | null;
  activityType: string;
  city: string;
  district: string;
  startTime: Date | null;
  durationMinutes: number;
  budgetPerPerson?: number;
  skillLevel: string;
  maxNum: number;
  password: string;
  status: number | string;
};

const initFormData: TeamAddFormData = {
  name: "",
  description: "",
  expireTime: null,
  activityType: "",
  city: "",
  district: "",
  startTime: null,
  durationMinutes: 120,
  budgetPerPerson: undefined,
  skillLevel: "",
  maxNum: 3,
  password: "",
  status: 0,
};

const minDate = new Date();
const submitting = ref(false);
const expireDatePickerValue = ref<string[]>(toDatePickerValue(new Date()));
const expireTimePickerValue = ref<string[]>(toTimePickerValue(new Date()));
const startDatePickerValue = ref<string[]>(toDatePickerValue(new Date()));
const startTimePickerValue = ref<string[]>(toTimePickerValue(new Date()));

const addTeamData = ref<TeamAddFormData>({...initFormData});

const openExpireTimePicker = () => {
  const fallback = new Date();
  expireDatePickerValue.value = toDatePickerValue(addTeamData.value.expireTime, fallback);
  expireTimePickerValue.value = toTimePickerValue(addTeamData.value.expireTime, fallback);
  showExpirePicker.value = true;
};

const confirmExpireTime = () => {
  const expireTime = composeDateTime(expireDatePickerValue.value, expireTimePickerValue.value);
  if (expireTime <= new Date()) {
    showFailToast("停止加入时间必须晚于当前时间");
    return;
  }
  addTeamData.value.expireTime = expireTime;
  showExpirePicker.value = false;
};

const openStartTimePicker = () => {
  const fallback = new Date();
  startDatePickerValue.value = toDatePickerValue(addTeamData.value.startTime, fallback);
  startTimePickerValue.value = toTimePickerValue(addTeamData.value.startTime, fallback);
  showStartPicker.value = true;
};

const confirmStartTime = () => {
  const startTime = composeDateTime(startDatePickerValue.value, startTimePickerValue.value);
  if (startTime <= new Date()) {
    showFailToast("活动时间必须晚于当前时间");
    return;
  }
  addTeamData.value.startTime = startTime;
  showStartPicker.value = false;
};

const onSubmit = async () => {
  submitting.value = true;
  try {
    const postData = {
      ...addTeamData.value,
      status: Number(addTeamData.value.status),
    };
    const res = await myAxios.post("/team/add", postData);
    if (res?.code === 0 && res.data) {
      showSuccessToast("添加成功");
      router.push({
        path: "/team",
        replace: true,
      });
    } else {
      showFailToast("添加失败");
    }
  } catch (error) {
    console.error("/team/add error", error);
    showFailToast("添加失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.app-form__unit {
  margin-left: 8px;
  font-size: 13px;
  color: var(--app-text-muted);
}
</style>
