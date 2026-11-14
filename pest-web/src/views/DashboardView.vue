<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMe, listTasks } from '@/api'

const router = useRouter()
const name = ref('')
const todayCount = ref<number | null>(null)

onMounted(async () => {
  try {
    const me = await getMe()
    name.value = me.name ?? ''
  } catch {
    // getMe 失败（token 失效等）由 http 拦截器统一跳登录
    return
  }
  // 今日任务数（按门店本地日期）
  try {
    const today = new Date()
    const y = today.getFullYear()
    const m = String(today.getMonth() + 1).padStart(2, '0')
    const d = String(today.getDate()).padStart(2, '0')
    const date = `${y}-${m}-${d}`
    const res = await listTasks({ startDate: date, endDate: date, limit: 100 })
    todayCount.value = res.items.length
  } catch {
    todayCount.value = null
  }
})
</script>

<template>
  <div class="dash">
    <header class="topbar">
      <span class="title">工作台</span>
      <span class="user">{{ name }}</span>
    </header>
    <div class="content">
      <button class="entry card" @click="router.push('/tasks')">
        <span>任务列表</span>
        <span v-if="todayCount !== null" class="badge">今日 {{ todayCount }}</span>
      </button>
      <button class="entry card" @click="router.push('/stores')">门店列表</button>
      <div class="card placeholder">签到、巡检、虫情、风险等现场作业将在后续工作包接入</div>
    </div>
  </div>
</template>

<style scoped>
.dash {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--c-primary);
  color: #fff;
}
.title {
  font-size: 17px;
}
.user {
  font-size: 13px;
  opacity: 0.85;
}
.content {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.entry {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 16px;
  text-align: left;
  cursor: pointer;
  color: var(--c-text);
}
.badge {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  background: #eaf2fa;
  color: #1b4f8a;
}
.placeholder {
  color: var(--c-text-weak);
  font-size: 13px;
}
</style>
