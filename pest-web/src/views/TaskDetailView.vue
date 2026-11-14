<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getTask,
  getTaskContext,
  submitOfflineWork,
  getSubmission,
  getDeviceId,
  type Task,
  type OfflineContext,
  type SubmissionReceipt,
} from '@/api'
import {
  saveContext,
  getContext,
  enqueueSync,
  getSyncQueue,
  removeSyncItem,
  saveDraft as saveLocalDraft,
} from '@/services/offlineStore'
import OfflineForm from '@/components/OfflineForm.vue'
import type { ServiceContent, OfflineContextBody } from '@/api/serviceContent'

const route = useRoute()
const router = useRouter()
const taskId = route.params.taskId as string

// 账号隔离键：用 deviceId（同一浏览器同一账号稳定）
const accountId = computed(() => getDeviceId())

const task = ref<Task | null>(null)
const loading = ref(true)
const error = ref('')

// 当前员工 id（登录时缓存在 localStorage('user')，用于 NewRisk.ownerId / ChemicalUse.operatorId 默认值）
const employeeId = ref<string | undefined>(undefined)
try {
  const raw = localStorage.getItem('user')
  if (raw) employeeId.value = (JSON.parse(raw) as { id?: string }).id
} catch {
  /* 忽略解析失败 */
}

// 离线上下文
const context = ref<OfflineContext | null>(null)
const contextLoading = ref(false)

// 完整作业表单（draft.content 完整 ServiceContent 结构）
const content = ref<ServiceContent>({})
const contextBody = ref<OfflineContextBody | null>(null)
const submitting = ref(false)
const toast = ref('')

// 同步状态
const submission = ref<SubmissionReceipt | null>(null)
const online = ref(navigator.onLine)

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
    task.value = await getTask(taskId)
  } catch {
    error.value = '加载任务失败，请重试'
  } finally {
    loading.value = false
  }
}

/** 下载离线上下文并缓存到 IndexedDB */
async function downloadContext() {
  if (!task.value) return
  contextLoading.value = true
  toast.value = ''
  try {
    const ctx = await getTaskContext(taskId, { deviceId: accountId.value })
    context.value = ctx
    contextBody.value = (ctx.contextBody ?? {}) as OfflineContextBody
    await saveContext(accountId.value, ctx.id, ctx.contextBody)
    toast.value = '离线上下文已下载，可离线作业'
  } catch {
    toast.value = '下载上下文失败，请检查网络'
  } finally {
    contextLoading.value = false
  }
}

/** 从本地缓存恢复上下文（离线场景） */
async function restoreContext() {
  const cachedId = context.value?.id
  if (!cachedId) return false
  try {
    const body = await getContext<Record<string, unknown>>(accountId.value, cachedId)
    if (body) {
      contextBody.value = body as unknown as OfflineContextBody
      toast.value = '已从本地缓存恢复离线上下文'
      return true
    }
    return false
  } catch {
    return false
  }
}

/** 提交作业：有网直传，无网入队 */
async function submit() {
  if (!task.value) return
  if (!context.value) {
    toast.value = '请先下载离线上下文'
    return
  }

  submitting.value = true
  toast.value = ''

  const serviceId = task.value.serviceId ?? crypto.randomUUID()
  const submissionId = crypto.randomUUID()
  // draft.content 透传完整 ServiceContent（含签到/虫情/点检/用药/风险/总结等模块）
  const draftContent = content.value

  // 先落本地草稿（离线也可用）
  await saveLocalDraft(accountId.value, {
    id: `service:${serviceId}`,
    content: draftContent,
    updatedAt: Date.now(),
  })

  const payload: Record<string, unknown> = {
    id: submissionId,
    deviceId: accountId.value,
    contextId: context.value.id,
    taskId: taskId,
    serviceId,
    assignmentVersion: task.value.assignmentVersion,
    baseDraftRevision: 0,
    draft: { content: draftContent },
    submittedAt: new Date().toISOString(),
  }

  if (!navigator.onLine) {
    // 无网：入队待同步
    await enqueueSync(accountId.value, payload)
    toast.value = '已离线保存，联网后自动同步'
    submitting.value = false
    return
  }

  try {
    submission.value = await submitOfflineWork(payload as never)
    toast.value = '作业已提交，正在服务端处理'
    await pollSubmission(submissionId)
  } catch {
    // 提交失败（网络抖动）也入队兜底
    await enqueueSync(accountId.value, payload)
    toast.value = '提交失败，已转离线队列，联网后重试'
  } finally {
    submitting.value = false
  }
}

/** 轮询提交状态（worker 异步应用） */
async function pollSubmission(submissionId: string) {
  for (let i = 0; i < 5; i++) {
    await new Promise((r) => setTimeout(r, 1500))
    try {
      const s = await getSubmission(submissionId)
      submission.value = s
      if (s.state === 'APPLIED' || s.state === 'CONFLICT' || s.state === 'REJECTED') {
        toast.value =
          s.state === 'APPLIED' ? '作业已同步成功' : `作业同步异常：${s.state}`
        break
      }
    } catch {
      break
    }
  }
}

