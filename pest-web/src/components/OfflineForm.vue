<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type {
  ServiceContent,
  Checkin,
  ObservationSection,
  StaticAssessment,
  Inspection,
  NewRisk,
  RiskFollowup,
  Treatment,
  Summary,
  Quantity,
  Finding,
  Capture,
  ChemicalUse,
  OfflineContextBody,
  ContextFacility,
  ContextRisk,
  DictPestType,
  DictChemical,
} from '@/api/serviceContent'

const props = defineProps<{
  /** 当前草稿正文，双向绑定 */
  modelValue: ServiceContent
  /** 离线上下文 contextBody，用于渲染字典/设施/风险下拉 */
  contextBody: OfflineContextBody | null
  /** 员工本人 id（NewRisk.ownerId / ChemicalUse.operatorId 默认值） */
  employeeId?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: ServiceContent): void
}>()

// —— 从上下文取字典 / 设施 / 风险 ——
const pestTypes = computed<DictPestType[]>(() => props.contextBody?.dictionaries?.pestTypes ?? [])
const chemicals = computed<DictChemical[]>(() => props.contextBody?.dictionaries?.chemicals ?? [])
const facilities = computed<ContextFacility[]>(() => props.contextBody?.facilities ?? [])
const openRisks = computed<ContextRisk[]>(() => props.contextBody?.openRisks ?? [])
const areas = computed(() => props.contextBody?.areas ?? [])

// —— 折叠面板状态 ——
const panels = ref<Record<string, boolean>>({
  checkin: true,
  feedback: false,
  findings: false,
  assessment: false,
  inspection: false,
  risks: false,
  treatment: false,
  summary: false,
})
function toggle(name: string) {
  panels.value[name] = !panels.value[name]
}

function nowIso(): string {
  return new Date().toISOString()
}
function newId(): string {
  return crypto.randomUUID()
}

// —— 工具：更新 content 并回传 ——
function patch(partial: Partial<ServiceContent>) {
  emit('update:modelValue', { ...props.modelValue, ...partial })
}

// —— 通用辅助 ——
function emptyQuantity(): Quantity {
  return { kind: 'UNKNOWN' }
}

// ============ 1. 签到 ============
const checkin = computed<Checkin>(() => {
  const c = props.modelValue.checkin ?? {
    arrivedAt: '',
    locationStatus: 'NORMAL',
    storefrontPhotos: [],
  }
  return c
})
function updateCheckin(patch: Partial<Checkin>) {
  const next: Checkin = { ...checkin.value, ...patch }
  emit('update:modelValue', { ...props.modelValue, checkin: next })
}
const locationStatusOptions = ['NORMAL', 'OUT_OF_RANGE', 'UNAVAILABLE', 'LOW_ACCURACY'] as const
const locationStatusLabel: Record<string, string> = {
  NORMAL: '正常',
  OUT_OF_RANGE: '超出范围',
  UNAVAILABLE: '定位不可用',
  LOW_ACCURACY: '精度不足',
}

// ============ 2. 客户反馈 / 现场发现 ============
function observation(section: 'customerFeedback' | 'onSiteFindings'): ObservationSection {
  return (
    props.modelValue[section] ?? {
      state: 'UNFILLED',
      findings: [],
    }
  )
}
function setObservationState(section: 'customerFeedback' | 'onSiteFindings', state: ObservationSection['state']) {
  const cur = observation(section)
  const next: ObservationSection = { ...cur, state, confirmedAt: state === 'UNFILLED' ? undefined : nowIso() }
  patch({ [section]: next })
}
function addFinding(section: 'customerFeedback' | 'onSiteFindings') {
  const cur = observation(section)
  const source = section === 'customerFeedback' ? 'CUSTOMER' : 'ON_SITE'
  const f: Finding = {
    id: newId(),
    source,
    pestTypeId: pestTypes.value[0]?.id ?? '',
    areaId: areas.value[0]?.id ?? '',
    position: '',
    observedAt: nowIso(),
    quantity: emptyQuantity(),
    description: '',
    photos: [],
    treatmentIds: [],
  }
  patch({ [section]: { ...cur, state: 'FOUND', findings: [...cur.findings, f] } })
}
function removeFinding(section: 'customerFeedback' | 'onSiteFindings', id: string) {
  const cur = observation(section)
  patch({ [section]: { ...cur, findings: cur.findings.filter((f) => f.id !== id) } })
}
function updateFinding(section: 'customerFeedback' | 'onSiteFindings', id: string, p: Partial<Finding>) {
  const cur = observation(section)
  patch({
    [section]: {
      ...cur,
      findings: cur.findings.map((f) => (f.id === id ? { ...f, ...p } : f)),
    },
  })
}

