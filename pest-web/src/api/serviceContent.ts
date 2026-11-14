/**
 * ServiceContent 完整字段模型。
 *
 * 对齐后端字段字典《虫害治理App接口文档_V1.0/完整字段字典.md》，
 * draft.content 由后端透传 JSON 存储，前端按此结构组包即可，无需后端改动。
 *
 * 草稿允许模块缺省；冻结前由服务端按 V01–V16 做完整性校验。
 * 本文件是离线表单的单一事实来源，字段名/枚举值必须与字段字典一致。
 */

// —— 基础结构 ——

/** 数量：EXACT 时 value 必填（可为 0），UNKNOWN 时不传 value */
export interface Quantity {
  kind: 'EXACT' | 'UNKNOWN'
  value?: number
}

/** 附件引用：attachmentId + 原始字节 sha256（小写十六进制） */
export interface AttachmentRef {
  attachmentId: string
  sha256: string
}

/** 定位信息 */
export interface Location {
  latitude: number
  longitude: number
  accuracyMeters: number
  provider: 'GPS' | 'NETWORK' | 'MANUAL'
  capturedAt: string
}

// —— 签到 ——

export interface Checkin {
  arrivedAt: string
  location?: Location
  locationStatus: 'NORMAL' | 'OUT_OF_RANGE' | 'UNAVAILABLE' | 'LOW_ACCURACY'
  exceptionReason?: string
  storefrontPhotos: AttachmentRef[]
}

// —— 服务须知确认 ——

export interface NoticeConfirmation {
  noticeId: string
  version: string
  isAcknowledged: boolean
  confirmedAt: string
}

// —— 观察项（客户反馈 / 现场发现共用） ——

export interface Finding {
  id: string
  source: 'CUSTOMER' | 'ON_SITE'
  eventGroupId?: string
  pestTypeId: string
  areaId: string
  position: string
  observedAt: string
  quantity: Quantity
  description: string
  photos: AttachmentRef[]
  treatmentIds: string[]
  noTreatmentReason?: string
}

export interface ObservationSection {
  state: 'UNFILLED' | 'NONE_FOUND' | 'FOUND'
  findings: Finding[]
  confirmedAt?: string
}

// —— 环境评估 ——

export interface StaticAssessment {
  isConfirmed: boolean
  confirmedAt?: string
  sourceRevision: number
  hasChanged: boolean
  storeType: 'OPEN' | 'ENCLOSED' | 'UNKNOWN'
  shutterDoor: 'PRESENT' | 'ABSENT' | 'UNKNOWN'
  foldingDoor: 'PRESENT' | 'ABSENT' | 'UNKNOWN'
  airCurtain: 'ON' | 'OFF' | 'ABSENT' | 'UNKNOWN'
  airAngle: 'PASS' | 'FAIL' | 'NOT_APPLICABLE' | 'UNKNOWN'
  openCeiling: 'YES' | 'NO' | 'UNKNOWN'
  sewerWellCount: Quantity
  greeneryDescription: string
  nearbyRenovation: 'YES' | 'NO' | 'UNKNOWN'
  nearbyVacancy: 'YES' | 'NO' | 'UNKNOWN'
  remarks: string
  photos: AttachmentRef[]
}

// —— 设施点检 ——

export interface Capture {
  pestTypeId: string
  quantity: Quantity
  treatmentIds: string[]
  noTreatmentReason?: string
}

export interface Inspection {
  id: string
  facilityId: string
  facilityRevision: number
  checkResult: 'CHECKED' | 'NOT_CHECKED' | 'UNCONFIRMABLE'
  notCheckedReason?: string
  facilityCondition?: 'NORMAL' | 'DAMAGED' | 'MISSING' | 'UNCONFIRMABLE'
  consumableAction?: 'REPLACED' | 'NOT_REPLACED' | 'NOT_APPLICABLE'
  captures: Capture[]
  photos: AttachmentRef[]
  remarks: string
  inspectedAt?: string
  noTreatmentReason?: string
  treatmentIds: string[]
}

// —— 设施变更 ——

