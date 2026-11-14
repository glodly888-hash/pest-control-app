<script setup>
import { computed } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { service: s, state, locked, go, toast } = useDemo();
const risk = computed(
  () =>
    s.value.risks.find((r) => r.id === state.selectedRisk) || s.value.risks[0],
);
const options = computed(() =>
  risk.value.state === "已关闭"
    ? ["重开"]
    : [
        "未改善",
        "整改中",
        "提交复核",
        ...(risk.value.state === "待复核" ? ["复核通过", "复核不通过"] : []),
        "本次未核查",
      ],
);
function save() {
  const r = risk.value;
  if (!options.value.includes(r.result))
    return toast("请选择当前状态允许的跟进结果");
  if (!r.note.trim()) return toast("请填写跟进说明或未核查原因");
  if (r.result === "复核通过" && !r.followupPhotos.length)
    return toast("复核通过需添加改善照片");
  const from = r.state;
  const statuses = {
    整改中: "整改中",
    提交复核: "待复核",
    复核通过: "已关闭",
    复核不通过: "整改中",
    重开: "整改中",
  };
  r.state = statuses[r.result] || r.state;
  r.checked = true;
  r.followups.push({
    date: new Date().toLocaleString("zh-CN"),
    result: r.result,
    note: r.note,
    from,
    to: r.state,
    material: r.material,
  });
  go("risks");
}
</script>
<template>
  <PageFrame title="整改跟进" back="risks"
    ><div class="inner">
      <div class="split">
        <span class="tag">{{ risk.type }}</span
        ><span class="tag tag-blue">{{ risk.state }}</span>
      </div>
      <h2 class="risk-title">{{ risk.title }}</h2>
      <p class="muted">
        {{ risk.area }} · {{ risk.floor }} · {{ risk.position }}
      </p>
      <PhotoField :model-value="risk.photos" label="风险证据" readonly />
      <div class="panel">
        <div class="kv"><span>发现时间</span><span>2026-10-11</span></div>
        <div class="kv">
          <span>责任方</span><span>{{ risk.responsible }}</span>
        </div>
        <div class="kv">
          <span>整改期限</span><span>{{ risk.due }}</span>
        </div>
        <p>{{ risk.advice }}</p>
      </div>
      <div class="section-title">跟进记录</div>
      <div class="timeline">
        <div
          v-for="(f, i) in [...risk.followups].reverse()"
          :key="i"
          class="timeline-item"
        >
          <strong>{{ f.date }} · {{ f.result }}</strong>
          <p>{{ f.note }}</p>
          <small>{{ f.material }} · {{ f.from }} → {{ f.to }}</small>
        </div>
        <div class="timeline-item">
          <strong>10月11日 · 首次发现</strong>
          <p>新增风险并指派门店跟进。</p>
        </div>
      </div>
      <EditorGuard
        ><FormField
          v-model="risk.result"
          label="本次跟进结果"
          :options="options" /><FormField
          v-model="risk.note"
          label="跟进说明或未核查原因"
          type="textarea"
          required /><FormField
          v-model="risk.material"
          label="材料来源"
          :options="['现场记录', '客户提供', '后台代录']" /><PhotoField
          v-model="risk.followupPhotos"
          label="改善或跟进照片"
      /></EditorGuard>
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        :disabled="locked"
        @click="save"
      >
        保存跟进
      </button></template
    ></PageFrame
  >
</template>
