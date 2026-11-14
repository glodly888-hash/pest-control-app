<script setup>
import { ref, computed, onMounted, onUnmounted } from "vue";
import { useDemo } from "../composables/useDemo.js";
import ReportBody from "../components/ReportBody.vue";
const { service: s, task, go } = useDemo();
const query = new URLSearchParams(location.search);
const back = ["staffsign", "clientsign", "reportdone"].includes(
  query.get("return"),
)
  ? query.get("return")
  : "summary";
const published = computed(() =>
  query.has("version")
    ? s.value.publishedReports.find(
        (r) => r.version === Number(query.get("version")),
      )
    : null,
);
const record = computed(
  () =>
    published.value?.snapshot ||
    s.value.snapshot || {
      task: task.value,
      content: s.value,
      version: s.value.version,
    },
);
const signatures = computed(
  () => published.value?.signatures || s.value.signatures,
);
const elapsed = ref(0);
const remaining = computed(() => Math.max(0, Math.ceil(5 - elapsed.value)));
let timer, last;
onMounted(() => {
  last = performance.now();
  timer = setInterval(() => {
    const now = performance.now();
    if (!document.hidden) elapsed.value += Math.min((now - last) / 1000, 0.3);
    last = now;
  }, 100);
});
onUnmounted(() => clearInterval(timer));
function finish() {
  if (remaining.value && !published.value) return;
  if (!published.value) s.value.reviewVersion = s.value.version;
  go(back);
}
</script>
<template>
  <PageFrame title="报告预览" :back="back"
    ><div class="strip">
      <Icon name="file" /><span>{{
        published
          ? "已归档演示版本 · 内容保持不变"
          : "本次真实填写内容 · 尚未发布正式报告"
      }}</span>
    </div>
    <div class="inner">
      <ReportBody
        :record="record"
        :signatures="signatures"
        :report-number="published?.number"
      />
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        :disabled="remaining > 0 && !published"
        @click="finish"
      >
        {{
          published
            ? "返回归档记录"
            : remaining > 0
              ? "请阅读报告（" + remaining + "秒）"
              : "已预览，返回确认"
        }}
      </button></template
    ></PageFrame
  >
</template>