export interface FacilityChange {
  id: string
  facilityId: string
  action: 'ADD' | 'MOVE' | 'DEACTIVATE' | 'REMOVE'
  expectedRevision: number
  code?: string
  typeId?: string
  areaId?: string
  floor?: string
  position?: string
  reason: string
  occurredAt: string
  supersedesEventId?: string
}

// —— 风险 ——

export interface NewRisk {
  id: string
  typeId: string
  pestTypeIds: string[]
  areaId: string
  floor?: string
  position: string
  discoveredAt: string
  description: string
  emergencyAction?: string
  suggestion: string
  responsibleParty: string
  contactName?: string
  ownerId: string
  dueDate: string
  photos: AttachmentRef[]
}

export interface RiskFollowup {
  id: string
  riskId: string
  expectedRiskRevision: number
  status: 'RESOLVED' | 'UNRESOLVED' | 'NOT_VERIFIED' | 'NOT_CHECKED'
  notCheckedReason?: string
  result: string
  photos: AttachmentRef[]
  occurredAt: string
}

// —— 处置 / 用药 ——

export interface TargetLink {
  type: 'FINDING' | 'INSPECTION' | 'RISK'
  id: string
}

export interface ChemicalUse {
  id: string
  chemicalId: string
  chemicalRevision: number
  targetPestIds: string[]
  formulation: string
  method: string
  concentrationText: string
  amount: string
  unitCode: string
  amountBasis: 'CONCENTRATE' | 'PREPARED_LIQUID' | 'PER_AREA'
  position: string
  operatorId: string
}

export interface Treatment {
  id: string
  type: 'CLEANING' | 'SEALING' | 'FACILITY_ADJUSTMENT' | 'PHYSICAL' | 'CHEMICAL' | 'OBSERVATION'
  targets: TargetLink[]
  preventivePurpose?: string
  position: string
  description: string
  photos: AttachmentRef[]
  chemicalUses: ChemicalUse[]
  occurredAt: string
  nextFocus?: string
}

// —— 总结 ——

export interface PestRating {
  pestTypeId: string
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'UNASSESSED'
  basis: string
}

export interface Summary {
  mainFindings: string
  measuresTaken: string
  unresolvedIssues: string
  customerActions: string
  nextFocus: string
  weather: 'SUNNY' | 'CLOUDY' | 'RAINY' | 'OTHER' | 'UNRECORDED'
  shortServiceReason?: string
  ratings: PestRating[]
}

// —— 草稿正文（draft.content） ——

export interface ServiceContent {
  checkin?: Checkin
  noticeConfirmations?: NoticeConfirmation[]
  customerFeedback?: ObservationSection
  onSiteFindings?: ObservationSection
  assessment?: StaticAssessment
  inspections?: Inspection[]
  facilityChanges?: FacilityChange[]
  newRisks?: NewRisk[]
  riskFollowups?: RiskFollowup[]
  treatments?: Treatment[]
  summary?: Summary
  serviceAttachments?: AttachmentRef[]
}

// —— 离线上下文里的字典（供下拉选项） ——

export interface DictPestType {
  id: string
  name: string
}

export interface DictChemical {
  id: string
  name: string
  revision: number
}

export interface DictFacilityType {
  id: string
  name: string
}

/** 上下文里的门店设施快照（供点检勾选） */
export interface ContextFacility {
  id: string
  code: string
  name?: string
  typeId: string
  typeName?: string
  areaId?: string
  floor?: string
  position: string
  revision: number
}

/** 上下文里的既有未关闭风险（供跟进勾选） */
export interface ContextRisk {
  id: string
  position: string
  description: string
  revision: number
}

/** 上下文里的区域 */
export interface ContextArea {
  id: string
  name: string
  floor?: string
}

/** 离线上下文 contextBody 的实际结构（dictionaries 用于渲染下拉） */
export interface OfflineContextBody {
  schemaVersion?: string
  store?: { id: string; name: string; code?: string }
  facilities?: ContextFacility[]
  openRisks?: ContextRisk[]
  areas?: ContextArea[]
  dictionaries?: {
    pestTypes?: DictPestType[]
    chemicals?: DictChemical[]
    facilityTypes?: DictFacilityType[]
  }
  [k: string]: unknown
}
