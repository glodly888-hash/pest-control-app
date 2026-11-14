<script setup>
import { onMounted, ref } from "vue";
const model = defineModel({ default: "" });
defineProps({ label: String });
const canvas = ref(null);
let drawing = false;
let ctx;
let moved = false;
let start;
onMounted(() => {
  ctx = canvas.value.getContext("2d");
  ctx.lineWidth = 3;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  ctx.strokeStyle = "#003D7A";
  if (model.value) {
    const i = new Image();
    i.onload = () => ctx.drawImage(i, 0, 0, 760, 320);
    i.src = model.value;
  }
});
function point(e) {
  const r = canvas.value.getBoundingClientRect();
  return [
    ((e.clientX - r.left) * 760) / r.width,
    ((e.clientY - r.top) * 320) / r.height,
  ];
}
function down(e) {
  drawing = true;
  moved = false;
  start = point(e);
  canvas.value.setPointerCapture(e.pointerId);
  ctx.beginPath();
  ctx.moveTo(...start);
}
function move(e) {
  if (!drawing) return;
  const p = point(e);
  if (Math.hypot(p[0] - start[0], p[1] - start[1]) > 5) moved = true;
  ctx.lineTo(...p);
  ctx.stroke();
}
function up() {
  if (!drawing) return;
  drawing = false;
  if (moved) model.value = canvas.value.toDataURL("image/png");
}
function clear() {
  ctx.clearRect(0, 0, 760, 320);
  model.value = "";
}
</script>
<template>
  <div class="signature-wrap">
    <canvas
      ref="canvas"
      width="760"
      height="320"
      class="signature"
      :aria-label="label + '手写签字区域，请用触屏或鼠标书写'"
      @pointerdown="down"
      @pointermove="move"
      @pointerup="up"
      @pointercancel="up"
    ></canvas>
    <div v-if="!model" class="signature-guide">
      请{{ label }}在此手写签字<small>使用触屏或鼠标书写</small>
    </div>
  </div>
  <div class="sign-labels">
    <small>签名与本次内容版本关联</small
    ><button type="button" class="text-button" @click="clear">清除重签</button>
  </div>
</template>
