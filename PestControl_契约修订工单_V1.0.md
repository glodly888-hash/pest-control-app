# 虫害治理 App 契约修订工单 V1.0

> 生成日期：2026-11-12
> 触发依据：架构文档《接口到模块和数据表映射》第 116 条——「既有部分 maxLength=2000 必须与物理列宽对齐后再启动契约测试，禁止静默截断。」
> 比对对象：`openapi.yaml` 组件 schema 的 maxLength × `V001__initial_schema.sql` 物理列宽
> 验证环境：MySQL 8.4.13（Docker，本机 3307 端口）

---

## 一、复验结论（G0 第一条硬判据）

| 判据 | 期望 | 实测 | 结果 |
|---|---|---|---|
| 建表数量 | 44 张 | 44 张 | ✅ 通过 |
| 外键数量 | 96 条 | 96 条 | ✅ 通过 |
| 索引数量 | — | 279 个 | — |
| MySQL 版本 | 8.4（生产基线） | 8.4.13 | ✅ 一次建成 |

**结论：架构文档担心的「DDL 只在 9.6 验证过、8.4 未验证」的隐患已消除。44 张表 + 96 条外键在 MySQL 8.4.13 上一遍建成，无语法兼容性问题。**

---

## 二、必须修订的契约差异（maxLength > 列宽，存在静默截断风险）

共 **16 处**（不含 id 的 BINARY(16) 正常映射）。按风险排序：

### 高风险（溢出 ≥ 50 倍，或字段为枚举/编码类）

| # | schema 字段 | 接口 maxLength | 表列 | 列宽 | 建议处理 |
|---|---|---|---|---|---|
| 1 | Store.code | 2000 | store.code | 40 | maxLength 改为 40 |
| 2 | Store.timezone | 2000 | store.timezone | 64 | maxLength 改为 64 |
| 3 | Facility.floor | 2000 | facility.floor | 40 | maxLength 改为 40 |
| 4 | Facility.status | 2000 | facility.status | 16 | maxLength 改为 16 |
| 5 | PublicationJob.status | 2000 | publication_job.status | 16 | maxLength 改为 16 |
| 6 | UploadSession.status | 2000 | upload_session.status | 16 | maxLength 改为 16 |
| 7 | Attachment.status | 2000 | attachment.status | 16 | maxLength 改为 16 |
| 8 | Attachment.purpose | 2000 | attachment.purpose | 32 | maxLength 改为 32 |
| 9 | SyncException.status | 2000 | sync_exception.status | 24 | maxLength 改为 24 |
| 10 | RiskFollowup.result | 2000 | risk_followup.result | 32 | maxLength 改为 32 |

> 这些字段本质是**枚举/编码/状态值**，列宽 16–64 完全够用，是接口侧 maxLength 模板化地统一写了 2000 导致的。**应改接口侧（maxLength 对齐列宽），不改数据库**。

### 中风险（溢出 2–12 倍，自由文本字段）

| # | schema 字段 | 接口 maxLength | 表列 | 列宽 | 建议处理 |
|---|---|---|---|---|---|
| 11 | Store.name | 2000 | store.name | 160 | maxLength 改为 160 |
| 12 | Store.address | 2000 | store.address | 500 | maxLength 改为 500 |
| 13 | Facility.position | 2000 | facility.position | 300 | maxLength 改为 300 |
| 14 | Risk.position | 2000 | risk.position | 300 | maxLength 改为 300 |
| 15 | TaskChangeInput.reason | 2000 | task_change.reason | 1000 | maxLength 改为 1000 |
| 16 | SyncException.status | 2000 | sync_exception.status | 24 | maxLength 改为 24 |

> 这 5 处是自由文本。若业务上确实需要更长（如 address 超过 500），应**改数据库列宽**而非压接口。需产品确认：门店地址、设施位置、风险位置、派工变更原因，实际最长的业务输入是多长。

---

## 三、join / 解密展示字段（已处理 + 待确认）

OpenAPI 有 95 处 `maxLength=2000` 字段「无同名列对应」。分三类：

### 1. 已对齐来源表列宽（本轮已改）

| 字段 | 来源 | 改为 |
|---|---|---|
| Store.brandName | brand.name | 160 |
| Task.storeName | store.name | 160 |
| User.teamName | org_unit.name | 100 |
| User.name | app_user.name | 80 |
| Task.serviceType | service_task.service_type | 24 |
| Task.source | snapshot_finding.source | 24 |
| Finding.source | snapshot_finding.source | 24 |
| SourceStatistics.source | snapshot_finding.source | 24 |
| Risk.floor / NewRisk.floor / FacilityChange.floor | facility.floor | 40 |
| Finding.position / NewRisk.position / FacilityChange.position / ChemicalUse.position / Treatment.position | facility.position | 300 |
| Risk.responsibleParty / NewRisk.responsibleParty | risk.responsible_party | 100 |
| RiskFollowup.notCheckedReason / Inspection.notCheckedReason | snapshot_inspection.not_checked_reason | 1000 |
| Inspection.checkResult | snapshot_inspection.check_result | 24 |
| Inspection.consumableAction | snapshot_inspection.consumable_action | 24 |
| PublicationJob.errorCode | publication_job.error_code | 80 |
| PublicationJob.errorMessage | publication_job.error_message | 1000 |
| Report.reportNumber | service_report.report_number | 80 |
| SignatureInput.role / OfflineAsset.role | service_signature.role | 16 |
| SignatureInput.replacementReason | service_signature.replacement_reason | 1000 |
| EmergencyTaskInput.reason / RefusalInput.reason / RevisionInput.reason / CorrectionInput.reason / ResolutionInput.reason / FacilityChange.reason | confirmation_exception.reason | 1000 |
| TaskChangeInput.action / FacilityChange.action | audit_log.action | 100 |
| Notification.resourceType | audit_log.resource_type | 64 |
| UploadInput.purpose | attachment.purpose | 32 |
| ServiceDraft.phase | service.phase | 24 |
| OfflineContext.schemaVersion / SnapshotPayload.schemaVersion | service_draft.schema_version | 20 |

