<script setup>
import { computed, ref } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { state, taskStatus, selectTask, toast } = useDemo();
const query = ref(""),
  filter = ref("全部"),
  date = ref(12),
  adding = ref(new URLSearchParams(location.search).has("new"));
const store = ref("示例餐饮 · 万达店"),
  reason = ref("");
const filtered = computed(() =>
  state.tasks.filter(
    (t) =>
      t.date === `2026-11-${date.value}` &&
      (filter.value === "全部" || taskStatus(t) === filter.value) &&
      (t.store.includes(query.value) || t.code.includes(query.value)),
  ),
);
function add() {
  if (state.offline && !state.downloaded)
    return toast("请联网下载授权门店资料后创建应急任务");
  if (!reason.value.trim()) return toast("请填写应急服务说明");
  const base = state.tasks.find((t) => t.store === store.value);
  if (
    state.tasks.some(
      (t) =>
        t.store === store.value &&
        t.date === "2026-11-12" &&
        t.type === "应急服务",
    )
  )
    return toast("今天已有该门店应急任务，请先检查");
  const t = {
    ...base,
    id: crypto.randomUUID(),
    type: "应急服务",
    time: "临时安排",
    date: "2026-11-12",
    instructions: reason.value,
  };
  state.tasks.push(t);
  selectTask(t.id);
}
</script>
<template>
  <PageFrame title="我的任务" nav="tasks"
    ><template #action
      ><button
        type="button"
        class="icon-button"
        aria-label="新增应急服务"
        @click="adding = !adding"
      >
        <Icon name="plus" /></button
    ></template>
    <div class="date-strip">
      <button
        v-for="(d, i) in ['一', '二', '三', '四', '五', '六', '日']"
        :key="i"
        type="button"
        class="day"
        :class="{ current: date === 9 + i }"
        @click="date = 9 + i"
      >
        {{ d }}<b>{{ 9 + i }}</b>
      </button>
    </div>
    <div class="tabs">
      <button
        v-for="f in ['全部', '待执行', '进行中', '待确认', '已完成']"
        :key="f"
        type="button"
        :class="{ active: filter === f }"
        @click="filter = f"
      >
        {{ f }}
      </button>
    </div>
    <div class="inner">
      <form v-if="adding" class="panel" @submit.prevent="add">
        <h2>新增应急服务</h2>
        <p class="mb">从已授权门店中选择，创建独立服务任务。</p>
        <FormField
          v-model="store"
          label="授权门店"
          :options="[...new Set(state.tasks.map((t) => t.store))]"
        /><FormField
          v-model="reason"
          label="服务说明"
          type="textarea"
          required
        /><button type="submit" class="btn primary full">创建应急任务</button>
      </form>
      <label class="search"
        ><Icon name="search" /><input
          v-model="query"
          aria-label="搜索门店名称或编号"
          placeholder="搜索门店名称或编号" /></label
      ><TaskCard v-for="t in filtered" :key="t.id" :task="t" />
      <div v-if="!filtered.length" class="empty-state">
        <Icon name="tasks" />
        <h3>暂无相关任务</h3>
        <p>试试切换日期、状态或搜索关键词</p>
      </div>
    </div></PageFrame
  >
</template>
