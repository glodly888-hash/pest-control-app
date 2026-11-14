<script setup>
const model = defineModel();
defineProps({ label: String, options: Array });
</script>
<template>
  <div class="field">
    <span>{{ label }}</span>
    <div class="segmented" role="group" :aria-label="label">
      <button
        v-for="item in options"
        :key="item"
        type="button"
        :class="{ active: model === item }"
        :aria-pressed="model === item"
        @click="model = item"
      >
        {{ item }}
      </button>
    </div>
  </div>
</template>
