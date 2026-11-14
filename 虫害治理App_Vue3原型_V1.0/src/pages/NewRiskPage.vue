<script setup>
import { reactive } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { service: s, locked, go, toast } = useDemo();
const form = reactive({
  type: "建筑结构",
  pest: "鼠类",
  area: "后区",
  floor: "三楼",
  position: "",
  title: "",
  advice: "",
  responsible: "门店工程负责人",
  due: "2026-11-15",
  photos: [],
});
function save() {
  if (
    !form.title.trim() ||
    !form.position.trim() ||
    !form.responsible.trim() ||
    !form.due
  )
    return toast("请填写位置、风险描述、责任方和日期");
  if (!form.photos.length) return toast("请添加风险现场照片");
  s.value.risks.push({
    ...JSON.parse(JSON.stringify(form)),
    id: crypto.randomUUID(),
    state: "待整改",
    checked: true,
    result: "未改善",
    note: "",
    followups: [],
    followupPhotos: [],
    material: "现场记录",
  });
  go("risks");
}
</script>
<template>
  <PageFrame title="新增风险" back="risks"
    ><div class="inner">
      <EditorGuard
        ><FormField
          v-model="form.type"
          label="风险类型"
          :options="['建筑结构', '卫生清洁', '设施管理', '外部环境', '其他']"
          required
        /><FormField
          v-model="form.pest"
          label="关联虫害"
          :options="['鼠类', '蟑螂', '飞虫', '其他']"
        />
        <div class="number-grid">
          <FormField
            v-model="form.area"
            label="区域"
            :options="['后区', '客区', '厨房', '仓库']"
          /><FormField v-model="form.floor" label="楼层" />
        </div>
        <FormField
          v-model="form.position"
          label="具体位置"
          placeholder="例如：配电柜上方穿墙孔洞"
          required
        /><FormField
          v-model="form.title"
          label="风险描述"
          type="textarea"
          required
        /><PhotoField
          v-model="form.photos"
          label="风险照片（至少1张）"
        /><FormField
          v-model="form.advice"
          label="建议改进措施"
          type="textarea"
        /><FormField
          v-model="form.responsible"
          label="整改责任方"
          required
        /><FormField
          v-model="form.due"
          label="计划完成日期"
          type="date"
          required
        />
        <div class="notice">
          未发现虫体，也可以登记结构或卫生风险，无需虚构捕获数量或用药。
        </div></EditorGuard
      >
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        :disabled="locked"
        @click="save"
      >
        保存风险
      </button></template
    ></PageFrame
  >
</template>
