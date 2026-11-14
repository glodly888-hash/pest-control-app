<script setup>
import { computed, ref, reactive } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { service: s, state, locked, go, toast } = useDemo();
const filter = ref("全部"),
  adding = ref(false);
const form = reactive({ id: "", type: "粘鼠板", position: "" });
const list = computed(() =>
  s.value.facilities.filter(
    (f) => filter.value === "全部" || f.type === filter.value,
  ),
);
const count = computed(() => s.value.facilities.filter((f) => f.saved).length);
function open(f) {
  state.selectedFacility = f.id;
  go("inspection");
}
function add() {
  if (!form.id.trim() || !form.position.trim())
    return toast("请填写编号和位置");
  if (s.value.facilities.some((f) => f.id === form.id.trim()))
    return toast("设施编号不能重复");
  s.value.facilities.push({
    id: form.id.trim(),
    type: form.type,
    position: form.position,
    area: "后区",
    floor: "一层",
    status: "在用",
    result: "待检查",
    condition: "正常",
    consumable: "未更换",
    quantityKind: "明确数量",
    flies: 0,
    others: 0,
    rodents: 0,
    cockroaches: 0,
    photos: [],
    reason: "",
    note: "",
    treatment: "",
    saved: false,
  });
  adding.value = false;
  toast("设施已添加，请完成本次点检");
}
</script>
<template>
  <PageFrame title="设施维护" back="detail"
    ><template #action
      ><button
        type="button"
        class="text-button"
        :disabled="locked"
        @click="adding = !adding"
      >
        新增
      </button></template
    >
    <div class="inner">
      <div class="split">
        <strong class="count-title"
          >{{ count }} <small>/ {{ s.facilities.length }} 已保存</small></strong
        ><span class="tag tag-blue"
          >{{ s.facilities.length - count }}项待填写</span
        >
      </div>
      <div class="progress">
        <span
          :style="{ width: (count / s.facilities.length) * 100 + '%' }"
        ></span>
      </div>
      <p class="hint">逐个检查并拍照，未检查需说明原因。</p>
    </div>
    <div class="tabs">
      <button
        v-for="f in ['全部', '灭蝇灯', '粘鼠板', '蟑螂监测设施', '果蝇杯']"
        :key="f"
        :class="{ active: filter === f }"
        type="button"
        @click="filter = f"
      >
        {{ f === "蟑螂监测设施" ? "蟑螂设施" : f }}
      </button>
    </div>
    <div class="inner">
      <form v-if="adding" class="panel" @submit.prevent="add">
        <h2>新增设施</h2>
        <FormField v-model="form.id" label="门店内编号" required /><FormField
          v-model="form.type"
          label="设施类型"
          :options="['粘鼠板', '灭蝇灯', '蟑螂监测设施', '果蝇杯']"
        /><FormField v-model="form.position" label="具体位置" required /><button
          type="submit"
          class="btn primary full"
        >
          保存设施
        </button>
      </form>
      <button
        v-for="f in list"
        :key="f.id"
        type="button"
        class="task-card"
        @click="open(f)"
      >
        <div class="split">
          <strong>{{ f.id }} · {{ f.type }}</strong
          ><span class="tag" :class="{ 'tag-blue': f.saved }">{{
            f.saved ? f.result : "待检查"
          }}</span>
        </div>
        <p class="mt">{{ f.area }} · {{ f.position }}</p>
        <div class="card-bottom">
          <span>{{
            f.result === "已检查"
              ? f.quantityKind === "数量不明"
                ? "捕获数量不明"
                : f.type === "灭蝇灯"
                  ? `飞虫 ${f.flies} · 其他 ${f.others}`
                  : "已记录检查结果"
              : f.reason || "等待本次点检"
          }}</span
          ><Icon name="chevron" />
        </div>
      </button>
      <div v-if="!list.length" class="empty-state">
        <Icon name="tasks" />
        <h3>暂无此类设施</h3>
        <p>可按实际情况新增设施</p>
      </div>
    </div>
    <template #footer
      ><button type="button" class="btn" @click="go('detail')">
        返回作业清单
      </button></template
    ></PageFrame
  >
</template>
