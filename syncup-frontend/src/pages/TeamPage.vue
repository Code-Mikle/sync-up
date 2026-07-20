<template>
  <div class="team-page">
    <section class="team-hero">
      <div>
        <p class="team-hero__eyebrow">组队大厅</p>
        <h1>找到一起行动的队伍</h1>
        <p class="team-hero__desc">按活动、地点、时间和预算筛选队伍，公开队伍可直接加入。</p>
      </div>
      <van-button class="team-hero__button" type="primary" icon="plus" round @click="toAddTeam">
        创建
      </van-button>
    </section>

    <section class="team-toolbar">
      <van-search
          v-model="filters.searchText"
          placeholder="搜索队伍名称或描述"
          shape="round"
          @search="refreshTeamList"
          @clear="refreshTeamList"
      />
      <van-tabs v-model:active="active" class="team-tabs" @change="onTabChange">
        <van-tab title="公开队伍" name="public" />
        <van-tab title="加密队伍" name="secret" />
      </van-tabs>
      <div class="team-filters">
        <van-field label="大类">
          <template #input>
            <select v-model.number="filters.activityCategory" class="category-select" @change="refreshTeamList">
              <option :value="undefined">全部</option>
              <option
                  v-for="category in teamActivityCategoryOptions"
                  :key="category.code"
                  :value="category.code"
              >
                {{ category.name }}
              </option>
            </select>
          </template>
        </van-field>
        <van-field v-model="filters.city" label="城市" placeholder="西安" clearable @blur="refreshTeamList" />
        <van-field
            v-model.number="filters.maxBudgetPerPerson"
            label="预算"
            type="number"
            placeholder="上限"
            clearable
            @blur="refreshTeamList"
        />
        <van-field
            is-link
            readonly
            label="开始"
            :model-value="formatDateTime(filters.startTimeBegin)"
            placeholder="时间下限"
            @click="openTimePicker('begin')"
        />
        <van-field
            is-link
            readonly
            label="结束"
            :model-value="formatDateTime(filters.startTimeEnd)"
            placeholder="时间上限"
            @click="openTimePicker('end')"
        />
        <van-cell title="只看有余位">
          <template #right-icon>
            <van-switch v-model="filters.onlyAvailable" size="22px" @change="refreshTeamList" />
          </template>
        </van-cell>
      </div>
      <div class="team-filter-actions">
        <van-button size="small" plain round @click="resetFilters">重置</van-button>
        <van-button size="small" type="primary" round @click="refreshTeamList">筛选</van-button>
      </div>
    </section>

    <van-popup v-model:show="showTimePicker" position="bottom">
      <van-picker-group
          title="选择时间"
          :tabs="['选择日期', '选择时间']"
          next-step-text="下一步"
          @confirm="confirmFilterTime"
          @cancel="showTimePicker = false"
      >
        <van-date-picker v-model="filterDatePickerValue" :show-toolbar="false" />
        <van-time-picker v-model="filterTimePickerValue" :show-toolbar="false" />
      </van-picker-group>
    </van-popup>

    <section class="team-summary">
      <div>
        <h2>{{ active === 'public' ? '公开队伍' : '加密队伍' }}</h2>
        <p>{{ loading ? '正在加载队伍' : `共 ${teamList.length} 个队伍` }}</p>
      </div>
      <van-button size="small" plain round icon="replay" @click="refreshTeamList">
        刷新
      </van-button>
    </section>

    <team-card-list
        :team-list="teamList"
        :loading="loading"
        @action-success="refreshTeamList"
    />
    <van-empty
        v-if="!loading && teamList.length < 1"
        image-size="88"
        description="暂时没有找到队伍"
    />
  </div>
</template>

<script setup lang="ts">
import {useRouter} from "vue-router";
import TeamCardList from "../components/TeamCardList.vue";
import {computed, onMounted, ref} from "vue";
import myAxios from "../plugins/myAxios";
import {showFailToast} from "vant";
import {TeamType} from "../models/team";
import {composeDateTime, formatDateTime, toDatePickerValue, toTimePickerValue} from "../utils/date";
import {teamActivityCategoryOptions} from "../constants/team";

const active = ref("public");
const router = useRouter();
const loading = ref(false);
const teamList = ref<TeamType[]>([]);
const showTimePicker = ref(false);
const editingTimeField = ref<"begin" | "end">("begin");
const filterDatePickerValue = ref<string[]>(toDatePickerValue(new Date()));
const filterTimePickerValue = ref<string[]>(toTimePickerValue(new Date()));

const filters = ref({
  searchText: "",
  activityCategory: undefined as number | undefined,
  city: "",
  maxBudgetPerPerson: undefined as number | undefined,
  startTimeBegin: undefined as Date | undefined,
  startTimeEnd: undefined as Date | undefined,
  onlyAvailable: false,
});

