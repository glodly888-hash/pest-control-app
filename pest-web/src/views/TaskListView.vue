<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listTasks, type Task } from '@/api'

const router = useRouter()
const tasks = ref<Task[]>([])
const loading = ref(true)
const error = ref('')
const filterStatus = ref('')

const statusOptions = [
  { value: '', label: '全部' },
  { value: 'PENDING', label: '待执行' },
  { value: 'IN_PROGRESS', label: '进行中' },
  { value: 'AWAITING_CONFIRMATION', label: '待确认' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
]

const typeLabel: Record<string, string> = {
  ROUTINE: '常规',
  EMERGENCY: '应急',
  RECHECK: '复查',
}

const statusLabel: Record<string, string> = {
  PENDING: '待执行',
  IN_PROGRESS: '进行中',
  AWAITING_CONFIRMATION: '待确认',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await listTasks(filterStatus.value ? { status: filterStatus.value } : {})
    tasks.value = res.items
  } catch {
    error.value = '加载任务失败，请重试'
  } finally {
    loading.value = false
  }
}

function onFilter() {
  load()
}

onMounted(load)
</script>

<template>
  <div class="tasks">
    <header class="topbar">任务列表</header>
    <div class="filter-bar">
      <select v-model="filterStatus" @change="onFilter">
        <option v-for="o in statusOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
    </div>
    <div class="content">
      <p v-if="loading" class="hint">加载中…</p>
      <p v-else-if="error" class="hint error">{{ error }}</p>
      <p v-else-if="tasks.length === 0" class="hint">暂无任务</p>
      <div v-else class="list">
        <div v-for="t in tasks" :key="t.id" class="card item" @click="router.push(`/tasks/${t.id}`)">
          <div class="row">
            <span class="tag" :class="t.status.toLowerCase()">{{ statusLabel[t.status] }}</span>
            <span class="tag type">{{ typeLabel[t.serviceType] }}</span>
            <span v-if="t.isOverdue" class="tag overdue">逾期</span>
          </div>
          <div class="name">{{ t.storeName }}</div>
          <div class="meta">{{ t.plannedDate }}</div>
          <div v-if="t.instructions" class="instructions">{{ t.instructions }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.tasks {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  background: var(--c-primary);
  color: #fff;
  font-size: 17px;
}
.filter-bar {
  padding: 12px 16px;
  border-bottom: 1px solid var(--c-border);
}
.filter-bar select {
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  font-size: 14px;
  background: #fff;
}
.content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.item {
  cursor: pointer;
}
.item .row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #eaf2fa;
  color: #1b4f8a;
}
.tag.pending {
  background: #fff4e5;
  color: #b25e00;
}
.tag.in_progress {
  background: #e6f4ff;
  color: #0b6bcb;
}
.tag.awaiting_confirmation {
  background: #f3e8ff;
  color: #7a3fb3;
}
.tag.completed {
  background: #e6f7ee;
  color: #1a7a3c;
}
.tag.cancelled {
  background: #f0f0f0;
  color: #888;
}
.tag.type {
  background: #f0f0f0;
  color: #555;
}
.tag.overdue {
  background: #ffe5e5;
  color: #d33;
}
.item .name {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
}
.item .meta {
  margin-top: 4px;
  color: var(--c-text-weak);
  font-size: 13px;
}
.item .instructions {
  margin-top: 6px;
  color: var(--c-text-weak);
  font-size: 13px;
  line-height: 1.5;
}
.hint {
  color: var(--c-text-weak);
  font-size: 14px;
}
.hint.error {
  color: var(--c-danger);
}
</style>
