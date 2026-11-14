<script setup>
import { ref } from "vue";
import Icon from "./Icon.vue";
import { samplePhoto } from "../data/seed.js";
import { toast } from "../composables/useDemo.js";
const model = defineModel({ default: () => [] });
defineProps({
  label: { type: String, default: "现场照片" },
  readonly: Boolean,
});
const input = ref(null);
async function upload(event) {
  const files = [...event.target.files];
  for (const file of files) {
    if (
      !["image/jpeg", "image/png", "image/webp"].includes(file.type) ||
      file.size > 800 * 1024
    ) {
      toast("请选择800KB以内的JPG、PNG或WebP图片");
      continue;
    }
    const url = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    }).catch(() => null);
    if (url)
      model.value.push({
        id: crypto.randomUUID(),
        label: file.name,
        url,
        sample: false,
      });
    else toast("图片读取失败，请重试");
  }
  event.target.value = "";
}
</script>
<template>
  <div class="photo-field">
    <div class="section-title">
      {{ label }}<small v-if="!readonly">本机演示留证</small>
    </div>
    <div class="photo-row">
      <figure v-for="(photo, index) in model" :key="photo.id" class="photo">
        <img v-if="photo.url" :src="photo.url" :alt="photo.label" /><template
          v-else
          ><Icon name="camera" /><span>{{ photo.label }}</span
          ><small>示例图片</small></template
        ><button
          v-if="!readonly"
          class="photo-remove"
          type="button"
          :aria-label="'移除' + photo.label"
          @click="model.splice(index, 1)"
        >
          ×
        </button>
      </figure>
      <button
        v-if="!readonly"
        type="button"
        class="photo add"
        @click="model.push(samplePhoto('新增现场示例'))"
      >
        <Icon name="plus" /><span>添加示意</span>
      </button>
    </div>
    <button
      v-if="!readonly"
      type="button"
      class="text-button"
      @click="input.click()"
    >
      选择本机图片</button
    ><input
      ref="input"
      hidden
      type="file"
      accept="image/jpeg,image/png,image/webp"
      multiple
      @change="upload"
    />
  </div>
</template>