const currentStatus = computed(() => active.value === "public" ? 0 : 2);

const onTabChange = () => {
  refreshTeamList();
};

const toAddTeam = () => {
  router.push({path: "/team/add"});
};

const listTeam = async () => {
  loading.value = true;
  try {
    const res = await myAxios.get<TeamType[]>("/team/list", {
      params: {
        searchText: filters.value.searchText,
        pageNum: 1,
        status: currentStatus.value,
        activityCategory: filters.value.activityCategory || undefined,
        city: filters.value.city || undefined,
        maxBudgetPerPerson: filters.value.maxBudgetPerPerson || undefined,
        startTimeBegin: filters.value.startTimeBegin,
        startTimeEnd: filters.value.startTimeEnd,
        onlyAvailable: filters.value.onlyAvailable || undefined,
      },
    });
    if (res?.code === 0) {
      teamList.value = res.data ?? [];
    } else {
      teamList.value = [];
      showFailToast("加载队伍失败，请刷新重试");
    }
  } catch (error) {
    console.error("/team/list error", error);
    teamList.value = [];
    showFailToast("加载队伍失败，请刷新重试");
  } finally {
    loading.value = false;
  }
};

const refreshTeamList = () => {
  listTeam();
};

const resetFilters = () => {
  filters.value = {
    searchText: "",
    activityCategory: undefined,
    city: "",
    maxBudgetPerPerson: undefined,
    startTimeBegin: undefined,
    startTimeEnd: undefined,
    onlyAvailable: false,
  };
  refreshTeamList();
};

const openTimePicker = (field: "begin" | "end") => {
  editingTimeField.value = field;
  const value = field === "begin" ? filters.value.startTimeBegin : filters.value.startTimeEnd;
  filterDatePickerValue.value = toDatePickerValue(value, new Date());
  filterTimePickerValue.value = toTimePickerValue(value, new Date());
  showTimePicker.value = true;
};

const confirmFilterTime = () => {
  const date = composeDateTime(filterDatePickerValue.value, filterTimePickerValue.value);
  if (editingTimeField.value === "begin") {
    filters.value.startTimeBegin = date;
  } else {
    filters.value.startTimeEnd = date;
  }
  showTimePicker.value = false;
  refreshTeamList();
};

onMounted(() => {
  refreshTeamList();
});
</script>

<style scoped>
.team-page {
  padding: 14px var(--app-page-x) 0;
}

.team-hero {
  display: flex;
  gap: 14px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 18px;
  color: var(--app-text);
  background:
      radial-gradient(circle at right top, rgba(var(--app-accent-rgb), 0.18), transparent 8rem),
      radial-gradient(circle at left bottom, rgba(var(--app-secondary-rgb), 0.08), transparent 9rem),
      linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(239, 241, 255, 0.95));
  border: 1px solid var(--app-border);
  border-radius: 22px;
  box-shadow: var(--app-shadow);
}

.team-hero__eyebrow {
  margin: 0 0 7px;
  font-size: 12px;
  font-weight: 800;
  color: var(--app-primary-deep);
}

.team-hero h1 {
  max-width: 210px;
  margin: 0;
  font-size: 23px;
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: 0;
}

.team-hero__desc {
  max-width: 230px;
  margin: 9px 0 0;
  font-size: 13px;
  line-height: 1.55;
  color: var(--app-text-muted);
}

.team-hero__button {
  flex: 0 0 auto;
  height: 38px;
  padding: 0 15px;
  box-shadow: 0 10px 20px rgba(var(--app-primary-rgb), 0.2);
}

.team-toolbar {
  margin-top: 12px;
  padding: 4px 0 10px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(40, 38, 101, 0.05);
  border-radius: 18px;
}

.team-toolbar :deep(.van-search) {
  padding: 8px;
}

.team-tabs {
  padding: 0 8px 4px;
}

.team-tabs :deep(.van-tabs__wrap) {
  height: 42px;
}

.team-tabs :deep(.van-tab) {
  font-weight: 700;
  color: var(--app-text-muted);
}

.team-tabs :deep(.van-tab--active) {
  color: var(--app-primary-deep);
}

.team-filters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 4px 10px 0;
}

.team-filters :deep(.van-cell) {
  min-height: 44px;
  padding: 8px 10px;
  border-radius: 12px;
}

.team-filters :deep(.van-field__label),
.team-filters :deep(.van-cell__title) {
  width: 42px;
  font-size: 12px;
}

.team-filter-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  padding: 10px 10px 0;
}

.category-select {
  width: 100%;
  color: var(--app-text);
  background: transparent;
  border: 0;
  outline: 0;
}

.team-summary {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 20px 2px 10px;
}

.team-summary h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: 0;
}

.team-summary p {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--app-text-muted);
}
</style>
