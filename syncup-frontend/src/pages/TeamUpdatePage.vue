<template>
  <div class="app-page">
    <section class="app-panel-heading">
      <p>更新队伍</p>
      <h1>调整队伍信息</h1>
      <span>保持活动字段准确，后续搜索和 AI 组队工具才能复用同一套能力。</span>
    </section>

    <section v-if="loading" class="app-panel-loading">
      <van-skeleton title :row="5"/>
    </section>

    <van-form v-else-if="loaded" class="app-form" @submit="onSubmit">
      <van-cell-group inset>
        <van-field
            v-model="teamData.name"
            name="name"
            label="队伍名"
            placeholder="请输入队伍名"
            clearable
            :rules="[{ required: true, message: '请输入队伍名' }]"
        />
        <van-field
            v-model="teamData.description"
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
        <van-field v-model="teamData.activityType" label="活动类型" placeholder="如 羽毛球、徒步、探店" clearable />
        <van-field v-model="teamData.city" label="城市" placeholder="如 西安" clearable />
        <van-field v-model="teamData.district" label="区域" placeholder="区县或商圈，可不填" clearable />
        <van-field
            is-link
            readonly
            label="活动时间"
            :model-value="formatDateTime(teamData.startTime)"
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
            <van-stepper v-model="teamData.durationMinutes" min="30" max="720" step="30"/>
            <span class="app-form__unit">分钟</span>
          </template>
        </van-field>
        <van-field
            v-model.number="teamData.budgetPerPerson"
            label="人均预算"
            type="number"
            placeholder="可不填"
            clearable
        />
        <van-field v-model="teamData.skillLevel" label="水平" placeholder="如 入门、中等、熟练" clearable />
      </van-cell-group>

      <van-cell-group inset class="app-form__group">
        <van-field
            is-link
            readonly
            label="停止加入"
            :model-value="formatDateTime(teamData.expireTime)"
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
        <van-field name="radio" label="队伍状态">
          <template #input>
            <van-radio-group v-model="teamData.status" direction="horizontal">
              <van-radio name="0">公开</van-radio>
              <van-radio name="1">私有</van-radio>
              <van-radio name="2">加密</van-radio>
            </van-radio-group>
          </template>
        </van-field>
        <van-field
            v-if="Number(teamData.status) === 2"
            v-model="teamData.password"
            type="password"
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
import {composeDateTime, formatDateTime, toDatePickerValue, toTimePickerValue} from "../utils/date";

const router = useRouter();
const route = useRoute();

const showExpirePicker = ref(false);
const showStartPicker = ref(false);
const minDate = new Date();
const loading = ref(true);
const loaded = ref(false);
const submitting = ref(false);
const expireDatePickerValue = ref<string[]>(toDatePickerValue(new Date()));
const expireTimePickerValue = ref<string[]>(toTimePickerValue(new Date()));
const startDatePickerValue = ref<string[]>(toDatePickerValue(new Date()));
const startTimePickerValue = ref<string[]>(toTimePickerValue(new Date()));

const id = Number(route.query.id ?? 0);
type TeamUpdateFormData = Partial<Omit<TeamType, "status">> & {
  status?: number | string;
};

const teamData = ref<TeamUpdateFormData>({});

onMounted(async () => {
  if (id <= 0) {
    showFailToast("加载队伍失败");
    loading.value = false;
    return;
  }
  try {
    const res = await myAxios.get<TeamType>("/team/get", {
      params: {id},
    });
    if (res?.code === 0) {
      teamData.value = {
        ...res.data,
        status: String(res.data.status),
        durationMinutes: res.data.durationMinutes ?? 120,
      };
      loaded.value = true;
    } else {
      showFailToast("加载队伍失败，请刷新重试");
    }
  } catch (error) {
    console.error("/team/get error", error);
    showFailToast("加载队伍失败，请刷新重试");
  } finally {
    loading.value = false;
  }
});

const openExpireTimePicker = () => {
  const fallback = new Date();
  expireDatePickerValue.value = toDatePickerValue(teamData.value.expireTime, fallback);
  expireTimePickerValue.value = toTimePickerValue(teamData.value.expireTime, fallback);
  showExpirePicker.value = true;
};

const confirmExpireTime = () => {
  const expireTime = composeDateTime(expireDatePickerValue.value, expireTimePickerValue.value);
  if (expireTime <= new Date()) {
    showFailToast("停止加入时间必须晚于当前时间");
    return;
  }
  teamData.value.expireTime = expireTime;
  showExpirePicker.value = false;
};

const openStartTimePicker = () => {
  const fallback = new Date();
  startDatePickerValue.value = toDatePickerValue(teamData.value.startTime, fallback);
  startTimePickerValue.value = toTimePickerValue(teamData.value.startTime, fallback);
  showStartPicker.value = true;
};

const confirmStartTime = () => {
  const startTime = composeDateTime(startDatePickerValue.value, startTimePickerValue.value);
  teamData.value.startTime = startTime;
  showStartPicker.value = false;
};

const onSubmit = async () => {
  submitting.value = true;
  try {
    const postData = {
      ...teamData.value,
      status: Number(teamData.value.status),
    };
    const res = await myAxios.post("/team/update", postData);
    if (res?.code === 0 && res.data) {
      showSuccessToast("更新成功");
      router.push({
        path: "/team",
        replace: true,
      });
    } else {
      showFailToast("更新失败");
    }
  } catch (error) {
    console.error("/team/update error", error);
    showFailToast("更新失败，请稍后重试");
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