// ============ 3. 环境评估 ============
const assessment = computed<StaticAssessment>(() => {
  return (
    props.modelValue.assessment ?? {
      isConfirmed: false,
      sourceRevision: 0,
      hasChanged: false,
      storeType: 'UNKNOWN',
      shutterDoor: 'UNKNOWN',
      foldingDoor: 'UNKNOWN',
      airCurtain: 'UNKNOWN',
      airAngle: 'UNKNOWN',
      openCeiling: 'UNKNOWN',
      sewerWellCount: emptyQuantity(),
      greeneryDescription: '',
      nearbyRenovation: 'UNKNOWN',
      nearbyVacancy: 'UNKNOWN',
      remarks: '',
      photos: [],
    }
  )
})
function updateAssessment(p: Partial<StaticAssessment>) {
  patch({ assessment: { ...assessment.value, ...p } })
}
const ynOptions = ['YES', 'NO', 'UNKNOWN'] as const
const ynLabel: Record<string, string> = { YES: '是', NO: '否', UNKNOWN: '未知' }
const presenceOptions = ['PRESENT', 'ABSENT', 'UNKNOWN'] as const
const presenceLabel: Record<string, string> = { PRESENT: '有', ABSENT: '无', UNKNOWN: '未知' }

// ============ 4. 设施点检 ============
const inspections = computed<Inspection[]>(() => props.modelValue.inspections ?? [])
function ensureInspection(facility: ContextFacility) {
  const existing = inspections.value.find((i) => i.facilityId === facility.id)
  if (existing) return existing
  const item: Inspection = {
    id: newId(),
    facilityId: facility.id,
    facilityRevision: facility.revision ?? 0,
    checkResult: 'CHECKED',
    captures: [],
    photos: [],
    remarks: '',
    inspectedAt: nowIso(),
    treatmentIds: [],
  }
  patch({ inspections: [...inspections.value, item] })
  return item
}
function updateInspection(id: string, p: Partial<Inspection>) {
  patch({
    inspections: inspections.value.map((i) => (i.id === id ? { ...i, ...p } : i)),
  })
}
function addCapture(inspectionId: string) {
  const cap: Capture = {
    pestTypeId: pestTypes.value[0]?.id ?? '',
    quantity: emptyQuantity(),
    treatmentIds: [],
  }
  updateInspection(inspectionId, {
    captures: [...(inspections.value.find((i) => i.id === inspectionId)?.captures ?? []), cap],
  })
}
function updateCapture(inspectionId: string, idx: number, p: Partial<Capture>) {
  const insp = inspections.value.find((i) => i.id === inspectionId)
  if (!insp) return
  const captures = insp.captures.map((c, i) => (i === idx ? { ...c, ...p } : c))
  updateInspection(inspectionId, { captures })
}
function removeCapture(inspectionId: string, idx: number) {
  const insp = inspections.value.find((i) => i.id === inspectionId)
  if (!insp) return
  updateInspection(inspectionId, { captures: insp.captures.filter((_, i) => i !== idx) })
}
const checkResultOptions = ['CHECKED', 'NOT_CHECKED', 'UNCONFIRMABLE'] as const
const checkResultLabel: Record<string, string> = { CHECKED: '已检查', NOT_CHECKED: '未检查', UNCONFIRMABLE: '无法确认' }
const facilityConditionOptions = ['NORMAL', 'DAMAGED', 'MISSING', 'UNCONFIRMABLE'] as const
const facilityConditionLabel: Record<string, string> = {
  NORMAL: '正常',
  DAMAGED: '损坏',
  MISSING: '缺失',
  UNCONFIRMABLE: '无法确认',
}
const consumableActionOptions = ['REPLACED', 'NOT_REPLACED', 'NOT_APPLICABLE'] as const
const consumableActionLabel: Record<string, string> = {
  REPLACED: '已更换',
  NOT_REPLACED: '未更换',
  NOT_APPLICABLE: '不适用',
}

// ============ 5. 新风险 ============
const newRisks = computed<NewRisk[]>(() => props.modelValue.newRisks ?? [])
function addNewRisk() {
  const r: NewRisk = {
    id: newId(),
    typeId: '',
    pestTypeIds: [],
    areaId: areas.value[0]?.id ?? '',
    position: '',
    discoveredAt: nowIso(),
    description: '',
    suggestion: '',
    responsibleParty: '',
    ownerId: props.employeeId ?? '',
    dueDate: '',
    photos: [],
  }
  patch({ newRisks: [...newRisks.value, r] })
}
function updateNewRisk(id: string, p: Partial<NewRisk>) {
  patch({ newRisks: newRisks.value.map((r) => (r.id === id ? { ...r, ...p } : r)) })
}
function removeNewRisk(id: string) {
  patch({ newRisks: newRisks.value.filter((r) => r.id !== id) })
}
function togglePestType(list: string[], id: string): string[] {
  return list.includes(id) ? list.filter((x) => x !== id) : [...list, id]
}

