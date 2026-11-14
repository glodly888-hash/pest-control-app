<script setup>
import Icon from "./Icon.vue";
import { selectTask, taskStatus } from "../composables/useDemo.js";
defineProps({ task: Object });
</script>
<template>
  <button type="button" class="task-card" @click="selectTask(task.id)">
    <div class="split">
      <span class="time">{{ task.time }}</span
      ><span
        class="tag"
        :class="{ 'tag-blue': taskStatus(task) !== '待执行' }"
        >{{ taskStatus(task) }}</span
      >
    </div>
    <h3>{{ task.store }}</h3>
    <p><Icon name="pin" />{{ task.address }}</p>
    <div class="card-bottom">
      <span>{{ task.type }} · 门店 {{ task.code }}</span
      ><span class="link-label"
        >{{ taskStatus(task) === "已完成" ? "查看服务" : "进入作业" }}
        <Icon name="chevron"
      /></span>
    </div>
  </button>
</template>
