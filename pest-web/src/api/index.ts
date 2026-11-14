import http from './http'

/** 身份与门店相关 API（对应 identity / masterdata 模块，与后端 DTO 对齐） */

// —— 后端统一成功响应包装：{ data, requestId } ——
interface ApiEnvelope<T> {
  data: T
  requestId: string
}

export interface Store {
  id: string
  customerId: string | null
  brandId: string | null
  code: string
  name: string
  address: string | null
  timezone: string | null
  brandName: string | null
  regionId?: string | null
  location?: { latitude: number; longitude: number } | null
  revision?: number
  contactName?: string | null
  contactPhone?: string | null
  areas?: { id: string; name: string; floor: string | null }[]
}

export interface Facility {
  id: string
  storeId: string
  code: string
  typeId: string
  typeName: string | null
  areaId: string | null
  floor: string | null
  position: string
  status: 'ACTIVE' | 'INACTIVE' | 'REMOVED'
  revision: number
  updatedAt: string | null
}

export interface User {
  id: string
  companyId: string
  employeeCode: string
  name: string
  roles: string[]
  permissions: string[]
  teamName: string | null
  regionIds: string[]
  permissionVersion: number
}

export interface Session {
  accessToken: string
  refreshToken: string
  tokenType: string
  accessExpiresAt: string
  refreshExpiresAt: string
  sessionId: string
  user: User
}

/** 登录（POST /auth/sessions），companyCode 用于多租户定位主体 */
export async function login(
  companyCode: string,
  account: string,
  password: string,
): Promise<Session> {
  const deviceId = getDeviceId()
  const { data } = await http.post<ApiEnvelope<Session>>('/auth/sessions', {
    companyCode,
    account,
    password,
    deviceId,
  })
  return data.data
}

/** 授权门店列表（GET /stores），返回 items + 分页游标 */
export async function listStores(): Promise<{
  items: Store[]
  nextCursor: string | null
  hasMore: boolean
}> {
  const { data } = await http.get<ApiEnvelope<{ items: Store[]; nextCursor: string | null; hasMore: boolean }>>(
    '/stores',
  )
  return data.data
}

/** 门店详情（GET /stores/{storeId}） */
export async function getStore(storeId: string): Promise<Store> {
  const { data } = await http.get<ApiEnvelope<Store>>(`/stores/${storeId}`)
  return data.data
}

/** 设施台账（GET /stores/{storeId}/facilities） */
export async function listFacilities(
  storeId: string,
  params: { cursor?: string; limit?: number; typeId?: string; status?: string } = {},
): Promise<{
  items: Facility[]
  nextCursor: string | null
  hasMore: boolean
}> {
  const { data } = await http.get<
    ApiEnvelope<{ items: Facility[]; nextCursor: string | null; hasMore: boolean }>
  >(`/stores/${storeId}/facilities`, { params })
  return data.data
}

// —— 任务模块 ——

export interface Task {
  id: string
  storeId: string
  storeName: string
  serviceType: 'ROUTINE' | 'EMERGENCY' | 'RECHECK'
  status: 'PENDING' | 'IN_PROGRESS' | 'AWAITING_CONFIRMATION' | 'COMPLETED' | 'CANCELLED'
  plannedDate: string
  plannedStartAt: string | null
  plannedEndAt: string | null
  assigneeId: string
  assignmentVersion: number
  revision: number
  instructions: string
  source: string
  serviceId: string | null
  isOverdue: boolean
  latestReportId: string | null
  moduleStates: { module: string; state: string }[]
}

export interface TaskListParams {
  cursor?: string
  limit?: number
  startDate?: string
  endDate?: string
  status?: string
  keyword?: string
  storeId?: string
}

/** 当前员工与权限（GET /me） */
export async function getMe(): Promise<User> {
  const { data } = await http.get<ApiEnvelope<User>>('/me')
  return data.data
}

/** 任务详情（GET /tasks/{taskId}） */
export async function getTask(taskId: string): Promise<Task> {
  const { data } = await http.get<ApiEnvelope<Task>>(`/tasks/${taskId}`)
  return data.data
}

/** 任务列表（GET /tasks），按门店时区 plannedDate 排序 */
export async function listTasks(params: TaskListParams = {}): Promise<{
  items: Task[]
  nextCursor: string | null
  hasMore: boolean
}> {
  const { data } = await http.get<ApiEnvelope<{ items: Task[]; nextCursor: string | null; hasMore: boolean }>>(
    '/tasks',
    { params },
  )
  return data.data
}