// ============ 6. 既有风险跟进 ============
const riskFollowups = computed<RiskFollowup[]>(() => props.modelValue.riskFollowups ?? [])
function ensureFollowup(risk: ContextRisk) {
  const existing = riskFollowups.value.find((f) => f.riskId === risk.id)
  if (existing) return
  const f: RiskFollowup = {
    id: newId(),
    riskId: risk.id,
    expectedRiskRevision: risk.revision ?? 0,
    status: 'UNRESOLVED',
    result: '',
    photos: [],
    occurredAt: nowIso(),
  }
  patch({ riskFollowups: [...riskFollowups.value, f] })
}
function updateFollowup(id: string, p: Partial<RiskFollowup>) {
  patch({ riskFollowups: riskFollowups.value.map((f) => (f.id === id ? { ...f, ...p } : f)) })
}
const followupStatusOptions = ['RESOLVED', 'UNRESOLVED', 'NOT_VERIFIED', 'NOT_CHECKED'] as const
const followupStatusLabel: Record<string, string> = {
  RESOLVED: '已解决',
  UNRESOLVED: '未解决',
  NOT_VERIFIED: '未核实',
  NOT_CHECKED: '未核查',
}

// ============ 7. 处置 / 用药 ============
const treatments = computed<Treatment[]>(() => props.modelValue.treatments ?? [])
function addTreatment() {
  const t: Treatment = {
    id: newId(),
    type: 'CHEMICAL',
    targets: [],
    position: '',
    description: '',
    photos: [],
    chemicalUses: [],
    occurredAt: nowIso(),
  }
  patch({ treatments: [...treatments.value, t] })
}
function updateTreatment(id: string, p: Partial<Treatment>) {
  patch({ treatments: treatments.value.map((t) => (t.id === id ? { ...t, ...p } : t)) })
}
function removeTreatment(id: string) {
  patch({ treatments: treatments.value.filter((t) => t.id !== id) })
}
function addChemicalUse(treatmentId: string) {
  const t = treatments.value.find((x) => x.id === treatmentId)
  if (!t) return
  const cu: ChemicalUse = {
    id: newId(),
    chemicalId: chemicals.value[0]?.id ?? '',
    chemicalRevision: chemicals.value[0]?.revision ?? 0,
    targetPestIds: [],
    formulation: '',
    method: '',
    concentrationText: '',
    amount: '',
    unitCode: '',
    amountBasis: 'CONCENTRATE',
    position: '',
    operatorId: props.employeeId ?? '',
  }
  updateTreatment(treatmentId, { chemicalUses: [...t.chemicalUses, cu] })
}
function updateChemicalUse(treatmentId: string, idx: number, p: Partial<ChemicalUse>) {
  const t = treatments.value.find((x) => x.id === treatmentId)
  if (!t) return
  updateTreatment(treatmentId, {
    chemicalUses: t.chemicalUses.map((c, i) => (i === idx ? { ...c, ...p } : c)),
  })
}
function removeChemicalUse(treatmentId: string, idx: number) {
  const t = treatments.value.find((x) => x.id === treatmentId)
  if (!t) return
  updateTreatment(treatmentId, { chemicalUses: t.chemicalUses.filter((_, i) => i !== idx) })
}
const treatmentTypeOptions = ['CLEANING', 'SEALING', 'FACILITY_ADJUSTMENT', 'PHYSICAL', 'CHEMICAL', 'OBSERVATION'] as const
const treatmentTypeLabel: Record<string, string> = {
  CLEANING: '清洁',
  SEALING: '封堵',
  FACILITY_ADJUSTMENT: '设施调整',
  PHYSICAL: '物理捕杀',
  CHEMICAL: '药剂处理',
  OBSERVATION: '观察复查',
}
const amountBasisOptions = ['CONCENTRATE', 'PREPARED_LIQUID', 'PER_AREA'] as const
const amountBasisLabel: Record<string, string> = {
  CONCENTRATE: '原药',
  PREPARED_LIQUID: '配制液',
  PER_AREA: '单位面积',
}

// ============ 8. 总结 ============
const summary = computed<Summary>(() => {
  return (
    props.modelValue.summary ?? {
      mainFindings: '',
      measuresTaken: '',
      unresolvedIssues: '',
      customerActions: '',
      nextFocus: '',
      weather: 'UNRECORDED',
      ratings: [],
    }
  )
})
function updateSummary(p: Partial<Summary>) {
  patch({ summary: { ...summary.value, ...p } })
}
const weatherOptions = ['SUNNY', 'CLOUDY', 'RAINY', 'OTHER', 'UNRECORDED'] as const
const weatherLabel: Record<string, string> = {
  SUNNY: '晴',
  CLOUDY: '阴',
  RAINY: '雨',
  OTHER: '其他',
  UNRECORDED: '未记录',
}

