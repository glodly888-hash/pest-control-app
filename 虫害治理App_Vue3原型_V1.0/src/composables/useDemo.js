import { reactive, computed, watch } from "vue";
import {
  seedTasks,
  seedService,
  samplePhoto,
  clone,
  demoDate,
} from "../data/seed.js";
import { pages } from "../data/pages.js";
const KEY = "sheweisi-pest-vue-prototype-v1";
const initial = {
  schema: 1,
  offline: false,
  downloaded: true,
  activeTask: "task-4273",
  tasks: seedTasks(),
  services: {},
  selectedFacility: "I0001",
  selectedRisk: "risk-1",
};
let saved;
try {
  saved = JSON.parse(localStorage.getItem(KEY) || "null");
} catch {
  saved = null;
}
export const state = reactive(saved?.schema === 1 ? saved : initial);
export const ui = reactive({ message: "", storageError: "", dialog: null });
export const task = computed(
  () => state.tasks.find((t) => t.id === state.activeTask) || state.tasks[0],
);
if (!state.services[state.activeTask])
  state.services[state.activeTask] = seedService();
export const service = computed(() => state.services[state.activeTask]);
state.services[state.activeTask].signatureDrafts ||= { staff: "", client: "" };
export const locked = computed(() => !!service.value.snapshot);
let toastTimer;
export function toast(message) {
  ui.message = message;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => (ui.message = ""), 3200);
}
export function persist() {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
    ui.storageError = "";
    return true;
  } catch {
    ui.storageError =
      "本机存储空间不足或不可用。当前修改尚未保存，请先导出演示资料。";
    return false;
  }
}
watch(state, persist, { deep: true, flush: "sync" });
export function pageHref(id, query = {}) {
  const p = pages.find((p) => p.id === id);
  const params = new URLSearchParams(query);
  if (new URLSearchParams(location.search).has("screen"))
    params.set("screen", "1");
  return p.file + (params.size ? "?" + params : "");
}
export function go(id, query = {}) {
  if (!persist()) return toast("请先处理本机保存失败");
  location.href = pageHref(id, query);
}
export function selectTask(id) {
  state.activeTask = id;
  if (!state.services[id]) state.services[id] = seedService();
  state.selectedFacility = "I0001";
  state.selectedRisk = "risk-1";
  go("detail");
}
export function taskStatus(t) {
  const s = state.services[t.id];
  return s?.publishedReports.length
    ? "已完成"
    : s?.signatures.staff
      ? "待确认"
      : s?.checkin.time
        ? "进行中"
        : "待执行";
}
export function modal(title, text, actionLabel = "", onConfirm = null) {
  ui.dialog = { title, text, actionLabel, onConfirm };
}
export function saveModule(name, next) {
  service.value.modules[name] = true;
  if (persist()) {
    toast("已保存到本机");
    if (next) go(next);
  }
}
export function validation() {
  const s = service.value;
  const errors = [];
  const add = (label, page) => errors.push({ label, page });
  if (!s.checkin.time) add("请完成到店签到", "checkin");
  if (!s.intake.notice || !s.modules.intake) add("请完成服务前确认", "intake");
  if (s.intake.onsite === "未检查") add("请确认现场发现情况", "intake");
  if (!s.assessment.confirmed || !s.modules.static)
    add("请核对本次静态评估", "static");
  const fs = s.facilities.filter((f) => f.status === "在用");
  const validCount = (value) =>
    value !== "" && Number.isInteger(Number(value)) && Number(value) >= 0;
  if (
    fs.some(
      (f) =>
        !f.saved ||
        f.result === "待检查" ||
        (f.result !== "已检查" && !f.reason.trim()) ||
        (f.result === "已检查" &&
          (!f.photos.length ||
            (f.quantityKind === "明确数量" &&
              ["flies", "others", "rodents", "cockroaches"].some(
                (k) => !validCount(f[k]),
              )))),
    )
  )
    add("请补全设施检查结果、数量、照片或未检查原因", "devices");
  if (
    s.intake.feedback === "有发现" &&
    s.intake.quantityKind === "明确数量" &&
    !validCount(s.intake.quantity)
  )
    add("客户反馈数量应为非负整数", "intake");
  if (
    s.intake.onsite === "有发现" &&
    s.intake.onsiteQuantityKind === "明确数量" &&
    !validCount(s.intake.onsiteQuantity)
  )
    add("现场发现数量应为非负整数", "intake");
  if (s.risks.some((r) => r.state !== "已关闭" && !r.checked))
    add("请逐条跟进未关闭风险", "risks");
  if (!s.treatment.saved) add("请保存处置措施或未处置原因", "treatment");
  const t = s.treatment;
  if (
    !t.position.trim() ||
    !t.targets.length ||
    (t.type === "本次未处置"
      ? !t.reason.trim()
      : t.type !== "药剂处理" && !t.description.trim())
  )
    add("请补全处置位置、关联来源与说明", "treatment");
  if (
    t.type === "药剂处理" &&
    (["drug", "form", "method", "concentration", "dose", "unit"].some(
      (k) => !String(t[k]).trim(),
    ) ||
      !Number.isFinite(Number(t.dose)) ||
      Number(t.dose) <= 0)
  )
    add("请补全实际用药记录", "treatment");
  const discoveries = [
    ["客户反馈", s.intake.feedback === "有发现"],
    ["现场发现", s.intake.onsite === "有发现"],
    [
      "设施捕获",
      fs.some(
        (f) =>
          f.result === "已检查" &&
          ["flies", "others", "rodents", "cockroaches"].some(
            (k) => Number(f[k]) > 0,
          ),
      ),
    ],
  ];
  if (discoveries.some(([source, has]) => has && !t.targets.includes(source)))
    add("请为各虫情来源关联处置措施或未处置原因", "treatment");
  if (!s.summary.text.trim()) add("请填写服务总结", "summary");
  if (!s.summary.basis.trim()) add("请填写虫害风险评定依据", "summary");
  if (
    s.checkin.time &&
    (Date.now() - Date.parse(s.checkin.time)) / 60000 < 30 &&
    !s.summary.durationReason.trim()
  )
    add("服务不足建议时长，请说明原因", "summary");
  return errors;
}
export function fillCompleteDemo() {
  if (locked.value) return toast("请先开始新的内容版本");
  const s = seedService();
  s.version = service.value.version;
  s.publishedReports = clone(service.value.publishedReports);
  s.checkin.time = new Date(Date.now() - 42 * 60000).toISOString();
  s.intake.notice = true;
  s.intake.onsite = "未发现";
  s.assessment.confirmed = true;
  s.modules = { checkin: true, intake: true, static: true, treatment: true };
  s.facilities.forEach((f) => {
    f.result = "已检查";
    f.saved = true;
    f.photos = [samplePhoto("本次检查示例")];
    f.treatment = "清洁处理";
  });
  s.risks.forEach((r) => {
    r.checked = true;
    r.note = "本次已复核，仍需门店继续整改。";
  });
  s.treatment.saved = true;
  s.summary.text =
    "本次检查设施9处，记录客户反馈飞虫15只、设施捕获飞虫10只及其他38只，完成清洁处理。7条风险继续跟进。";
  s.summary.level = "中";
  s.summary.basis = "清洁池周边存在飞虫活动，部分结构风险仍待改善。";
  state.services[state.activeTask] = s;
  toast("已载入完整演示记录，签字仍需本人操作");
}
export function freezeAndSign(image) {
  if (validation().length) return toast("请先补全服务记录");
  const s = service.value;
  if (!s.snapshot)
    s.snapshot = {
      task: clone(task.value),
      content: clone({
        checkin: s.checkin,
        intake: s.intake,
        assessment: s.assessment,
        facilities: s.facilities,
        risks: s.risks,
        treatment: s.treatment,
        summary: s.summary,
      }),
      version: s.version,
    };
  s.signatures.staff = {
    image,
    at: new Date().toISOString(),
    name: "陈工",
    version: s.version,
  };
  s.signatures.client = null;
  s.reviewVersion = 0;
  s.client.read = false;
  go("clientsign");
}
export function startRevision() {
  const s = service.value;
  s.version++;
  s.snapshot = null;
  s.signatures = { staff: null, client: null };
  s.signatureDrafts = { staff: "", client: "" };
  s.reviewVersion = 0;
  s.client.read = false;
  s.syncState = "待同步";
  s.syncFailure = "";
  toast("已建立新内容版本，请修改后重新双签");
}
export function exportData() {
  downloadFile(
    "虫控原型演示资料.json",
    JSON.stringify(state, null, 2),
    "application/json",
  );
}
export function downloadFile(name, text, type) {
  const a = document.createElement("a");
  const url = URL.createObjectURL(new Blob([text], { type }));
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function resetDemo() {
  localStorage.removeItem(KEY);
  location.reload();
}
export function useDemo() {
  return {
    state,
    ui,
    task,
    service,
    locked,
    pages,
    go,
    toast,
    modal,
    saveModule,
    validation,
    fillCompleteDemo,
    startRevision,
    taskStatus,
    selectTask,
    exportData,
    demoDate,
  };
}
