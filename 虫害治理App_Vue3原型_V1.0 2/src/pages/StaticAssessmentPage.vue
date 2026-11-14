<script setup>
import { useDemo } from "../composables/useDemo.js";
const { service: s, locked, toast, saveModule } = useDemo();
function save() {
  if (!s.value.assessment.confirmed) return toast("请确认已核对本次环境情况");
  if (Number(s.value.assessment.wells) < 0) return toast("污水井数量不能为负");
  saveModule("static", "devices");
}
</script>
<template>
  <PageFrame title="门店静态评估" back="detail"
    ><div class="strip">
      <Icon name="clock" /><span>参考上次评估 · 2026-10-11</span>
    </div>
    <div class="inner">
      <EditorGuard
        ><Segmented
          v-model="s.assessment.storeType"
          label="门店类型"
          :options="['封闭式', '开放式', '无法确认']"
        /><Segmented
          v-model="s.assessment.shutter"
          label="卷帘门"
          :options="['有', '无', '无法确认']"
        /><Segmented
          v-model="s.assessment.folding"
          label="折叠门"
          :options="['有', '无', '无法确认']"
        /><Segmented
          v-model="s.assessment.air"
          label="风幕机状态"
          :options="['开启', '未开启', '无设备']"
        /><Segmented
          v-model="s.assessment.angle"
          label="风幕机出风角度"
          :options="['符合', '不符合', '不适用']"
        /><Segmented
          v-model="s.assessment.ceiling"
          label="开放式吊顶"
          :options="['有', '无', '无法确认']"
        /><FormField
          v-model="s.assessment.wells"
          type="number"
          min="0"
          label="周边污水井数量"
        /><Segmented
          v-model="s.assessment.plants"
          label="周边绿植"
          :options="['有', '无', '无法确认']"
        /><Segmented
          v-model="s.assessment.renovation"
          label="周边装修商铺"
          :options="['有', '无', '无法确认']"
        /><Segmented
          v-model="s.assessment.vacancy"
          label="周边空置商铺"
          :options="['有', '无', '无法确认']"
        /><PhotoField
          v-model="s.assessment.photos"
          label="环境照片"
        /><FormField
          v-model="s.assessment.note"
          label="备注"
          type="textarea"
        /><label class="checkline"
          ><input v-model="s.assessment.confirmed" type="checkbox" /><span
            >已核对本次环境情况，与当前现场一致</span
          ></label
        ></EditorGuard
      >
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        :disabled="locked"
        @click="save"
      >
        保存本次评估
      </button></template
    ></PageFrame
  >
</template>