// —— 完成度提示 ——
const filledModules = computed(() => {
  const c = props.modelValue
  const m = [
    !!c.checkin?.arrivedAt,
    !!c.customerFeedback && c.customerFeedback.state !== 'UNFILLED',
    !!c.onSiteFindings && c.onSiteFindings.state !== 'UNFILLED',
    !!c.assessment?.isConfirmed,
    (c.inspections?.length ?? 0) > 0,
    (c.riskFollowups?.length ?? 0) > 0,
    (c.treatments?.length ?? 0) > 0,
    !!c.summary?.mainFindings,
  ]
  return m.filter(Boolean).length
})

// 供父组件读取当前 content（用于提交）
watch(
  () => props.modelValue,
  () => {},
  { deep: true },
)

defineExpose({ filledModules })
</script>

<template>
  <div class="offline-form">
    <!-- 模块完成度 -->
    <div class="progress">已填模块 {{ filledModules }}/8</div>

    <!-- ============ 1. 签到 ============ -->
    <section class="module">
      <button class="module-head" @click="toggle('checkin')">
        <span>1. 到店签到</span>
        <span class="arrow" :class="{ open: panels.checkin }">▾</span>
      </button>
      <div v-if="panels.checkin" class="module-body">
        <label class="field">
          <span>到店时间</span>
          <input v-model="checkin.arrivedAt" type="datetime-local" @input="updateCheckin({ arrivedAt: checkin.arrivedAt })" />
        </label>
        <label class="field">
          <span>定位状态</span>
          <select v-model="checkin.locationStatus" @change="updateCheckin({ locationStatus: checkin.locationStatus })">
            <option v-for="o in locationStatusOptions" :key="o" :value="o">{{ locationStatusLabel[o] }}</option>
          </select>
        </label>
        <label v-if="checkin.locationStatus !== 'NORMAL'" class="field">
          <span>异常原因</span>
          <input v-model="checkin.exceptionReason" placeholder="说明定位异常原因" @input="updateCheckin({ exceptionReason: checkin.exceptionReason })" />
        </label>
      </div>
    </section>

    <!-- ============ 2. 客户反馈 ============ -->
    <section class="module">
      <button class="module-head" @click="toggle('feedback')">
        <span>2. 客户反馈</span>
        <span class="arrow" :class="{ open: panels.feedback }">▾</span>
      </button>
      <div v-if="panels.feedback" class="module-body">
        <div class="seg">
          <button v-for="s in ['UNFILLED', 'NONE_FOUND', 'FOUND']" :key="s" class="seg-btn"
            :class="{ on: observation('customerFeedback').state === s }"
            @click="setObservationState('customerFeedback', s as any)">
            {{ { UNFILLED: '未填写', NONE_FOUND: '无发现', FOUND: '有发现' }[s] }}
          </button>
        </div>
        <template v-if="observation('customerFeedback').state === 'FOUND'">
          <button class="add" @click="addFinding('customerFeedback')">+ 添加客户反馈明细</button>
          <div v-for="f in observation('customerFeedback').findings" :key="f.id" class="sub-card">
            <div class="sub-head">
              <span>反馈明细</span>
              <button class="del" @click="removeFinding('customerFeedback', f.id)">删除</button>
            </div>
            <label class="field"><span>虫种</span>
              <select v-model="f.pestTypeId" @change="updateFinding('customerFeedback', f.id, { pestTypeId: f.pestTypeId })">
                <option v-for="p in pestTypes" :key="p.id" :value="p.id">{{ p.name }}</option>
              </select>
            </label>
            <label class="field"><span>区域</span>
              <select v-model="f.areaId" @change="updateFinding('customerFeedback', f.id, { areaId: f.areaId })">
                <option v-for="a in areas" :key="a.id" :value="a.id">{{ a.name }}</option>
              </select>
            </label>
            <label class="field"><span>位置</span>
              <input v-model="f.position" placeholder="具体位置" @input="updateFinding('customerFeedback', f.id, { position: f.position })" />
            </label>
            <label class="field"><span>数量</span>
              <select v-model="f.quantity.kind" @change="f.quantity = f.quantity.kind === 'EXACT' ? { kind: 'EXACT', value: 0 } : { kind: 'UNKNOWN' }">
                <option value="EXACT">明确数量</option>
                <option value="UNKNOWN">数量不明</option>
              </select>
            </label>
            <label v-if="f.quantity.kind === 'EXACT'" class="field"><span>具体数量</span>
              <input v-model.number="f.quantity.value" type="number" min="0" />
            </label>
            <label class="field"><span>描述</span>
              <input v-model="f.description" placeholder="现场描述" @input="updateFinding('customerFeedback', f.id, { description: f.description })" />
            </label>
          </div>
        </template>
      </div>
    </section>

    <!-- ============ 3. 现场发现 ============ -->
    <section class="module">
      <button class="module-head" @click="toggle('findings')">
        <span>3. 现场发现</span>
        <span class="arrow" :class="{ open: panels.findings }">▾</span>
      </button>
      <div v-if="panels.findings" class="module-body">
        <div class="seg">
          <button v-for="s in ['UNFILLED', 'NONE_FOUND', 'FOUND']" :key="s" class="seg-btn"
            :class="{ on: observation('onSiteFindings').state === s }"
            @click="setObservationState('onSiteFindings', s as any)">
            {{ { UNFILLED: '未填写', NONE_FOUND: '无发现', FOUND: '有发现' }[s] }}
          </button>
        </div>
        <template v-if="observation('onSiteFindings').state === 'FOUND'">
          <button class="add" @click="addFinding('onSiteFindings')">+ 添加现场发现明细</button>
          <div v-for="f in observation('onSiteFindings').findings" :key="f.id" class="sub-card">
            <div class="sub-head">
              <span>发现明细</span>
              <button class="del" @click="removeFinding('onSiteFindings', f.id)">删除</button>
            </div>
            <label class="field"><span>虫种</span>
              <select v-model="f.pestTypeId" @change="updateFinding('onSiteFindings', f.id, { pestTypeId: f.pestTypeId })">
                <option v-for="p in pestTypes" :key="p.id" :value="p.id">{{ p.name }}</option>
              </select>
            </label>
            <label class="field"><span>区域</span>
              <select v-model="f.areaId" @change="updateFinding('onSiteFindings', f.id, { areaId: f.areaId })">
                <option v-for="a in areas" :key="a.id" :value="a.id">{{ a.name }}</option>
              </select>
            </label>
            <label class="field"><span>位置</span>
              <input v-model="f.position" placeholder="具体位置" @input="updateFinding('onSiteFindings', f.id, { position: f.position })" />
            </label>
            <label class="field"><span>数量</span>
              <select v-model="f.quantity.kind" @change="f.quantity = f.quantity.kind === 'EXACT' ? { kind: 'EXACT', value: 0 } : { kind: 'UNKNOWN' }">
                <option value="EXACT">明确数量</option>
                <option value="UNKNOWN">数量不明</option>
              </select>
            </label>
            <label v-if="f.quantity.kind === 'EXACT'" class="field"><span>具体数量</span>
              <input v-model.number="f.quantity.value" type="number" min="0" />
            </label>
            <label class="field"><span>描述</span>
              <input v-model="f.description" placeholder="现场描述" @input="updateFinding('onSiteFindings', f.id, { description: f.description })" />
            </label>
          </div>
        </template>
      </div>
    </section>

    <!-- ============ 4. 环境评估 ============ -->
    <section class="module">
      <button class="module-head" @click="toggle('assessment')">
        <span>4. 环境评估</span>
        <span class="arrow" :class="{ open: panels.assessment }">▾</span>
      </button>
      <div v-if="panels.assessment" class="module-body">
        <label class="field check"><input v-model="assessment.isConfirmed" type="checkbox" @change="updateAssessment({ isConfirmed: assessment.isConfirmed, confirmedAt: assessment.isConfirmed ? nowIso() : undefined })" /> 本次评估已确认</label>
        <label class="field"><span>门店类型</span>
          <select v-model="assessment.storeType" @change="updateAssessment({ storeType: assessment.storeType })">
            <option value="OPEN">开放式</option><option value="ENCLOSED">封闭式</option><option value="UNKNOWN">未知</option>
          </select>
        </label>
        <label class="field"><span>卷帘门</span>
          <select v-model="assessment.shutterDoor" @change="updateAssessment({ shutterDoor: assessment.shutterDoor })">
            <option v-for="o in presenceOptions" :key="o" :value="o">{{ presenceLabel[o] }}</option>
          </select>
        </label>
        <label class="field"><span>折叠门</span>
          <select v-model="assessment.foldingDoor" @change="updateAssessment({ foldingDoor: assessment.foldingDoor })">
            <option v-for="o in presenceOptions" :key="o" :value="o">{{ presenceLabel[o] }}</option>
          </select>
        </label>
        <label class="field"><span>风幕机</span>
          <select v-model="assessment.airCurtain" @change="updateAssessment({ airCurtain: assessment.airCurtain })">
            <option value="ON">开启</option><option value="OFF">关闭</option><option value="ABSENT">无</option><option value="UNKNOWN">未知</option>
          </select>
        </label>
        <label class="field"><span>风幕角度</span>
          <select v-model="assessment.airAngle" @change="updateAssessment({ airAngle: assessment.airAngle })">
            <option value="PASS">合格</option><option value="FAIL">不合格</option><option value="NOT_APPLICABLE">不适用</option><option value="UNKNOWN">未知</option>
          </select>
        </label>
        <label class="field"><span>敞开天花板</span>
          <select v-model="assessment.openCeiling" @change="updateAssessment({ openCeiling: assessment.openCeiling })">
            <option v-for="o in ynOptions" :key="o" :value="o">{{ ynLabel[o] }}</option>
          </select>
        </label>
        <label class="field"><span>附近装修</span>
          <select v-model="assessment.nearbyRenovation" @change="updateAssessment({ nearbyRenovation: assessment.nearbyRenovation })">
            <option v-for="o in ynOptions" :key="o" :value="o">{{ ynLabel[o] }}</option>
          </select>
        </label>
        <label class="field"><span>附近空置</span>
          <select v-model="assessment.nearbyVacancy" @change="updateAssessment({ nearbyVacancy: assessment.nearbyVacancy })">
            <option v-for="o in ynOptions" :key="o" :value="o">{{ ynLabel[o] }}</option>
          </select>
        </label>
        <label class="field"><span>绿化描述</span>
          <input v-model="assessment.greeneryDescription" placeholder="绿化情况" @input="updateAssessment({ greeneryDescription: assessment.greeneryDescription })" />
        </label>
        <label class="field"><span>备注</span>
          <input v-model="assessment.remarks" placeholder="其他说明" @input="updateAssessment({ remarks: assessment.remarks })" />
        </label>
      </div>
    </section>

    <!-- ============ 5. 设施点检 ============ -->
    <section class="module">
      <button class="module-head" @click="toggle('inspection')">
        <span>5. 设施点检</span>
        <span class="arrow" :class="{ open: panels.inspection }">▾</span>
      </button>
      <div v-if="panels.inspection" class="module-body">
        <p v-if="facilities.length === 0" class="empty">上下文无设施快照，请先下载离线上下文</p>
        <div v-for="fac in facilities" :key="fac.id" class="sub-card">
          <div class="sub-head">
            <span>{{ fac.typeName || fac.name || fac.code }}</span>
            <button class="del" @click="ensureInspection(fac)">点检</button>
          </div>
          <template v-if="inspections.find((i) => i.facilityId === fac.id)">
            <div v-for="i in inspections.filter((x) => x.facilityId === fac.id)" :key="i.id">
              <label class="field"><span>点检结果</span>
                <select v-model="i.checkResult" @change="updateInspection(i.id, { checkResult: i.checkResult })">
                  <option v-for="o in checkResultOptions" :key="o" :value="o">{{ checkResultLabel[o] }}</option>
                </select>
              </label>
              <template v-if="i.checkResult === 'CHECKED'">
                <label class="field"><span>设施状态</span>
                  <select v-model="i.facilityCondition" @change="updateInspection(i.id, { facilityCondition: i.facilityCondition })">
                    <option v-for="o in facilityConditionOptions" :key="o" :value="o">{{ facilityConditionLabel[o] }}</option>
                  </select>
                </label>
                <label class="field"><span>耗材动作</span>
                  <select v-model="i.consumableAction" @change="updateInspection(i.id, { consumableAction: i.consumableAction })">
                    <option v-for="o in consumableActionOptions" :key="o" :value="o">{{ consumableActionLabel[o] }}</option>
                  </select>
                </label>
                <div class="captures">
                  <button class="add" @click="addCapture(i.id)">+ 捕获记录</button>
                  <div v-for="(c, idx) in i.captures" :key="idx" class="cap-row">
                    <select v-model="c.pestTypeId" @change="updateCapture(i.id, idx, { pestTypeId: c.pestTypeId })">
                      <option v-for="p in pestTypes" :key="p.id" :value="p.id">{{ p.name }}</option>
                    </select>
                    <select v-model="c.quantity.kind" @change="c.quantity = c.quantity.kind === 'EXACT' ? { kind: 'EXACT', value: 0 } : { kind: 'UNKNOWN' }">
                      <option value="EXACT">明确</option><option value="UNKNOWN">不明</option>
                    </select>
                    <input v-if="c.quantity.kind === 'EXACT'" v-model.number="c.quantity.value" type="number" min="0" class="num" />
                    <button class="del" @click="removeCapture(i.id, idx)">删</button>
                  </div>
                </div>
              </template>
              <label v-else class="field"><span>未检查原因</span>
                <input v-model="i.notCheckedReason" placeholder="说明未检查原因" @input="updateInspection(i.id, { notCheckedReason: i.notCheckedReason })" />
              </label>
              <label class="field"><span>备注</span>
                <input v-model="i.remarks" placeholder="备注" @input="updateInspection(i.id, { remarks: i.remarks })" />
              </label>
            </div>
          </template>
        </div>
      </div>
    </section>

    <!-- ============ 6. 风险跟进 ============ -->
    <section class="module">
      <button class="module-head" @click="toggle('risks')">
        <span>6. 风险跟进</span>
        <span class="arrow" :class="{ open: panels.risks }">▾</span>
      </button>
      <div v-if="panels.risks" class="module-body">
        <button class="add" @click="addNewRisk">+ 新发现风险</button>
        <div v-for="r in newRisks" :key="r.id" class="sub-card">
          <div class="sub-head"><span>新风险</span><button class="del" @click="removeNewRisk(r.id)">删除</button></div>
          <label class="field"><span>位置</span><input v-model="r.position" placeholder="风险位置" @input="updateNewRisk(r.id, { position: r.position })" /></label>
          <label class="field"><span>描述</span><input v-model="r.description" placeholder="风险描述" @input="updateNewRisk(r.id, { description: r.description })" /></label>
          <label class="field"><span>建议</span><input v-model="r.suggestion" placeholder="整改建议" @input="updateNewRisk(r.id, { suggestion: r.suggestion })" /></label>
          <label class="field"><span>责任方</span><input v-model="r.responsibleParty" placeholder="责任方" @input="updateNewRisk(r.id, { responsibleParty: r.responsibleParty })" /></label>
          <label class="field"><span>期限</span><input v-model="r.dueDate" type="date" @input="updateNewRisk(r.id, { dueDate: r.dueDate })" /></label>
          <label class="field"><span>关联虫种</span>
            <div class="chips">
              <button v-for="p in pestTypes" :key="p.id" class="chip" :class="{ on: r.pestTypeIds.includes(p.id) }"
                @click="updateNewRisk(r.id, { pestTypeIds: togglePestType(r.pestTypeIds, p.id) })">{{ p.name }}</button>
            </div>
          </label>
        </div>

        <p v-if="openRisks.length === 0" class="empty">上下文无既有未关闭风险</p>
        <div v-for="risk in openRisks" :key="risk.id" class="sub-card">
          <div class="sub-head">
            <span>{{ risk.description || risk.position }}</span>
            <button class="del" @click="ensureFollowup(risk)">跟进</button>
          </div>
          <template v-if="riskFollowups.find((f) => f.riskId === risk.id)">
            <div v-for="f in riskFollowups.filter((x) => x.riskId === risk.id)" :key="f.id">
              <label class="field"><span>跟进状态</span>
                <select v-model="f.status" @change="updateFollowup(f.id, { status: f.status })">
                  <option v-for="o in followupStatusOptions" :key="o" :value="o">{{ followupStatusLabel[o] }}</option>
                </select>
              </label>
              <label v-if="f.status === 'NOT_CHECKED'" class="field"><span>未核查原因</span>
                <input v-model="f.notCheckedReason" placeholder="说明未核查原因" @input="updateFollowup(f.id, { notCheckedReason: f.notCheckedReason })" />
              </label>
              <label class="field"><span>跟进结果</span>
                <input v-model="f.result" placeholder="跟进结果" @input="updateFollowup(f.id, { result: f.result })" />
              </label>
            </div>
          </template>
        </div>
      </div>
    </section>

    <!-- ============ 7. 处置 / 用药 ============ -->
    <section class="module">
      <button class="module-head" @click="toggle('treatment')">
        <span>7. 处置与用药</span>
        <span class="arrow" :class="{ open: panels.treatment }">▾</span>
      </button>
      <div v-if="panels.treatment" class="module-body">
        <button class="add" @click="addTreatment">+ 添加处置措施</button>
        <div v-for="t in treatments" :key="t.id" class="sub-card">
          <div class="sub-head"><span>处置措施</span><button class="del" @click="removeTreatment(t.id)">删除</button></div>
          <label class="field"><span>措施类型</span>
            <select v-model="t.type" @change="updateTreatment(t.id, { type: t.type })">
              <option v-for="o in treatmentTypeOptions" :key="o" :value="o">{{ treatmentTypeLabel[o] }}</option>
            </select>
          </label>
          <label class="field"><span>位置</span><input v-model="t.position" placeholder="处置位置" @input="updateTreatment(t.id, { position: t.position })" /></label>
          <label class="field"><span>描述</span><input v-model="t.description" placeholder="措施描述" @input="updateTreatment(t.id, { description: t.description })" /></label>

          <template v-if="t.type === 'CHEMICAL'">
            <button class="add" @click="addChemicalUse(t.id)">+ 添加用药记录</button>
            <div v-for="(cu, idx) in t.chemicalUses" :key="idx" class="cap-row">
              <select v-model="cu.chemicalId" @change="updateChemicalUse(t.id, idx, { chemicalId: cu.chemicalId })">
                <option v-for="c in chemicals" :key="c.id" :value="c.id">{{ c.name }}</option>
              </select>
              <select v-model="cu.amountBasis" @change="updateChemicalUse(t.id, idx, { amountBasis: cu.amountBasis })">
                <option v-for="o in amountBasisOptions" :key="o" :value="o">{{ amountBasisLabel[o] }}</option>
              </select>
              <button class="del" @click="removeChemicalUse(t.id, idx)">删</button>
            </div>
            <div v-for="(cu, idx) in t.chemicalUses" :key="'detail' + idx" class="sub-card">
              <label class="field"><span>剂型</span><input v-model="cu.formulation" placeholder="剂型" @input="updateChemicalUse(t.id, idx, { formulation: cu.formulation })" /></label>
              <label class="field"><span>处理方法</span><input v-model="cu.method" placeholder="处理方法" @input="updateChemicalUse(t.id, idx, { method: cu.method })" /></label>
              <label class="field"><span>实际配比</span><input v-model="cu.concentrationText" placeholder="实际配比/浓度" @input="updateChemicalUse(t.id, idx, { concentrationText: cu.concentrationText })" /></label>
              <label class="field"><span>用量</span><input v-model="cu.amount" placeholder="用量（十进制字符串）" @input="updateChemicalUse(t.id, idx, { amount: cu.amount })" /></label>
              <label class="field"><span>单位</span><input v-model="cu.unitCode" placeholder="单位代码" @input="updateChemicalUse(t.id, idx, { unitCode: cu.unitCode })" /></label>
              <label class="field"><span>位置</span><input v-model="cu.position" placeholder="用药位置" @input="updateChemicalUse(t.id, idx, { position: cu.position })" /></label>
            </div>
          </template>
        </div>
      </div>
    </section>

    <!-- ============ 8. 总结 ============ -->
    <section class="module">
      <button class="module-head" @click="toggle('summary')">
        <span>8. 总结</span>
        <span class="arrow" :class="{ open: panels.summary }">▾</span>
      </button>
      <div v-if="panels.summary" class="module-body">
        <label class="field"><span>天气</span>
          <select v-model="summary.weather" @change="updateSummary({ weather: summary.weather })">
            <option v-for="o in weatherOptions" :key="o" :value="o">{{ weatherLabel[o] }}</option>
          </select>
        </label>
        <label class="field"><span>主要发现</span><textarea v-model="summary.mainFindings" rows="2" @input="updateSummary({ mainFindings: summary.mainFindings })"></textarea></label>
        <label class="field"><span>采取措施</span><textarea v-model="summary.measuresTaken" rows="2" @input="updateSummary({ measuresTaken: summary.measuresTaken })"></textarea></label>
        <label class="field"><span>未解决事项</span><textarea v-model="summary.unresolvedIssues" rows="2" @input="updateSummary({ unresolvedIssues: summary.unresolvedIssues })"></textarea></label>
        <label class="field"><span>客户配合事项</span><textarea v-model="summary.customerActions" rows="2" @input="updateSummary({ customerActions: summary.customerActions })"></textarea></label>
        <label class="field"><span>下次关注</span><textarea v-model="summary.nextFocus" rows="2" @input="updateSummary({ nextFocus: summary.nextFocus })"></textarea></label>
      </div>
    </section>
  </div>
