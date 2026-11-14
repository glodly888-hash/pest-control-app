import http from './http'

/** 现场作业模块 API（对应 workorder 模块，与后端 DTO 对齐） */

interface ApiEnvelope<T> {
  data: T
  requestId: string
}

export interface ServiceDraft {
  serviceId: string
  taskId: string
  contextId: string
  draftRevision: number
  fieldCommitRevision: number
  phase: 'EDITABLE' | 'FROZEN' | 'AWAITING_CONFIRMATION' | 'PUBLISHING' | 'PUBLISHED'
  contentJson: string
  updatedAt: string
}

/** 创建服务草稿（POST /services/{taskId}），幂等 */
export async function createService(
  taskId: string,
  input: { id?: string; contextId?: string; assignmentVersion?: number } = {},
): Promise<ServiceDraft> {
  const { data } = await http.post<ApiEnvelope<ServiceDraft>>(`/services/${taskId}`, input)
  return data.data
}

/** 恢复草稿（GET /services/{serviceId}/draft） */
export async function getDraft(serviceId: string): Promise<ServiceDraft> {
  const { data } = await http.get<ApiEnvelope<ServiceDraft>>(`/services/${serviceId}/draft`)
  return data.data
}

/**
 * 保存草稿（PUT /services/{serviceId}/draft）。
 * 后端用 If-Match 做乐观锁（ETag 形如 "draft-{revision}"）。
 */
export async function saveDraft(
  serviceId: string,
  input: { contextId?: string; content: Record<string, unknown>; savedAt?: string },
  draftRevision: number,
): Promise<ServiceDraft> {
  const { data } = await http.put<ApiEnvelope<ServiceDraft>>(
    `/services/${serviceId}/draft`,
    { ...input, savedAt: input.savedAt ?? new Date().toISOString() },
    { headers: { 'If-Match': `"draft-${draftRevision}"` } },
  )
  return data.data
}
