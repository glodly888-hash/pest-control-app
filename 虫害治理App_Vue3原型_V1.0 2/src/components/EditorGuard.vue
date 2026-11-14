<script setup>
import { useDemo } from "../composables/useDemo.js";
const { locked, modal, startRevision } = useDemo();
function edit() {
  modal(
    "建立新的内容版本",
    "原签字不能用于修改后的内容；已发布的旧报告会继续保留。",
    "开始修改",
    startRevision,
  );
}
</script>
<template>
  <div v-if="locked" class="notice">
    <strong>本次内容已签字锁定</strong>
    <p>修改后需要重新完成双方确认。</p>
    <button type="button" class="text-button" @click="edit">
      建立新版本后修改
    </button>
  </div>
  <fieldset :disabled="locked" class="editor"><slot /></fieldset>
</template>
