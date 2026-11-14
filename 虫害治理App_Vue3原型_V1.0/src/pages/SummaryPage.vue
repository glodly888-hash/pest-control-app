<script setup>
import { computed } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { service: s, locked, go, validation, toast } = useDemo();
const errors = computed(validation);
const minutes = computed(() =>
  s.value.checkin.time
    ? Math.max(
        0,
        Math.floor((Date.now() - Date.parse(s.value.checkin.time)) / 60000),
      )
    : 0,
);
function draft() {
  s.value.summary.text = `本次已记录${s.value.facilities.filter((f) => f.saved).length}处设施检查，跟进${s.value.risks.filter((r) => r.checked).length}条风险。已登记${s.value.treatment.type}，仍有${s.value.risks.filter((r) => r.state !== "已关闭").length}条风险需持续整改。`;
}
function next() {
  if (errors.value.length) return toast("请按检查清单补充缺失内容");
  go("staffsign");
}
</script>
<template>
  <PageFrame title="服务总结" back="detail"
    ><div class="inner">
      <div class="panel">
        <div class="split">
          <h2>提交前检查</h2>
          <span
            class="tag"
            :class="errors.length ? 'tag-warn' : 'tag-success'"
            >{{ errors.length ? errors.length + "项待补充" : "已通过" }}</span
          >
        </div>
        <ul v-if="errors.length" class="validation-list">
          <li v-for="e in errors" :key="e.label">
            <button type="button" @click="go(e.page)">{{ e.label }}</button>
          </li>
        </ul>
        <div v-else class="row">
          <span class="circle done"><Icon name="check" /></span
          ><span class="main"
            ><strong>本次作业已核对</strong
            ><small>接下来由员工和客户现场确认</small></span
          >
        </div>
      </div>
      <EditorGuard
        ><div class="split">
          <strong>本次服务总结</strong
          ><button type="button" class="text-button" @click="draft">
            生成总结草稿
          </button>
        </div>
        <FormField
          v-model="s.summary.text"
          label="总结内容"
          type="textarea"
          :rows="5"
          required
        /><FormField
          v-model="s.summary.cooperation"
          label="客户需配合事项"
          type="textarea"
        /><Segmented
          v-model="s.summary.level"
          label="综合虫害风险等级"
          :options="['低', '中', '高', '未评估']"
        /><FormField
          v-model="s.summary.basis"
          label="风险评定依据"
          type="textarea"
          required
        /><Segmented
          v-model="s.summary.weather"
          label="天气情况"
          :options="['晴', '多云', '雨', '其他']"
        />
        <div class="panel">
          <div class="kv">
            <span>当前服务时长</span><span>{{ minutes }}分钟</span>
          </div>
          <div class="kv">
            <span>未关闭风险</span
            ><span
              >{{ s.risks.filter((r) => r.state !== "已关闭").length }}条 ·
              持续跟进</span
            >
          </div>
        </div>
        <FormField
          v-if="minutes < 30"
          v-model="s.summary.durationReason"
          label="低于30分钟建议时长的原因"
          type="textarea"
          required
        />
        <p class="hint">
          无需等待满30分钟。签字后更改内容，需要重新确认。
        </p></EditorGuard
      >
    </div>
    <template #footer
      ><button type="button" class="btn primary" @click="next">
        核对报告并签字
      </button></template
    ></PageFrame
  >
</template>
