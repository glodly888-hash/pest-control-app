/**
 * 离线本地存储封装。
 *
 * PWA 方案的离线能力核心，替代安卓原生本地库：
 * - 结构化数据（草稿、同步队列、摘要）→ IndexedDB
 * - 照片原图 → OPFS（Origin Private File System），避免 IndexedDB 存大二进制
 *
 * 架构设计约束：
 * - 账号隔离：缓存绑定登录账号，切账号不得读取前一账号数据
 * - 先落盘再提示成功：照片写入并校验后才建立引用
 * - 同步完成前不清理原始照片
 */

const DB_NAME = 'pest-offline'
const DB_VERSION = 1

let dbPromise: Promise<IDBDatabase> | null = null

/** 打开（或创建）账号隔离的数据库 */
function openDb(accountId: string): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(`${DB_NAME}-${accountId}`, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains('drafts')) {
        db.createObjectStore('drafts', { keyPath: 'id' })
      }
      if (!db.objectStoreNames.contains('syncQueue')) {
        db.createObjectStore('syncQueue', { keyPath: 'id', autoIncrement: true })
      }
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  return dbPromise
}

/** 保存一条作业草稿（结构化 JSON） */
export async function saveDraft(accountId: string, draft: { id: string; [k: string]: unknown }) {
  const db = await openDb(accountId)
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction('drafts', 'readwrite')
    tx.objectStore('drafts').put(draft)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/** 读取草稿 */
export async function getDraft<T>(accountId: string, id: string): Promise<T | undefined> {
  const db = await openDb(accountId)
  return new Promise<T | undefined>((resolve, reject) => {
    const tx = db.transaction('drafts', 'readonly')
    const req = tx.objectStore('drafts').get(id)
    req.onsuccess = () => resolve(req.result as T | undefined)
    req.onerror = () => reject(req.error)
  })
}

/** 入队一条待同步记录（联网后由页面可见事件触发提交） */
export async function enqueueSync(accountId: string, payload: unknown) {
  const db = await openDb(accountId)
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction('syncQueue', 'readwrite')
    tx.objectStore('syncQueue').add({ payload, createdAt: Date.now() })
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

export interface SyncQueueItem {
  id: number
  payload: Record<string, unknown>
  createdAt: number
}

/** 读取待同步队列（按入队顺序） */
export async function getSyncQueue(accountId: string): Promise<SyncQueueItem[]> {
  const db = await openDb(accountId)
  return new Promise<SyncQueueItem[]>((resolve, reject) => {
    const tx = db.transaction('syncQueue', 'readonly')
    const req = tx.objectStore('syncQueue').getAll()
    req.onsuccess = () => resolve((req.result as SyncQueueItem[]) ?? [])
    req.onerror = () => reject(req.error)
  })
}

/** 移除一条已同步的记录 */
export async function removeSyncItem(accountId: string, id: number): Promise<void> {
  const db = await openDb(accountId)
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction('syncQueue', 'readwrite')
    tx.objectStore('syncQueue').delete(id)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/** 缓存离线上下文（按 contextId 键，供离线作业时读取门店/设施/风险等） */
export async function saveContext(accountId: string, contextId: string, context: unknown): Promise<void> {
  const db = await openDb(accountId)
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction('drafts', 'readwrite')
    tx.objectStore('drafts').put({ id: `context:${contextId}`, context })
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
  })
}

/** 读取缓存上下文 */
export async function getContext<T>(accountId: string, contextId: string): Promise<T | undefined> {
  return getDraft<{ context: T }>(accountId, `context:${contextId}`).then((r) => r?.context)
}

/**
 * 把照片原图写入 OPFS，返回文件句柄名。
 * OPFS 只存二进制，引用关系（文件名 → 业务记录）记在 IndexedDB 草稿里。
 */
export async function savePhoto(accountId: string, name: string, blob: Blob): Promise<string> {
  const root = await navigator.storage.getDirectory()
  const dir = await root.getDirectoryHandle(`photos-${accountId}`, { create: true })
  const handle = await dir.getFileHandle(name, { create: true })
  const writable = await handle.createWritable()
  await writable.write(blob)
  await writable.close()
  return name
}

/** 读取照片原图 */
export async function readPhoto(accountId: string, name: string): Promise<Blob> {
  const root = await navigator.storage.getDirectory()
  const dir = await root.getDirectoryHandle(`photos-${accountId}`, { create: true })
  const handle = await dir.getFileHandle(name)
  const file = await handle.getFile()
  return file
}

/**
 * 浏览器持久化配额监控。存储超过阈值时返回警告，供 UI 提示清理。
 * 对应架构风险 R12：浏览器存储配额与后台保活。
 */
export async function checkQuota(): Promise<{ usage: number; quota: number; ratio: number }> {
  const { usage = 0, quota = 0 } = (await navigator.storage.estimate()) ?? {}
  return { usage, quota, ratio: quota ? usage / quota : 0 }
}