### 2. 已决策（解密字段，2026-11-12 老板拍板：按行业惯例默认值，冻结前可再评审）

| 字段 | 来源 | 决策值 | 说明 |
|---|---|---|---|
| Store.contactName | contact_cipher 解密 | maxLength **50** | 中文姓名惯例上限 |
| Store.contactPhone | contact_cipher 解密 | maxLength **32** | 含 +86、空格、分机号 |
| Risk.contactName | risk.contact_cipher 解密 | maxLength **50** | 中文姓名惯例上限 |

> 数据库存 `VARBINARY(2048)` 加密字节（联系人资料整块加密，密钥在库外），接口暴露解密后明文。明文长度是业务约定，已按行业惯例定为 50/32/50。若后续发现姓名/电话有更长需求，评审时再调。

### 3. 已决策（接口比数据库更严，2026-11-12 老板拍板放宽）

| 字段 | 物理列 | 决策值 | 说明 |
|---|---|---|---|
| Task.instructions | service_task.instructions **TEXT** | maxLength **8000** | 物理 TEXT 无上限，接口原限 2000 会不合理拒绝长任务说明，放宽到 8000 覆盖绝大多数场景 |

### 3. 无需处理（存 JSON 列 / 枚举 / 内部标识，无截断风险）

`Finding.description`、`Summary.*`、`StaticAssessment.*`、`ChemicalUse.*`、`Treatment.*`、`Inspection.*`、`Capture.*`、`NewRisk.*`、`ErrorDetail.*`、`ValidationIssue.*`、`SyncException.message/resolution` 等，最终序列化进 `content_json` / 快照 JSON 列，或为枚举/内部标识值，`maxLength=2000` 属「过度宽松但不致命」，**不改**（避免过度收紧误伤合法输入）。

---

## 四、不需修改的「伪差异」（说明用，避免误报）

### 1. id 字段 maxLength=36 × BINARY(16)

涉及 `notification / store / facility / risk / risk_followup / offline_asset / offline_context / preview_session / publication_job / upload_session / attachment / offline_submission / sync_exception` 的 id。

**这不是缺陷**。接口层 UUID 是 36 字符字符串（`xxxxxxxx-xxxx-...`），数据库存 BINARY(16) 二进制，后端在 hex↔binary 之间转换。属于设计如此，**不要动**。

### 2. 55 个未匹配到单表的 schema

`Capture / Inspection / Quantity / Treatment / Finding / Location / Checkin / ChemicalUse / ...` 等，是嵌套 DTO 或视图对象，最终序列化进 `service_draft.content_json`（JSON 列）或 `snapshot_*` 的 JSON 字段，**不映射到单列**，无截断风险。

---

## 五、处理原则与下一步

1. **方向**：默认「改接口 maxLength 对齐物理列宽」，数据库 DDL 不动（除非产品确认某字段业务上确实要更长）。
2. **已执行（2026-11-12，三批共 56 处）**：15 处存储字段 + 3 处 join 字段（brandName/storeName/teamName）+ 30 处 DTO 字段 + 8 处通用字段，全部对齐物理列宽，`check_contract.py` 回归通过「无真实截断风险」。
3. **产品需确认的 4 个自由文本字段**：store.address（500）、facility.position（300）、risk.position（300）、task_change.reason（1000）——已对齐物理列宽，若业务上确实需要更长，评审时再改 DDL。
4. **已决策（老板拍板，2026-11-12）**：3 个解密字段 contactName/contactPhone/contactName 明文 maxLength 定为 50/32/50；Task.instructions 放宽到 8000。均已同步改 openapi.yaml + openapi.json + 完整字段字典.md。
5. **责任**：契约修订归后端负责人，W1 内完成 openapi.yaml 的 maxLength 修正 + 契约测试基线。
6. **验证脚本**：比对逻辑已固化为脚本，修订后重跑即可回归验证（`.workbuddy/build/check_contract.py`）。

---

## 附：复验环境

- 容器：`pest-mysql84`（mysql:8.4，映射本机 3307:3306）
- 数据库：`pest`，root 密码 `pest_dev_root`（仅本地开发用，勿提交到生产）
- 迁移脚本：`pest-backend/pest-db/src/main/resources/db/migration/V001__initial_schema.sql`
