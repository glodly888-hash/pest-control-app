<script setup>
import { computed } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { state, service: s, task, go, taskStatus } = useDemo();
const steps = computed(() => [
  [
    "checkin",
    "到店签到",
    s.value.checkin.time ? "已完成" : "待填写",
    "门头照片与到店位置",
  ],
  [
    "intake",
    "服务前确认",
    s.value.modules.intake ? "已完成" : "待填写",
    "服务须知、反馈与发现",
  ],
  [
    "static",
    "门店静态评估",
    s.value.modules.static ? "已完成" : "待确认",
    "核对本次环境情况",
  ],
  [
    "devices",
    "设施维护",
    `${s.value.facilities.filter((f) => f.saved).length} / ${s.value.facilities.length}`,
    "逐个检查并拍照",
  ],
  [
    "risks",
    "风险整改跟进",
    `${s.value.risks.filter((r) => r.checked).length} / ${s.value.risks.length}`,
    "历史风险逐条复核",
  ],
  [
    "treatment",
    "防治措施",
    s.value.treatment.saved ? "已完成" : "待填写",
    "清洁、封堵、物理或用药",
  ],
  [
    "summary",
    "总结与签字",
    s.value.signatures.client ? "已双签" : "待完成",
    "核对内容，双方现场确认",
  ],
]);
const done = computed(
  () =>
    steps.value.filter((x) => ["已完成", "已双签"].includes(x[2])).length +
    (s.value.facilities.every((f) => f.saved) ? 1 : 0) +
    (s.value.risks.every((r) => r.checked || r.state === "已关闭") ? 1 : 0),
);
</script>
<template>
  <PageFrame title="服务任务" back="tasks"
    ><div class="strip">
      <Icon :name="state.offline ? 'download' : 'check'" /><span>{{
        state.downloaded
          ? "任务资料已下载 · 可离线作业"
          : "资料尚未下载，请先联网下载"
      }}</span>
    </div>
    <div class="inner">
      <div class="panel">
        <div class="split">
          <span class="tag tag-blue">{{ task.type }}</span
          ><span class="tag">{{ taskStatus(task) }}</span>
        </div>
        <h2 class="mt">{{ task.store }}</h2>
        <div class="kv">
          <span>门店编号</span><span>{{ task.code }}</span>
        </div>
        <div class="kv">
          <span>计划时间</span><span>{{ task.time }}</span>
        </div>
        <div class="kv">
          <span>地址</span><span>{{ task.address }}</span>
        </div>
        <div class="kv">
          <span>现场联系人</span><span>李店长 · 138****0000</span>
        </div>
        <div class="progress">
          <span :style="{ width: (done / 7) * 100 + '%' }"></span>
        </div>
        <small>{{ done }} / 7 项作业已确认</small>
      </div>
      <div class="section-title">作业步骤<small>建议 ≥30分钟</small></div>
      <div class="panel compact">
        <button
          v-for="(item, i) in steps"
          :key="item[0]"
          class="row"
          type="button"
          @click="go(item[0])"
        >
          <span class="circle" :class="{ done: item[2] === '已完成' }"
            ><Icon v-if="item[2] === '已完成'" name="check" /><span v-else>{{
              i + 1
            }}</span></span
          ><span class="main"
            ><strong>{{ item[1] }}</strong
            ><small>{{ item[3] }}</small></span
          ><span class="tag">{{ item[2] }}</span>
        </button>
      </div>
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        @click="go(s.signatures.client ? 'sync' : 'checkin')"
      >
        {{ s.signatures.client ? "查看同步进度" : "继续作业" }}
      </button></template
    ></PageFrame
  >
</template>
