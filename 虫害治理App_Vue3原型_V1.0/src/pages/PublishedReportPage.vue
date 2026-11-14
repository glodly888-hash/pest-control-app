<script setup>
import { ref, computed } from "vue";
import { useDemo, downloadFile } from "../composables/useDemo.js";
const { service: s, go, modal, startRevision } = useDemo();
const chosen = ref(s.value.publishedReports.at(-1)?.version || 0);
const report = computed(() =>
  s.value.publishedReports.find((r) => r.version === Number(chosen.value)),
);
const pending = computed(
  () =>
    report.value?.snapshot.content.risks.filter((r) => r.state !== "已关闭")
      .length || 0,
);
function revise() {
  modal(
    "创建报告更正版本",
    "已发布版本会继续保留。新版本修改后，需要员工与客户重新签字确认。",
    "创建新版本",
    () => {
      startRevision();
      go("summary");
    },
  );
}
function share() {
  modal(
    "报告分享交互说明",
    "正式 App 将调用安卓系统分享已生成的PDF。本原型尚未接入后端，不会发送任何文件。",
  );
}
function exportRecord() {
  downloadFile(
    report.value.number + ".json",
    JSON.stringify(report.value, null, 2),
    "application/json",
  );
}
</script>
<template>
  <PageFrame title="服务报告" back="home"
    ><div v-if="report" class="inner">
      <div class="success-mark"><Icon name="check" /></div>
      <h2 class="success-title">服务已完成 · 演示</h2>
      <p class="center hint">双方签字与发布流程已完成演示</p>
      <div class="panel">
        <h2>{{ report.snapshot.task.store }}</h2>
        <p class="report-id">{{ report.number }}</p>
        <div class="kv">
          <span>内容版本</span><span>V{{ report.version }}</span>
        </div>
        <div class="kv">
          <span>现场完成</span
          ><span>{{
            new Date(report.endedAt).toLocaleString("zh-CN", { hour12: false })
          }}</span>
        </div>
        <div class="kv">
          <span>当前版本状态</span
          ><span>{{
            report.version === s.publishedReports.at(-1)?.version
              ? "最新归档"
              : "已被新版替代"
          }}</span>
        </div>
        <button
          type="button"
          class="btn full mt"
          @click="
            go('report', { return: 'reportdone', version: report.version })
          "
        >
          查看归档报告
        </button>
      </div>
      <div class="notice">
        <strong>{{ pending }} 条风险仍需跟进</strong>
        <p>服务完成不代表风险关闭，整改情况将在后续服务中继续记录。</p>
      </div>
      <FormField
        v-if="s.publishedReports.length > 1"
        v-model="chosen"
        label="历史报告版本"
        :options="
          s.publishedReports.map((r) => ({
            label: 'V' + r.version + ' · ' + r.number,
            value: r.version,
          }))
        "
      />
      <div class="panel compact">
        <button type="button" class="row" @click="exportRecord">
          <Icon name="download" /><span class="main"
            ><strong>导出演示记录</strong
            ><small>JSON格式，包含本次内容快照</small></span
          ><Icon name="arrow" /></button
        ><button type="button" class="row" @click="share">
          <Icon name="share" /><span class="main"
            ><strong>分享服务报告</strong
            ><small>查看正式App的分享方式</small></span
          ><Icon name="arrow" /></button
        ><button type="button" class="row" @click="revise">
          <Icon name="edit" /><span class="main"
            ><strong>发起报告更正</strong
            ><small>保留旧版本，重新双方确认</small></span
          ><Icon name="arrow" />
        </button>
      </div>
      <p class="hint">当前为交互原型，尚未生成具有正式编号的PDF文件。</p>
    </div>
    <div v-else class="empty-state">
      <Icon name="file" />
      <h3>报告尚未发布</h3>
      <p>完成双方确认并同步后，报告将在这里归档。</p>
      <button type="button" class="btn primary mt" @click="go('sync')">
        前往同步中心
      </button>
    </div>
    <template #footer
      ><button type="button" class="btn primary" @click="go('home')">
        返回工作台
      </button></template
    ></PageFrame
  >
</template>