</template>

<style scoped>
.offline-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.progress {
  font-size: 12px;
  color: var(--c-text-weak);
  text-align: right;
}
.module {
  background: #fff;
  border-radius: var(--radius-card);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.module-head {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: none;
  border: none;
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text);
  cursor: pointer;
}
.arrow {
  transition: transform 0.2s;
  color: var(--c-text-weak);
}
.arrow.open {
  transform: rotate(180deg);
}
.module-body {
  padding: 0 16px 16px;
  border-top: 1px solid var(--c-border);
  padding-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.field {
  display: block;
}
.field > span {
  display: block;
  font-size: 13px;
  color: var(--c-text-weak);
  margin-bottom: 6px;
}
.field input,
.field select,
.field textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 9px 12px;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  font-size: 14px;
  font-family: inherit;
  background: #fff;
}
.field textarea {
  resize: vertical;
}
.field.check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.field.check input {
  width: auto;
}
.seg {
  display: flex;
  gap: 6px;
}
.seg-btn {
  flex: 1;
  padding: 8px 0;
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  background: #fff;
  color: var(--c-text-weak);
  font-size: 13px;
  cursor: pointer;
}
.seg-btn.on {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}
.add {
  align-self: flex-start;
  padding: 6px 12px;
  border: 1px dashed var(--c-secondary);
  border-radius: var(--radius-card);
  background: #f0f8ff;
  color: var(--c-primary);
  font-size: 13px;
  cursor: pointer;
}
.sub-card {
  border: 1px solid var(--c-border);
  border-radius: var(--radius-card);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.sub-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
}
.del {
  padding: 2px 8px;
  border: none;
  border-radius: 6px;
  background: #fdecea;
  color: var(--c-danger);
  font-size: 12px;
  cursor: pointer;
}
.captures {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cap-row {
  display: flex;
  gap: 6px;
  align-items: center;
}
.cap-row select {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  font-size: 13px;
}
.cap-row .num {
  width: 70px;
  padding: 6px 8px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  font-size: 13px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.chip {
  padding: 4px 10px;
  border: 1px solid var(--c-border);
  border-radius: 14px;
  background: #fff;
  color: var(--c-text-weak);
  font-size: 12px;
  cursor: pointer;
}
.chip.on {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}
.empty {
  color: var(--c-text-weak);
  font-size: 13px;
  margin: 4px 0;
}
</style>
