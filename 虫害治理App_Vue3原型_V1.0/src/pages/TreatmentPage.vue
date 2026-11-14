<script setup>
import { useDemo } from "../composables/useDemo.js";
const { service: s, locked, toast, saveModule } = useDemo();
function save() {
  const t = s.value.treatment;
  if (!t.position.trim() || !t.targets.length)
    return toast("请填写处理位置并选择关联来源");
  if (t.type === "本次未处置" && !t.reason.trim())
    return toast("请填写未处置原因");
  if (
    t.type === "药剂处理" &&
    ["drug", "form", "method", "concentration", "dose", "unit"].some(
      (k) => !String(t[k]).trim(),
    )
  )
    return toast("请填写实际药剂、剂型、方法、浓度、用量及单位");
  if (
    t.type === "药剂处理" &&
    (!Number.isFinite(Number(t.dose)) || Number(t.dose) <= 0)
  )
    return toast("施用量应大于0");
  if (t.type !== "本次未处置" && t.type !== "药剂处理" && !t.description.trim())
    return toast("请填写处理说明");
  t.saved = true;
  saveModule("treatment", "summary");
}
</script>
<template>
  <PageFrame title="防治措施" back="detail"
    ><div class="inner">
      <EditorGuard
        ><div class="panel">
          <h2>关联发现与处理</h2>
          <p>分别记录虫情来源，措施可关联多个发现。</p>
          <label
            v-for="source in [
              '客户反馈',
              '现场发现',
              '设施捕获',
              '风险整改',
              '预防性处理',
            ]"
            :key="source"
            class="checkline"
            ><input
              v-model="s.treatment.targets"
              type="checkbox"
              :value="source"
            /><span>{{ source }}</span></label
          >
        </div>
        <FormField
          v-model="s.treatment.type"
          label="处置措施"
          :options="[
            '清洁处理',
            '结构封堵',
            '设施调整',
            '物理捕杀',
            '药剂处理',
            '观察复查',
            '本次未处置',
          ]"
          required /><FormField
          v-model="s.treatment.position"
          label="处理位置"
          required /><template v-if="s.treatment.type === '药剂处理'"
          ><div class="notice">
            演示仅记录实际使用信息，不推荐药剂、浓度或配比。
          </div>
          <FormField
            v-model="s.treatment.drug"
            label="公司确认的药剂名称"
            placeholder="请按实际公司字典填写"
            required />
          <div class="number-grid">
            <FormField
              v-model="s.treatment.form"
              label="剂型"
              required
            /><FormField
              v-model="s.treatment.method"
              label="处理方法"
              required
            /><FormField
              v-model="s.treatment.concentration"
              label="配比浓度"
              required
            /><FormField
              v-model="s.treatment.dose"
              label="施用量"
              type="number"
              min="0"
              required
            />
          </div>
          <FormField
            v-model="s.treatment.doseType"
            label="用量口径"
            :options="['原药用量', '配制液用量', '单位面积用量']" /><FormField
            v-model="s.treatment.unit"
            label="单位"
            required /></template
        ><FormField
          v-else-if="s.treatment.type === '本次未处置'"
          v-model="s.treatment.reason"
          label="本次未处置原因"
          type="textarea"
          required /><FormField
          v-else
          v-model="s.treatment.description"
          label="处理说明"
          type="textarea"
          required /><PhotoField
          v-model="s.treatment.photos"
          label="处置留证" /><FormField
          v-model="s.treatment.nextFocus"
          label="下次关注事项"
          type="textarea"
      /></EditorGuard>
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        :disabled="locked"
        @click="save"
      >
        保存措施
      </button></template
    ></PageFrame
  >
</template>
