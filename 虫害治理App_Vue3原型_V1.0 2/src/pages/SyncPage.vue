<script setup>
import { ref, computed } from "vue";
import { useDemo } from "../composables/useDemo.js";
import { clone } from "../data/seed.js";
const { state, service: s, task, go, toast } = useDemo();
const busy = ref(false);
const percent = ref(0);
const failure = ref("正常同步");
const valid = computed(
  () =>
    s.value.snapshot?.version === s.value.version &&
    s.value.signatures.staff?.version === s.value.version &&
    s.value.signatures.client?.version === s.value.version,
);
const existing = computed(() =>
  s.value.publishedReports.some((r) => r.version === s.value.version),
);
async function sync() {
  if (busy.value) return;
  if (state.offline) return toast("当前为离线演示，请先切换在线");
  if (!valid.value) return toast("请先完成本次版本的双方签字");
  if (existing.value) return go("reportdone");
  busy.value = true;
  s.value.syncState = "同步中";
  s.value.syncFailure = "";
  for (const value of [25, 55, 80, 100]) {
    await new Promise((resolve) => setTimeout(resolve, 180));
    percent.value = value;
  }
  if (failure.value !== "正常同步") {
    s.value.syncState = "失败";
    s.value.syncFailure =
      failure.value === "任务已改派"
        ? "任务分配已变更，资料已保留为异常记录，等待主管处理。"
        : failure.value === "附件上传失败"
          ? "有照片尚未上传，请检查网络并重试。"
          : "PDF生成失败，当前服务不能标记完成。";
    busy.value = false;
    return;
  }
  // 原型只模拟发布状态，服务端校验、上传与PDF生成由正式接口实现。
  if (state.offline) {
    s.value.syncState = "失败";
    s.value.syncFailure = "网络已断开，记录仍保留在本机。";
    busy.value = false;
    return;
  }
  if (!s.value.publishedReports.some((r) => r.version === s.value.version))
    s.value.publishedReports.push({
      number:
        "DEMO-" +
        task.value.code +
        "-" +
        task.value.date.replaceAll("-", "") +
        "-V" +
        s.value.version,
      version: s.value.version,
      snapshot: clone(s.value.snapshot),
      signatures: clone(s.value.signatures),
      endedAt: s.value.endedAt,
      publishedAt: new Date().toISOString(),
    });
  s.value.syncState = "已发布";
  busy.value = false;
  go("reportdone");
}
function download() {
  if (state.offline) return toast("任务下载需要联网");
  state.downloaded = true;
  toast("示例任务资料已缓存到本机");
}
</script>
<template>
  <PageFrame title="同步中心" back="home"
    ><div class="inner">
      <div class="sync-big">
        <span class="square"><Icon name="sync" /></span>
        <div>
          <strong>{{
            busy
              ? "正在同步"
              : state.offline
                ? "当前离线"
                : existing
                  ? "本次已同步"
                  : "等待同步"
          }}</strong
          ><small>{{
            state.offline
              ? "填写与签名已保存在本机"
              : "联网后可上传本次服务资料"
          }}</small>
        </div>
      </div>
      <div class="panel">
        <h2>{{ task.store }}</h2>
        <p>内容版本 V{{ s.version }}</p>
        <div class="row">
          <span class="circle" :class="{ done: !!s.signatures.staff }"
            ><Icon name="check"
          /></span>
          <div class="main">
            <strong>员工签字</strong
            ><small>{{ s.signatures.staff ? "已确认" : "待员工确认" }}</small>
          </div>
        </div>
        <div class="row">
          <span class="circle" :class="{ done: !!s.signatures.client }"
            ><Icon name="check"
          /></span>
          <div class="main">
            <strong>客户签字</strong
            ><small>{{
              s.signatures.client ? "已确认" : s.client.refusal || "待客户确认"
            }}</small>
          </div>
        </div>
        <div class="row">
          <span class="circle" :class="{ done: existing }"
            ><Icon name="file"
          /></span>
          <div class="main">
            <strong>附件与报告</strong
            ><small>{{
              existing ? "发布流程演示完成" : "待上传、校验及生成"
            }}</small>
          </div>
        </div>
        <div
          v-if="busy"
          class="progress"
          role="progressbar"
          :aria-valuenow="percent"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span :style="{ width: percent + '%' }" />
        </div>
      </div>
      <div v-if="s.syncFailure" class="notice warning" role="alert">
        <strong>同步未完成</strong>
        <p>{{ s.syncFailure }}</p>
        <p>当前本机记录和签字仍保留。</p>
      </div>
      <button
        v-if="!valid"
        type="button"
        class="btn full mb"
        @click="go(s.signatures.staff ? 'clientsign' : 'staffsign')"
      >
        继续完成确认
      </button>
      <div class="panel">
        <div class="split">
          <strong>离线任务资料</strong
          ><span class="tag">{{ state.downloaded ? "已缓存" : "未下载" }}</span>
        </div>
        <p>门店、设施、未关闭风险及本次记录。</p>
        <button type="button" class="text-button" @click="download">
          更新示例任务缓存
        </button>
      </div>
      <details class="demo-options">
        <summary>原型测试：同步异常</summary>
        <FormField
          v-model="failure"
          label="模拟结果"
          :options="['正常同步', '附件上传失败', 'PDF生成失败', '任务已改派']"
        />
        <p class="hint">
          本原型演示状态变化，不连接服务器，不生成正式PDF。改派异常的真实处理需主管权限。
        </p>
      </details>
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        :disabled="busy || state.offline || !valid"
        @click="sync"
      >
        {{
          busy
            ? "正在同步…"
            : existing
              ? "查看报告记录"
              : s.syncFailure
                ? "重试同步（演示）"
                : "同步并发布报告（演示）"
        }}
      </button></template
    ></PageFrame
  >
</template>
