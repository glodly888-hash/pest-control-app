<script setup>
import { computed } from "vue";
import { useDemo } from "../composables/useDemo.js";
const { service: s, state, locked, go, toast } = useDemo();
const facility = computed(
  () =>
    s.value.facilities.find((f) => f.id === state.selectedFacility) ||
    s.value.facilities[0],
);
function save() {
  const f = facility.value;
  if (f.result === "待检查") return toast("请选择检查结果");
  if (f.result !== "已检查" && !f.reason.trim())
    return toast("请填写未检查或无法确认的原因");
  if (f.result === "已检查" && !f.photos.length)
    return toast("请添加本次检查照片");
  if (
    f.result === "已检查" &&
    f.quantityKind === "明确数量" &&
    ["flies", "others", "rodents", "cockroaches"].some(
      (k) => !Number.isInteger(Number(f[k])) || Number(f[k]) < 0,
    )
  )
    return toast("捕获数量需为非负整数");
  if (
    f.result === "已检查" &&
    (Number(f.flies) +
      Number(f.others) +
      Number(f.rodents) +
      Number(f.cockroaches) >
      0 ||
      f.condition !== "正常") &&
    !f.treatment.trim() &&
    !s.value.treatment.saved
  )
    return toast("请关联处置措施或填写本次未处置原因");
  f.saved = true;
  go("devices");
}
</script>
<template>
  <PageFrame title="设施点检" back="devices"
    ><div class="inner">
      <div class="panel">
        <div class="split">
          <h2>{{ facility.id }} · {{ facility.type }}</h2>
          <span class="tag tag-blue">{{ facility.status }}</span>
        </div>
        <p>
          {{ facility.area }} · {{ facility.floor }} · {{ facility.position }}
        </p>
      </div>
      <EditorGuard
        ><Segmented
          v-model="facility.result"
          label="检查结果"
          :options="['已检查', '未检查', '无法确认']" /><template
          v-if="facility.result === '已检查'"
          ><Segmented
            v-model="facility.condition"
            label="设施状态"
            :options="['正常', '损坏', '缺失']" /><Segmented
            v-model="facility.consumable"
            label="耗材更换"
            :options="['未更换', '已更换', '不适用']" />
          <div class="section-title">捕获数量</div>
          <Segmented
            v-model="facility.quantityKind"
            label="数量是否明确"
            :options="['明确数量', '数量不明']" />
          <div v-if="facility.quantityKind === '明确数量'" class="number-grid">
            <template v-if="['灭蝇灯', '果蝇杯'].includes(facility.type)"
              ><FormField
                v-model="facility.flies"
                type="number"
                min="0"
                label="飞虫（含小型飞虫）" /><FormField
                v-model="facility.others"
                type="number"
                min="0"
                label="其他虫类" /></template
            ><FormField
              v-else-if="facility.type === '粘鼠板'"
              v-model="facility.rodents"
              type="number"
              min="0"
              label="鼠类"
            /><FormField
              v-else
              v-model="facility.cockroaches"
              type="number"
              min="0"
              label="蟑螂"
            />
          </div>
          <PhotoField
            v-model="facility.photos"
            label="本次点检照片（至少1张）" />
          <div class="notice">
            有捕获或设施异常时，请关联处置或填写本次未处置原因。
          </div>
          <button type="button" class="btn full" @click="go('treatment')">
            关联防治措施</button
          ><FormField
            v-model="facility.treatment"
            type="textarea"
            label="关联措施或未处置原因"
            placeholder="例如：清洁处理并更换粘捕耗材" /></template
        ><template v-else-if="facility.result !== '待检查'"
          ><FormField
            v-model="facility.reason"
            type="textarea"
            label="本次未检查或无法确认的原因"
            required
          />
          <div class="notice">
            本条不计为正常或零捕获，历史数量不会自动带入本次统计。
          </div></template
        ><FormField v-model="facility.note" type="textarea" label="备注"
      /></EditorGuard>
    </div>
    <template #footer
      ><button
        type="button"
        class="btn primary"
        :disabled="locked"
        @click="save"
      >
        保存点检
      </button></template
    ></PageFrame
  >
</template>
