<script setup>
import Icon from "./Icon.vue";
import { go } from "../composables/useDemo.js";
defineProps({ title: String, back: String, nav: String });
</script>
<template>
  <div class="screen">
    <header class="appbar">
      <button
        v-if="back"
        type="button"
        class="icon-button"
        aria-label="返回"
        @click="go(back)"
      >
        <Icon name="back" />
      </button>
      <h1>{{ title }}</h1>
      <slot name="action" />
    </header>
    <main class="phone-content"><slot /></main>
    <footer v-if="$slots.footer" class="app-footer">
      <slot name="footer" />
    </footer>
    <nav v-if="nav" class="bottomnav" aria-label="应用导航">
      <button
        v-for="item in [
          ['home', 'home', '工作台'],
          ['tasks', 'tasks', '任务'],
          ['risks', 'risk', '风险'],
          ['profile', 'user', '我的'],
        ]"
        :key="item[0]"
        type="button"
        :class="{ active: nav === item[0] }"
        :aria-current="nav === item[0] ? 'page' : undefined"
        @click="go(item[0])"
      >
        <Icon :name="item[1]" /><span>{{ item[2] }}</span>
      </button>
    </nav>
  </div>
</template>