// —— 离线同步模块 ——

export interface OfflineContext {
  id: string
  schemaVersion: string
  contextDigest: string
  contextBody: Record<string, unknown>
  assetKey: string
  permissionVersion: number
  issuedAt: string
  expiresAt: string
  downloadBytes: number
}

export interface SubmissionReceipt {
  id: string
  state: string
  serviceId: string | null
  draftRevision: number
  conflictDetail: unknown
  resolvedWith: unknown
  effects: unknown[]
  receivedAt: string
}

export interface SyncException {
  id: string
  submissionId: string
  type: string
  status: string
  message: string
  submittedServiceId: string | null
  createdAt: string
  resolution: unknown
  resolvedAt: string | null
  taskId: string | null
  currentTaskRevision: number
}

/** 下载任务离线上下文（GET /tasks/{taskId}/offline-context） */
export async function getTaskContext(
  taskId: string,
  opts: { contextId?: string; deviceId?: string } = {},
): Promise<OfflineContext> {
  const { data } = await http.get<ApiEnvelope<OfflineContext>>(
    `/tasks/${taskId}/offline-context`,
    { params: { contextId: opts.contextId, deviceId: opts.deviceId } },
  )
  return data.data
}

/** 下载门店应急上下文（GET /stores/{storeId}/offline-context） */
export async function getStoreContext(
  storeId: string,
  opts: { contextId?: string; deviceId?: string } = {},
): Promise<OfflineContext> {
  const { data } = await http.get<ApiEnvelope<OfflineContext>>(
    `/stores/${storeId}/offline-context`,
    { params: { contextId: opts.contextId, deviceId: opts.deviceId } },
  )
  return data.data
}

/** 确认离线资料完整下载（POST /offline-contexts/{contextId}/download-receipts） */
export async function confirmDownload(
  contextId: string,
  assetHashes: string[] = [],
  downloadedAt?: string,
): Promise<void> {
  await http.post(`/offline-contexts/${contextId}/download-receipts`, {
    contextId,
    assetHashes,
    downloadedAt: downloadedAt ?? new Date().toISOString(),
  })
}

/** 提交离线作业包（POST /sync/submissions），返回 202 */
export async function submitOfflineWork(input: {
  id: string
  deviceId: string
  contextId: string
  taskId?: string
  emergencyTask?: unknown
  serviceId: string
  assignmentVersion: number
  baseDraftRevision: number
  draft: { content: Record<string, unknown> }
  snapshotId?: string
  snapshotPayload?: unknown
  contentHash?: string
  confirmations?: unknown
  publicationRequest?: unknown
  submittedAt: string
}): Promise<SubmissionReceipt> {
  const { data } = await http.post<ApiEnvelope<SubmissionReceipt>>('/sync/submissions', input)
  return data.data
}

/** 查询离线提交状态（GET /sync/submissions/{submissionId}） */
export async function getSubmission(submissionId: string): Promise<SubmissionReceipt> {
  const { data } = await http.get<ApiEnvelope<SubmissionReceipt>>(
    `/sync/submissions/${submissionId}`,
  )
  return data.data
}

/** 同步异常列表（GET /sync/exceptions） */
export async function listSyncExceptions(status?: string): Promise<SyncException[]> {
  const { data } = await http.get<ApiEnvelope<SyncException[]>>('/sync/exceptions', {
    params: { status },
  })
  return data.data
}

/** 处理同步异常（POST /sync/exceptions/{exceptionId}/resolutions） */
export async function resolveSyncException(
  exceptionId: string,
  input: { decision: string; reason: string; expectedTaskRevision: number },
): Promise<SyncException> {
  const { data } = await http.post<ApiEnvelope<SyncException>>(
    `/sync/exceptions/${exceptionId}/resolutions`,
    input,
  )
  return data.data
}

/**
 * 生成并持久化设备会话标识（后端要求 deviceId 非空，绑定刷新令牌）。
 * 同一浏览器/账号复用同一 deviceId，切换账号不受影响。
 */
export function getDeviceId(): string {
  const KEY = 'device_id'
  let id = localStorage.getItem(KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(KEY, id)
  }
  return id
}