/** 联网后冲刷待同步队列 */
async function flushQueue() {
  const queue = await getSyncQueue(accountId.value)
  for (const item of queue) {
    try {
      const s = await submitOfflineWork(item.payload as never)
      submission.value = s
      await removeSyncItem(accountId.value, item.id)
    } catch {
      // 仍失败则保留队列
      break
    }
  }
}

// 监听网络恢复自动冲刷队列
window.addEventListener('online', () => {
  online.value = true
  flushQueue()
})
window.addEventListener('offline', () => {
  online.value = false
})

onMounted(async () => {
  await load()
  // 离线场景：任务加载失败时也尝试从本地恢复，保证断网可作业
  if (!task.value) {
    await restoreContext()
  }
})
</script>

<template>
  <div class="task-detail">
    <header class="topbar">
      <button class="back" @click="router.back()">‹</button>
      <span class="title">作业详情</span>
      <span class="net" :class="{ offline: !online }">{{ online ? '在线' : '离线' }}</span>
    </header>

    <div class="content">
      <p v-if="loading" class="hint">加载中…</p>
      <p v-else-if="error" class="hint error">{{ error }}</p>

      <template v-else-if="task">
        <div class="card">
          <div class="row">
            <span class="tag" :class="task.status.toLowerCase()">{{ statusLabel[task.status] }}</span>
            <span class="tag type">{{ task.serviceType }}</span>
            <span v-if="task.isOverdue" class="tag overdue">逾期</span>
          </div>
          <div class="name">{{ task.storeName }}</div>
          <div class="meta">计划日期 {{ task.plannedDate }}</div>
          <div v-if="task.instructions" class="instructions">{{ task.instructions }}</div>
        </div>

        <div class="card">
          <div class="section-title">离线上下文</div>
          <p v-if="context" class="hint success">
            已缓存（版本 {{ context.permissionVersion }}，{{ context.expiresAt.slice(0, 10) }} 到期）
          </p>
          <p v-else class="hint">下载后可离线查看门店信息、设施与风险台账</p>
          <button class="btn primary" :disabled="contextLoading" @click="downloadContext">
            {{ contextLoading ? '下载中…' : context ? '重新下载' : '下载离线上下文' }}
          </button>
        </div>

        <div class="card">
          <div class="section-title">现场作业</div>
          <OfflineForm
            v-model="content"
            :context-body="contextBody"
            :employee-id="employeeId"
          />
          <button
            class="btn primary"
            style="margin-top: 12px"
            :disabled="submitting"
            @click="submit"
          >
            {{ submitting ? '提交中…' : '提交作业' }}
          </button>
        </div>

        <div v-if="submission" class="card">
          <div class="section-title">同步状态</div>
          <div class="meta">
            提交号 {{ submission.id.slice(0, 8) }} · 状态
            <span class="state" :class="submission.state.toLowerCase()">{{ submission.state }}</span>
          </div>
        </div>

        <div v-if="toast" class="toast">{{ toast }}</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.task-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.topbar {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--c-primary);
  color: #fff;
}
.back {
  background: none;
  border: none;
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}
.title {
  font-size: 17px;
  flex: 1;
}
.net {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.2);
}
.net.offline {
  background: #ffd6d6;
  color: #d33;
}
.content {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.card {
  background: #fff;
  border-radius: var(--radius-card);
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.row {
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
.name {
  margin-top: 8px;
  font-size: 17px;
  font-weight: 600;
}
.meta {
  margin-top: 4px;
  color: var(--c-text-weak);
  font-size: 13px;
}
.instructions {
  margin-top: 8px;
  color: var(--c-text-weak);
  font-size: 13px;
  line-height: 1.5;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
}
.field {
  display: block;
  margin-bottom: 12px;
}
.field span {
  display: block;
  font-size: 13px;
  color: var(--c-text-weak);
  margin-bottom: 6px;
}
.field input,
.field textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  font-size: 14px;
  font-family: inherit;
}
.btn {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: var(--radius-card);
  font-size: 15px;
  cursor: pointer;
}
.btn.primary {
  background: var(--c-primary);
  color: #fff;
}
.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.state {
  font-weight: 600;
}
.state.applied {
  color: #1a7a3c;
}
.state.conflict,
.state.rejected {
  color: #d33;
}
.state.received,
.state.processing {
  color: #b25e00;
}
.hint {
  color: var(--c-text-weak);
  font-size: 13px;
  margin-bottom: 10px;
}
.hint.error {
  color: var(--c-danger);
}
.hint.success {
  color: #1a7a3c;
}
.toast {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
  padding: 10px 18px;
  border-radius: 20px;
  font-size: 13px;
  max-width: 80%;
}
</style>
