<script setup>
import { useDemo } from "../composables/useDemo.js";
const { service: s, locked, toast, saveModule } = useDemo();
function save() {
  const d = s.value.intake;
  if (!d.notice) return toast("请先确认服务须知");
  if (d.feedback === "未询问" || d.onsite === "未检查")
    return toast("请明确记录客户反馈与现场发现");
  if (
    d.feedback === "有发现" &&
    d.quantityKind === "明确数量" &&
    (!Number.isInteger(Number(d.quantity)) || Number(d.quantity) < 0)
  )
    return toast("数量需为非负整数");
  if (
    d.onsite === "有发现" &&
    d.onsiteQuantityKind === "明确数量" &&
    (d.onsiteQuantity === "" ||
      !Number.isInteger(Number(d.onsiteQuantity)) ||
      Number(d.onsiteQuantity) < 0)
  )
    return toast("现场发现数量需为非负整数");
  saveModule("intake", "static");
}
</script>
<template>
  <PageFrame title="服务前确认" back="detail"
    ><div class="inner">
      <EditorGuard
        ><div class="panel">
          <h2>服务须知</h2>
          <p>
            确认个人防护、现场沟通与食品保护措施，了解门店近期虫害反馈后开始检查。
          </p>
          <label class="checkline"
            ><input v-model="s.intake.notice" type="checkbox" /><span
              >已阅读并完成服务前沟通</span
            ></label
          >
        </div>
        <div class="section-title">客户反馈<small>独立记录来源</small></div>
        <Segmented
          v-model="s.intake.feedback"
          label="近期是否发现虫害"
          :options="['未询问', '未发现', '有发现']"
        /><template v-if="s.intake.feedback === '有发现'"
          ><FormField
            v-model="s.intake.pest"
            label="虫害类型"
            :options="['鼠类', '蟑螂', '飞虫', '其他']" /><FormField
            v-model="s.intake.area"
            label="发现区域" /><Segmented
            v-model="s.intake.quantityKind"
            label="数量是否明确"
            :options="['明确数量', '数量不明']" /><FormField
            v-if="s.intake.quantityKind === '明确数量'"
            v-model="s.intake.quantity"
            label="反馈数量"
            type="number"
            min="0" /><FormField
            v-model="s.intake.description"
            label="反馈描述"
            type="textarea"
        /></template>
        <div class="section-title">现场发现</div>
        <Segmented
          v-model="s.intake.onsite"
          label="是否发现虫体或虫迹"
          :options="['未检查', '未发现', '有发现']"
        /><template v-if="s.intake.onsite === '有发现'"
          ><FormField
            v-model="s.intake.onsitePest"
            label="现场虫害类型"
            :options="['鼠类', '蟑螂', '飞虫', '其他']" /><Segmented
            v-model="s.intake.onsiteQuantityKind"
            label="现场数量"
            :options="['明确数量', '数量不明']" /><FormField
            v-if="s.intake.onsiteQuantityKind === '明确数量'"
            v-model="s.intake.onsiteQuantity"
            label="发现数量"
            type="number"
            min="0" /><FormField
            v-model="s.intake.onsiteText"
            label="发现位置及描述"
            type="textarea"
        /></template>
        <div class="notice">
          客户反馈、现场发现与设施捕获分别保存，不自动相加。
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
        保存并继续
      </button></template
    ></PageFrame
  >
</template>
