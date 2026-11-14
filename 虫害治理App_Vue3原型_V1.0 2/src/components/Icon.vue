<script setup>
import { icons } from "../data/icons.js";
defineProps({ name: { type: String, default: "file" } });
</script>
<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="icons[name] || icons.file"
  ></svg>
</template>
