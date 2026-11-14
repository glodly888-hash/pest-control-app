# 虫害治理 App 接口文档

V1.0 设计评审稿  2026年11月11日

本文定义安卓员工端与统一后端之间的接口契约，供产品、安卓、后端和测试人员评审与联调。范围对应现有 20 页白模，覆盖任务、现场记录、跨服务风险、离线同步、双签与正式报告；网页后台仅列出支撑这些流程所必需的管理接口。

本次交付为接口设计，现有白模仍使用本地演示数据。路径、字段、错误码、并发控制及同步协议属于本版补充设计，不表示后端已经部署，也不将 demo 中硬编码数量、自动勾选或模拟发布逻辑作为生产规则。

配套文件中，openapi.json 是可导入的机器可读契约，完整字段字典.md 是逐模型字段索引，联调示例.json 保存可校验的请求样例与摘要测试向量。若文字说明与接口定义出现差异，应提交评审修订，不由客户端自行猜测。

## 1 范围与设计约定

一家公司管理多个客户门店；员工使用安卓 App，客户只在员工手机上查看本次服务并现场确认，不创建客户登录账号。所有外部调用通过 /api/v1，业务接口默认鉴权。公司归属从登录会话确定，不能靠请求体 companyId 切换公司。

本版采用 REST 与 JSON，附件使用受控二进制上传。接口描述采用 OpenAPI 3.1.0；这是项目选择的契约版本，不表示采用最新规范版本。规范依据：https://spec.openapis.org/oas/v3.1.0.html 。

业务边界保持如下约定：服务完成与风险关闭独立；不同来源虫情不默认相加；签字绑定固定正文；正式报告成功生成后才完成服务；常规报告无需主管审批。主管介入只用于改派、取消、版本冲突等异常。

员工端没有采购库存、合同收费、客户小程序、远程签字、硬件接入或 AI 识虫接口。完整后台的员工管理、客户导入、周期计划生成、模板配置、备份管理及批量导出不在本版详细接口范围内，需要按后台页面另行补充。

### 1.1 演示页面与生产接口的差异

| demo 表现 | 接口设计要求 |
|---|---|
| 工作台固定显示待同步数量 | 从本机持久化队列统计；服务器只能返回已接收的提交状态 |
| 服务总结固定显示检查通过 | 必须调用 validations；任何阻断项均不开放签字 |
| 签字后继续编辑浏览器表单 | 原快照不改，新建内容版本并重新双方确认 |
| 新增设施和应急服务仅显示弹窗 | 分别提交 FacilityChange 与 EmergencyTaskInput |
| 卷帘门和折叠门合并一项 | 接口拆分 shutterDoor、foldingDoor；UI 应补齐，不能将一个答案复制为两个事实 |
| 未检查与无法确认在一个选择组 | 检查结果和数量状态分别保存；无法确认不是 0 |
| 演示签字可立即模拟发布 | 校验真实附件、双签及内容摘要后异步生成 PDF |
| 本地 HTML 退出后仍保留状态 | 正式 App 缓存按账号隔离，并实施本地加密和访问保护 |

### 1.2 页面与接口映射

| 页面 | 主要读取 | 主要提交 |
|---|---|---|
| 01 登录 | 配置由登录后拉取 | login、refresh、logout |
| 02 工作台 | getDashboard、listNotifications | readNotification |
| 03 任务列表 | listTasks | createEmergencyTask |
| 04 任务详情 | getTask、getDraft | createService、getTaskContext |
| 05 到店签到 | getStore | saveDraft 的 content.checkin |
| 06 服务前确认 | listNotices | noticeConfirmations、customerFeedback、onSiteFindings |
| 07 静态评估 | OfflineContext.contextBody.lastAssessment | assessment、commitFieldRecords |
| 08 设施列表 | listFacilities、getDraft | facilityChanges、commitFieldRecords |
| 09 设施点检 | context 中设施快照 | inspections、treatments |
| 10 风险台账 | listRisks | newRisks、commitFieldRecords |
| 11 整改跟进 | getRisk、listFollowups | riskFollowups；非本次服务用 createFollowup |
| 12 新增风险 | 字典、门店区域 | newRisks、commitFieldRecords |
| 13 防治措施 | 字典、当前虫情及风险 | treatments、chemicalUses |
| 14 服务总结 | getDraft、validateService | summary、freezeSnapshot |
| 15 报告预览 | getSnapshot | recordPreview |
| 16 员工签字 | getSnapshot | createSignature，role=STAFF |
| 17 客户确认 | 仅当前快照 | createSignature，role=CUSTOMER；recordRefusal |
| 18 同步中心 | getSubmission、listSyncExceptions | submitOfflineWork、verifyUpload |
| 19 正式报告 | getReport、downloadReport | createCorrection；安卓本机分享 |
| 20 我的 | getMe、listStores、listReports | logout |

表中的读取和提交名称是 openapi.json 的 operationId。所有现场模块通过同一 saveDraft 保存完整服务内容，不能把局部表单直接当作完整 content 覆盖提交。

## 2 通用协议

### 2.1 地址与请求头

占位地址为 https://api.example.invalid/api/v1，联调前替换为实际 HTTPS 域名。示例不包含真实账号、密钥或客户资料。

| 项目 | 约定 |
|---|---|
| Authorization | Bearer accessToken；登录和刷新接口除外 |
| Content-Type | 普通请求 application/json；附件字节 application/octet-stream |
| Idempotency-Key | 已登录的 POST、PUT 请求必填 UUID；同一操作重试保持不变 |
| If-Match | saveDraft、changeTask 必填服务端返回的强 ETag |
| requestId | 每个 JSON 响应包含服务器请求 UUID，排障使用 |
| ID | 业务标识使用 UUID v4；离线创建时由客户端先生成 |
| 时间 | 带偏移的 RFC3339 时间；另存服务器 receivedAt，不替代现场发生时间 |
| 日期 | plannedDate、dueDate 按门店 timezone 解释；不按手机当前时区重新归日 |
| 金额与用量 | 本版无金额；药剂用量为十进制字符串，amount 与 unitCode 分开 |
| 空值 | 可选字段不适用时省略；本版不接受 null，0、空数组、未填语义不同 |

所有资源标识都必须二次校验公司、门店及角色范围，包括请求体内的附件、设施、风险、药剂、服务、签名和报告 ID。越权访问返回 404 RESOURCE_NOT_FOUND，不能借搜索、统计、下载或错误信息泄露资源是否存在。403 用于已知本人范围内操作权限不足。

### 2.2 返回格式

```json
{
  "data": { "isAccepted": true, "receivedAt": "2026-11-11T01:10:00Z" },
  "requestId": "00000000-0000-4000-8000-000000000001"
}
```

列表 data 固定为 items、nextCursor、hasMore。limit 默认 20、最大 100；cursor 为服务端不透明游标，与当前过滤条件和授权范围绑定。列表按业务排序字段再按 id 排序；游标失效返回 400 INVALID_CURSOR，客户端重新拉取，不重放过期游标。数组全集只用于有界配置和完整离线上下文，不适用于普通列表。

### 2.3 幂等与并发

Idempotency-Key 的作用域是公司、账号、设备、HTTP 方法和规范化路径。第一次请求持久保存请求摘要和结果，同 key 同请求返回原结果；同 key 不同请求返回 409 IDEMPOTENCY_KEY_REUSED。并发相同 key 只执行一次，其余返回已有结果或 409 OPERATION_IN_PROGRESS 并带重试提示。

建议响应缓存至少保留 30 天，此期限是待确认的运行配置。即使缓存过期，也必须靠业务唯一键持续防重复：任务 ID、事件 ID、附件 ID、提交包 ID、签名 ID，以及 serviceId 加 snapshotId 的报告唯一约束。不能仅依赖短期 Redis 缓存。

草稿使用 GET 返回的 ETag，例如 "draft-7"；PUT 时带 If-Match: "draft-7"。缺失返回 428 PRECONDITION_REQUIRED，版本不同返回 412 REVISION_MISMATCH。失败时保留本地草稿，由用户比较或重基，不做最后写入覆盖。嵌入的风险及设施记录另用 expectedRevision 校验，返回 409 LEDGER_CONFLICT。

If-Match 使用强比较的语义依据 HTTP 规范：https://www.rfc-editor.org/rfc/rfc9110.html#name-if-match 。

## 3 数据模型与保存边界

### 3.1 主要对象关系

门店 Store 拥有设施 Facility 和长期风险 Risk。服务任务 Task 对应一次现场服务 Service，服务内维护可恢复的草稿 ServiceDraft。草稿冻结为不可变 Snapshot 后才能签字；一组有效签字触发 PublicationJob，成功后生成正式 Report。

OfflineContext 是某次下载时的门店、员工、设施、未关闭风险、字典、须知和报告模板快照，包含权限凭证及资源清单。签署正文只引用不可变 contextId 和 contextDigest；不能在生成报告时重新读取最新门店名称或药剂名称。

设施移除和字典停用只影响后续使用；旧 context、历史事件和报告保持可追溯。当前台账的后续变化不回写已冻结的服务内容。

### 3.2 草稿字段

saveDraft 的请求体为 DraftWrite：contextId、content、savedAt。草稿允许模块缺省，不能因此默认无异常；冻结前所有适用模块必须通过校验。

| content 字段 | 模型 | 对应业务 |
|---|---|---|
| checkin | Checkin | 到店时间、位置精度、门头照片、定位异常原因 |
| noticeConfirmations | NoticeConfirmation[] | 服务须知版本及主动确认 |
| customerFeedback | ObservationSection | 客户来源、数量或数量不明 |
| onSiteFindings | ObservationSection | 现场虫体或虫迹，独立保存 |
| assessment | StaticAssessment | 本次环境评估及历史值确认 |
| inspections | Inspection[] | 设施逐点检查、状态、耗材、捕获数量 |
| facilityChanges | FacilityChange[] | 新增、移动、停用、移除事件 |
| newRisks | NewRisk[] | 本次发现的风险及责任和期限 |
| riskFollowups | RiskFollowup[] | 既有风险跟进、复核或未核查原因 |
| treatments | Treatment[] | 处置对象、方法、实际用药及留证 |
| summary | Summary | 主要发现、措施、未解决事项、人工风险评定 |
| serviceAttachments | AttachmentRef[] | 本次服务附件，门头照片可复用 |

保存草稿只代表服务器已保留工作副本。保存时做字段类型和范围校验；检查照片或总结缺失等问题由完整性校验返回，允许继续补录。已冻结草稿拒绝直接 PUT，返回 409 CONTENT_FROZEN。

### 3.3 长期台账提交

为了让未签字服务中的新风险及时进入跟进台账，本版补充 field-commits 接口。客户端在设施变更、新风险、整改跟进或静态评估确认保存后调用它；只保存草稿而尚未提交时，页面必须显示“草稿／待提交”，不能显示已写入长期台账。

一次 field-commits 针对指定 draftRevision，事务内提交本服务所有相关事件。每个事件 ID 唯一：首次创建写入事件；相同 ID 和相同业务内容重放返回原回执；同 ID 的内容改变返回 EVENT_IMMUTABLE，客户端需新事件并注明更正关系。已提交的事件不能从草稿删除以撤销台账；更正需显式补偿事件或授权人员处理，保留历史。

新增设施或新风险在保存后的草稿仍保留原事件，用回执标记已提交，不再改其 expectedRevision。之后对同一资源的新动作使用新事件 ID 和最新资源版本。设施变更和风险跟进的纠正通过 supersedesEventId 引用旧事件；新风险档案内容的复杂更正属于后台补偿契约，不通过修改已提交的 NewRisk 事件绕过。新风险在本次报告只出现一次，后续跟进独立追加。

新增设施的创建版本约定为 1，FacilityChange.expectedRevision 填 0，关联 Inspection.facilityRevision 填 1；服务器以创建回执校验。已有设施的点检引用下载时的历史版本，设施后续移动不改写本次检查位置。

并发冲突导致该次 field-commits 全部回滚，已存草稿保留。提交成功返回事件与资源版本；签字前要求 fieldCommitRevision 等于当前 draftRevision，未涉及台账的草稿变动也可重新提交空增量更新回执。

此接口不把任务标记完成，不关闭所有风险，不生成报告。报告更正再次提交同一事件时按事件 ID 去重，避免重复新增设施、风险或跟进记录。

### 3.4 数量与点检

| 情况 | 保存方式 | 统计规则 |
|---|---|---|
| 明确 0 | Quantity.kind=EXACT，value=0 | 作为有效检查零数量 |
| 明确正数 | EXACT，value 为非负整数 | 只纳入所属来源、虫种合计 |
| 数量不明 | UNKNOWN，不传 value | 计入数量不明记录数 |
| 未检查设施 | checkResult=NOT_CHECKED，原因必填，captures=[] | 计入未检查，不计为零捕获 |
| 设施无法确认 | checkResult=UNCONFIRMABLE，原因必填 | 单独统计，不能当作正常 |
| 已检查但部分虫种数不清 | CHECKED，该虫种 Quantity=UNKNOWN | 检查覆盖率计已检查；该虫种不求和 |

设施状态 facilityCondition 与耗材动作 consumableAction 独立。检查照片至少一张；未检查时可没有检查照片，但必须有原因。CHECKED 时需 inspectedAt、设施状态及耗材动作，并对该设施类型要求的虫种分类填写明确数量或未知；灭蝇灯应包含蝇类、果蝇／蚤蝇／蛾蠓、其他等配置分类。

客户反馈和现场发现的 state 分为 UNFILLED、NONE_FOUND、FOUND。NONE_FOUND 必须 findings=[]；FOUND 至少一条，source 必须匹配所属模块。eventGroupId 可关联同一事件，但三种来源仍分表展示，不默认汇成一个总虫数。

### 3.5 处置和药剂

每个正数量、数量不明但确有发现的 Finding 或 Capture，以及设施异常，都必须关联有效 treatmentIds 或填写 noTreatmentReason。清洁、封堵、设施调整、物理捕杀、观察复查均可作为措施，不要求必须用药。

Treatment.targets 指向本服务虫情、点检或门店风险；targets 为空时 preventivePurpose 必填。药剂措施的 chemicalUses 至少一条，其他措施不携带用药记录。药剂必须在该上下文版本有效，服务端也核对停用政策；确属离线前使用的历史有效药剂保留实际事实，并在异常中提醒，不静默改名或改用量。

ChemicalUse 保存 chemicalId、chemicalRevision、靶标虫害、剂型、处理方法、实际配比、amount、unitCode、amountBasis、位置和操作人。amountBasis 区分 CONCENTRATE 原药、PREPARED_LIQUID 配制液、PER_AREA 单位面积；单位与口径须匹配。系统不推荐剂量、不推断浓度，示例不提供通用用药参数。

## 4 状态与发布条件

### 4.1 三类状态分别管理

| 状态对象 | 取值及触发 |
|---|---|
| 任务 | PENDING 待执行；有效签到后 IN_PROGRESS；员工确认或拒签后 AWAITING_CONFIRMATION；正式 PDF 发布成功后 COMPLETED；授权取消后 CANCELLED |
| 本地同步 | LOCAL_ONLY、PENDING、SYNCING、FAILED、SYNCED；由本机队列维护，服务器接收包用 SubmissionReceipt 表达 |
| 报告 | DRAFT、QUEUED、FAILED、PUBLISHED、SUPERSEDED；只有 PUBLISHED 或被替代的历史正式版本可下载 PDF |
| 草稿阶段 | EDITABLE、FROZEN、AWAITING_CONFIRMATION、PUBLISHING、PUBLISHED；签后修改必须新版本 |
| 整改 | PENDING 待整改、IN_PROGRESS 整改中、AWAITING_REVIEW 待复核、CLOSED 已关闭 |

服务端状态为权威，App 不可提交 status=COMPLETED。离线客户签完后可显示“现场确认完成／待同步”，不能显示正式服务已完成。同步元数据成功也不等于 PDF 已发布。

### 4.2 风险流转

NO_CHANGE 保持原状态；IN_PROGRESS 将待整改转为整改中；SUBMIT_REVIEW 从待整改或整改中转待复核；APPROVE 仅待复核可执行并关闭；REJECT 仅待复核可执行并退回整改中；NOT_CHECKED 保持状态并要求原因；REOPEN 仅已关闭可执行并退回待整改。

APPROVE、REJECT 需要 risk:review 权限，具体授予哪些员工由后台配置，不能仅因一线员工角色自动拥有。提交复核或复核通过至少提供一张改善照片。客户提供的照片须标明 CUSTOMER_PROVIDED 与材料提供人；后台代录使用 BACKOFFICE。

服务完成无需全部风险关闭，但下载时已有的全部未关闭风险都必须在本服务中有跟进或未核查原因。离线期间新增的其他服务风险不擅自补入已签正文，下一次同步可提示补做；如果补入本次报告，必须重建正文并重新签字。

## 5 签字与固定内容

### 5.1 正文摘要

签字对象是 SnapshotPayload：schemaVersion、serviceId、taskId、contextId、contextDigest、content。先按 RFC8785 的 JCS 规则序列化，再对 UTF-8 字节计算 SHA-256，结果为 contentHash。附件引用中的 attachmentId、sha256、mediaType、sizeBytes 都在正文中，不能签完后换照片。

JCS 依据：https://www.rfc-editor.org/rfc/rfc8785 。必须采用兼容实现，不能用“普通 JSON.stringify 后排序顶层键”代替。数组顺序是正文的一部分，冻结后不能排序重排；不进行未约定的 Unicode 归一化。数量为受限整数，用量为十进制字符串，禁止 NaN 和 Infinity。

contextDigest 对 OfflineContext.contextBody 整体计算，其中包含门店与报告品牌、字典、须知、模板版本和模板哈希。权限过期时间等控制字段不在报告正文里；服务端保存同一 contextBody 的不可变副本。正文不包含临时下载地址、上传进度、access token、接收时间或签名图。

### 5.2 预览与双方签署

1. saveDraft 后提交台账，再调用 validations；通过后 freezeSnapshot，服务端重新计算 contentHash 并冻结。
2. 员工核对正文，主动确认声明，保存 STAFF 签字。staffUserId 必须等于当前会话员工，不接收冒名员工 ID。
3. 客户只看到当前服务预览。前台可见且渲染完整的阅读时长累计至少 5000 毫秒，离开页面或切后台时暂停。
4. 记录 preview-session，随后客户填写姓名、主动确认，手写签名。评级 1 至 5 星选填，不传表示未评价，不默认 5 分。
5. CUSTOMER 签字引用同一 snapshotId、contentHash、有效 STAFF signatureId 和阅读回执；顺序及姓名、图片、时间均校验。

客户姓名、评价和签名属于签署附页，不写回已由员工签署的服务正文。每个 SignatureInput 另计算 attestationHash：对该对象除 attestationHash 以外的所有实际字段做 JCS 后 SHA-256。客户凭证通过 staffSignatureId 关联先前员工凭证；两个签字都指向同一服务正文摘要。修改客户姓名、评价、声明或签名也要重新取得客户凭证，不能沿用旧图。未发布时以 supersedesSignatureId 和 replacementReason 明确替换，旧凭证保留但失效；替换员工凭证还会使其关联客户凭证失效。已发布时只能走报告更正并重新双签。

客户签署发生时间取设备 signedAt，正式报告另列服务器 receivedAt 以便审计。首次服务实际结束时间取最终有效客户现场签署时间；拒签或缺席时保持待确认，不以之后的上传时间冒充现场结束。报告更正不得改写原服务起止时间，更正签署时间作为新版本签署时间单独展示。

离线预检的附件条件是原文件已安全保存在本机且摘要正确；联网后仍须补做服务端 VERIFIED 校验。离线预览和签字使用同一冻结内容、模板及摘要算法，本地先保存 PreviewSessionInput 和 SignatureInput，联网时通过提交包校验。离线阅读时长和设备时钟属于可审核的客户端证据，不能声称能证明真实阅读或提供第三方电子认证。本版为手写确认记录，不引入证书签名服务。

### 5.3 拒签与更正

ABSENT 或 REFUSED 只记录缺席／拒签原因，不能生成客户签字。客户以后在现场确认时须重新完整预览当前版本。第一次签字后正文变化，调用 draft-revisions，旧快照和签字仍归旧版，新版须双签。

已发布报告通过 corrections 新建草稿，需 report:correct 权限。旧报告继续有效，直到新版本 PDF 成功生成；发布事务再将旧版置为 SUPERSEDED。更正草稿失败或放弃不撤销旧报告，服务仍维持已完成，并另外显示存在更正草稿。

## 6 附件与离线同步

### 6.1 附件上传顺序

每张照片和签名先生成 attachmentId 并写入 App 私有存储。客户端计算原始字节 sha256，创建 attachment-upload-session，上传二进制，再提交 completions。只有服务端重新校验后返回 VERIFIED，才可用于正式签字和发布。正文中的照片引用必须与已验证字节一致。

首期采用单文件完整上传重试，不承诺分块断点续传。网络中断后同一 attachmentId 和相同摘要可恢复会话或重新上传；会话过期时重新创建会话，但不改变附件 ID。已 VERIFIED 的对象不可覆盖，重新压缩或裁剪会改变字节，必须新建附件并在签字前更新正文。

签名只接受 image/png 或 image/jpeg；PDF 只用于服务附件。服务器检查真实类型、文件大小及内容安全。上传地址属于受保护 API，不允许公共读取。下载正式 PDF 后由安卓 FileProvider 调用系统分享；不创建客户账号或公开报告链接。

### 6.2 离线下载

首次登录、权限更新和资料下载需要网络。任务上下文包含完整在用设施、全部未关闭风险、必要的历史评估、业务字典、须知、报告模板及附件清单。模板或必要资源下载缺失时，不可标记“可离线开始”。下载量超过服务器配置时应明确分批任务或限制本次任务包，不允许静默截断设施或风险列表。

已缓存授权门店可创建本地应急任务；contextBody 不带 task 时，在本地生成 EmergencyTaskInput 和 Task UUID。联网提交时服务端核验下载授权、门店权限和重复任务候选，不能因为 UUID 新就绕过业务去重。

### 6.3 离线提交包

OfflineSubmission 以一次服务为单位，带稳定 id、deviceId、contextId、任务及派工版本、serviceId、baseDraftRevision、完整草稿，以及可选的快照、预览和签字。taskId 与 emergencyTask 必须二选一；已签字时 snapshotId、snapshotPayload、contentHash 和 confirmations 必须齐全。请求发布时还带 publicationRequest。

附件先上传；提交包只引用附件 ID 和摘要。App 在上传前持久记录包内容与 idempotency key。若请求超时先按 submissionId 查询回执；不存在时用同一内容和 ID 重试，不临时换 UUID。

服务器先把接收包及其附件引用安全保存，返回 202 QUEUED；后续状态为 PROCESSING、APPLIED、CONFLICT 或 REJECTED。APPLIED 代表作业、台账、快照和签署记录已通过校验并原子落地，不代表报告已发布；publicationJobId 仅指向后续生成作业。

同一包内部先校验账号和原下载凭证，再校验任务归属、baseDraftRevision、现场事件版本、附件、固定正文与双方签署，之后一次事务落地。任一冲突都不能只提交一半。原始接收包不因事务回滚而丢失。报告生成作为事务后异步任务，失败可独立重试。

### 6.4 冲突处理

| 情况 | 服务端处理 | 客户端行为 |
|---|---|---|
| 离线期间改派 | CONFLICT，保存原包和附件为异常 | 原员工只查看自己的提交，等待归属处理 |
| 离线期间取消 | CONFLICT，不发布 | 展示取消原因及资料保留状态 |
| 草稿版本不一致 | CONFLICT，不覆盖 | 对比本地与已获授权的服务端版本，重基再提交 |
| 设施或风险并发变更 | CONFLICT，现场事件事务回滚 | 拉取最新台账，逐条处理；正文变更重新签字 |
| 权限已撤销或离线凭证过期 | 仅受限接收原凭证关联资料到隔离区 | 不继续读取门店或执行正式发布，交主管处理 |
| access token 过期 | 401，刷新后重试；刷新失败要求原账号登录 | 本地资料仍保留，不切到别的账号上传 |
| 图片缺失或摘要不同 | REJECTED，给出路径和附件 ID | 修复上传；换图须新快照及双签 |

受限接收要求能证明原账号和下载凭证的关联，不开放匿名上传。账号已禁用且无法重新认证时，客户端继续保留加密原始数据，需管理员恢复受限上传通道后提交，不能把旧凭证当成永久访问授权。

resolveSyncException 只处理异常：改回原员工仍需要刷新归属并重新提交；保留证据仅归档；重基后新包使用新 submissionId，并通过业务 ID 避免重复。任何处理结果都不能擅自代签或直接把冲突包发布为正式报告。

本地清理至少满足：作业已 APPLIED、全部附件 VERIFIED、正式报告已发布或资料已明确归档，且保留策略允许。不得因“上传请求返回成功”自动删除原照片、签名或作业；数据量不足时提示用户，不能静默清空未同步资料。

## 7 完整性校验

validations 返回 canSign 与 issues；每条 issue 包含 ruleId、JSON Pointer path、screen 和 message，可直接跳转白模对应页面。结构类型或越权错误仍按 HTTP 错误处理。

| 规则 | 阻断条件 |
|---|---|
| V01 任务有效 | 当前员工非执行人、任务取消或上下文不匹配 |
| V02 签到完整 | 缺到店时间或门头照片；定位异常无原因；正常定位缺经纬度或精度 |
| V03 须知确认 | 下载时适用的须知版本未主动确认或未记录确认时间 |
| V04 来源明确 | 客户反馈或现场发现仍为 UNFILLED；有发现却无明细 |
| V05 环境评估 | 未确认本次评估，或必填环境项为 UNKNOWN 且备注未解释 |
| V06 设施覆盖 | 下载时在用设施及本次新增设施遗漏点检或未检查原因；已移除设施须有变更原因与本次处置说明 |
| V07 点检真实性 | 已检查无本次照片、耗材动作或设施状态；未检查却填零捕获或正常状态 |
| V08 虫情处置 | 发现或捕获未关联措施且没有未处置原因；关联对象不属于本服务或本门店 |
| V09 历史风险 | 下载时全部未关闭风险未逐条跟进或说明未核查原因 |
| V10 新风险与复核 | 新风险无位置、描述、照片、责任人、跟进人或期限；复核动作无权限／改善证据 |
| V11 用药记录 | 药剂处理缺药剂版本、靶标、方法、浓度、用量、单位或位置；单位与口径冲突 |
| V12 总结与风险等级 | 主要发现和措施未核对；需展示虫种缺人工评定及依据，未评估也需原因 |
| V13 服务时长 | 实际现场确认时长低于配置建议值且无 shortServiceReason；不强制等待到时长 |
| V14 台账及附件 | 草稿版本未提交台账；引用附件未验证、归属错误或摘要不一致 |
| V15 签字有效 | 正文摘要不一致、签署顺序不符、客户未主动确认、阅读不足或签名缺失 |
| V16 发布前复核 | 生成前任务再次改派／取消、必要附件不可读取、当前快照失效或已被修订 |

V01 至 V14 在冻结前校验；V15 在签署及发布时校验；V16 在发布事务前重新校验。V13 在冻结时预检，在客户确认后按实际时间终检。离线发现低时长问题时应在客户端先提示；若联网终检发现缺原因，只能修订正文重新确认，不能服务器自动补写。

## 8 错误码与重试

```json
{
  "error": {
    "code": "REVISION_MISMATCH",
    "message": "草稿已更新，请比较并保留本地内容",
    "details": [{"path": "/content", "code": "REVISION_MISMATCH", "message": "当前版本为8", "currentRevision": 8}],
    "retryable": false
  },
  "requestId": "00000000-0000-4000-8000-000000000002"
}
```

| HTTP | code | 处理 |
|---|---|---|
| 400 | INVALID_REQUEST、INVALID_CURSOR | 修正参数；不盲目重试 |
| 401 | TOKEN_EXPIRED、UNAUTHENTICATED | 刷新一次；失败重新登录原账号 |
| 403 | PERMISSION_DENIED、ACCOUNT_DISABLED | 不提供操作；原数据保留 |
| 404 | RESOURCE_NOT_FOUND | 不存在或无数据访问范围，使用同一提示 |
| 409 | DUPLICATE_CANDIDATE | 返回授权范围内候选任务，确认复用或说明独立事件 |
| 409 | IDEMPOTENCY_KEY_REUSED、EVENT_IMMUTABLE | 修正请求；不得换 key 绕过业务唯一约束 |
| 409 | OPERATION_IN_PROGRESS | 按提示稍后查询或重试相同操作 |
| 409 | LEDGER_CONFLICT、ASSIGNMENT_CHANGED、TASK_CANCELLED | 保留现场数据并进入冲突处理 |
| 409 | CONTENT_FROZEN、SIGNATURE_INVALID、REPORT_NOT_READY | 修订版本或等待正确阶段，不能强制推进 |
| 412 | REVISION_MISMATCH | 保存本地，获取最新版后明确解决冲突 |
| 422 | VALIDATION_FAILED、ATTACHMENT_HASH_MISMATCH、PREVIEW_TOO_SHORT | 根据 details 跳转补齐；换正文后重签 |
| 428 | PRECONDITION_REQUIRED | 补齐 If-Match，不能猜版本 |
| 429 | RATE_LIMITED | 遵守 Retry-After |
| 500／503 | INTERNAL_ERROR、SERVICE_UNAVAILABLE | 指数退避并加随机抖动；先查操作回执再重试 |

服务端不得返回堆栈、数据库语句、凭证或其他门店内容。建议重试间隔 1、2、4、8、16、30 秒并加抖动；后台持续失败保留队列，允许人工重试。该间隔是初始建议值，可由客户端配置调整。

## 9 接口目录与逐项说明

以下接口相对 /api/v1。每项给出 operationId、请求和响应模型、权限及关键行为；完整字段类型、枚举、参数位置、成功响应和统一错误结构见配套 OpenAPI。未列请求体表示不接收 JSON 请求体，不能自行添加状态更新字段。

### 账号

#### 01 账号登录

POST /auth/sessions

标识：login。权限：无需access token。
请求：LoginInput。成功：201，data 为 Session。
公司编号用于定位登录主体，不提供多公司入驻。首次登录需联网；错误不暴露账号是否存在。

#### 02 刷新令牌

POST /auth/refreshes

标识：refresh。权限：无需access token。
请求：RefreshInput。成功：200，data 为 Session。
轮换refresh token并绑定deviceId；短暂重复请求使用相同refresh token应返回同一会话结果或确定性错误，不创建并行刷新链。

#### 03 退出当前设备会话

DELETE /auth/sessions/current

标识：logout。权限：各类已登录员工，按数据范围授权。
请求：无。成功：204，无响应正文。
幂等撤销当前会话；本地未同步资料保留在原账号加密区，其他账号不能读取。

#### 04 当前员工与权限

GET /me

标识：getMe。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 User。

### 基础

#### 05 客户端配置

GET /app-config

标识：getAppConfig。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 AppConfig。

#### 06 业务字典全集

GET /dictionaries

标识：getDictionaries。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 DictionarySet。
按version返回一套有界配置快照；停用项保留供历史显示，不允许新增选用。

#### 07 服务须知

GET /service-notices

标识：listNotices。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 分页<Notice>。
查询参数：cursor（string，选填）；limit（integer，选填）。

#### 08 员工通知

GET /notifications

标识：listNotifications。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 分页<Notification>。
查询参数：cursor（string，选填）；limit（integer，选填）。

#### 09 标记通知已读

PUT /notifications/{notificationId}/read-receipt

标识：readNotification。权限：各类已登录员工，按数据范围授权。
请求：{ readAt }。成功：200，data 为 Ack。
请求字段：readAt（string / date-time，必填）。

### 任务

#### 10 工作台汇总

GET /dashboard

标识：getDashboard。权限：EMPLOYEE。
请求：无。成功：200，data 为 Dashboard。
查询参数：date（string / date，必填）。
仅返回当前员工授权数据；本地待同步数量由App队列计算，不由此接口伪造。

#### 11 任务列表和日历

GET /tasks

标识：listTasks。权限：EMPLOYEE。
请求：无。成功：200，data 为 分页<Task>。
查询参数：cursor（string，选填）；limit（integer，选填）；startDate（string / date，选填）；endDate（string / date，选填）；status（TaskStatus，选填）；keyword（string，选填）；storeId（string / uuid，选填）。
默认本人任务；日期按门店业务时区，按plannedDate、id稳定排序。

#### 12 任务详情

GET /tasks/{taskId}

标识：getTask。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 Task。

#### 13 员工创建应急任务

POST /tasks

标识：createEmergencyTask。权限：EMPLOYEE。
请求：EmergencyTaskInput。成功：201，data 为 Task。
仅已授权门店；assignee取token本人。ID重复同内容返回原任务；同店同日近似任务返回409 DUPLICATE_CANDIDATE及候选任务ID，由用户选用已有任务或注明独立事件后另建。

### 管理依赖

#### 14 派工变更和取消

POST /tasks/{taskId}/changes

标识：changeTask。权限：DISPATCHER、SUPERVISOR、ADMIN。
请求：TaskChangeInput。成功：200，data 为 Task。
并发控制：If-Match 必填，使用该资源 GET 返回的 ETag。
If-Match使用任务ETag。ASSIGN/REASSIGN需assigneeId，RESCHEDULE需plannedDate；原因必填。已完成不可改历史日期；改派/取消递增assignmentVersion。

### 门店

#### 15 授权门店列表

GET /stores

标识：listStores。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 分页<Store>。
查询参数：cursor（string，选填）；limit（integer，选填）；keyword（string，选填）。

#### 16 门店详情

GET /stores/{storeId}

标识：getStore。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 Store。

### 现场

#### 17 设施台账

GET /stores/{storeId}/facilities

标识：listFacilities。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 分页<Facility>。
查询参数：cursor（string，选填）；limit（integer，选填）；typeId（string / uuid，选填）；status（string，选填）。

### 风险

#### 18 风险台账

GET /stores/{storeId}/risks

标识：listRisks。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 分页<Risk>。
查询参数：cursor（string，选填）；limit（integer，选填）；status（RiskStatus，选填）。

#### 19 风险详情

GET /risks/{riskId}

标识：getRisk。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 Risk。

#### 20 风险跟进历史

GET /risks/{riskId}/followups

标识：listFollowups。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 分页<RiskFollowupRecord>。
查询参数：cursor（string，选填）；limit（integer，选填）。

#### 21 服务之外的整改跟进

POST /risks/{riskId}/followups

标识：createFollowup。权限：各类已登录员工，按数据范围授权。
请求：FollowupInput。成功：201，data 为 RiskFollowupRecord。
服务内跟进写入ServiceContent.riskFollowups并通过field-commits提交，避免双写。独立跟进用此接口；expectedRiskRevision做乐观锁，冲突409。APPROVE/REJECT还需risk:review权限；REOPEN只用于已关闭风险。

### 离线

#### 22 下载任务上下文

GET /tasks/{taskId}/offline-context

标识：getTaskContext。权限：EMPLOYEE。
请求：无。成功：200，data 为 OfflineContext。
包含完整在用设施、全部未关闭风险、门店/字典/模板快照和资源清单。响应生成或复用不可变context；同一contextId内容永不变。

#### 23 下载门店应急上下文

GET /stores/{storeId}/offline-context

标识：getStoreContext。权限：EMPLOYEE。
请求：无。成功：200，data 为 OfflineContext。
用于已授权门店离线应急。contextBody不包含task；任务后来在本地创建。

#### 24 确认离线资料完整下载

POST /offline-contexts/{contextId}/download-receipts

标识：confirmDownload。权限：EMPLOYEE。
请求：GrantReceipt。成功：200，data 为 Ack。
客户端校验全部requiredAssets哈希后才可标记可离线开始。接口只记下载回执，不替代本地文件校验。

#### 25 下载模板及资料资源

GET /offline-assets/{assetId}/content

标识：downloadOfflineAsset。权限：EMPLOYEE。
请求：无。成功：200，直接返回文件二进制，无JSON外层。
逐个鉴权；不可公开直链。绑定上下文的模板哈希用于离线预览和报告一致性。

### 现场

#### 26 创建本次服务草稿

POST /tasks/{taskId}/services

标识：createService。权限：EMPLOYEE。
请求：CreateServiceInput。成功：201，data 为 ServiceDraft。
每任务至多一个有效服务；ID可离线生成。相同任务重复创建返回原服务。创建不代表签到，签到保存后由服务器推进进行中。

#### 27 恢复作业草稿

GET /services/{serviceId}/draft

标识：getDraft。权限：EMPLOYEE。
请求：无。成功：200，data 为 ServiceDraft。

#### 28 保存完整作业草稿

PUT /services/{serviceId}/draft

标识：saveDraft。权限：EMPLOYEE。
请求：DraftWrite。成功：200，data 为 ServiceDraft。
并发控制：If-Match 必填，使用该资源 GET 返回的 ETag。
完整替换content；调用方先在本地合并模块，遗漏字段会从草稿移除。If-Match必须等于GET返回ETag；草稿允许未填，不自动填0。签字后禁止原地修改。每次成功递增draftRevision。

#### 29 提交现场台账变更

POST /services/{serviceId}/field-commits

标识：commitFieldRecords。权限：EMPLOYEE。
请求：FieldCommitInput。成功：200，data 为 FieldCommitReceipt。
将草稿中的新增设施、设施变化、新风险、风险跟进及已确认静态评估提交到长期台账，独立于客户签字和服务完成。按事件ID及内容哈希去重，所有台账变化一次事务提交；并发冲突全批回滚。报告仍引用本次固定记录。

### 签字报告

#### 30 签字前完整性校验

POST /services/{serviceId}/validations

标识：validateService。权限：EMPLOYEE。
请求：ValidateInput。成功：200，data 为 ValidationResult。
canSign=false仍返回200；结构错误422。返回页面路由和字段路径，不执行签字、冻结或发布。

#### 31 冻结待签内容

POST /services/{serviceId}/snapshots

标识：freezeSnapshot。权限：EMPLOYEE。
请求：FreezeInput。成功：201，data 为 Snapshot。
重新运行完整性校验，核验draftRevision、fieldCommitRevision及contentHash；创建不可变快照，草稿进入FROZEN。客户姓名、评价、签名另存签署凭证，不修改该正文。

#### 32 读取报告预览数据

GET /snapshots/{snapshotId}

标识：getSnapshot。权限：EMPLOYEE。
请求：无。成功：200，data 为 Snapshot。
根据同一不可变context和模板渲染。客户模式由App仅显示此快照，不显示工作台和其他门店。

#### 33 记录客户阅读过程

POST /snapshots/{snapshotId}/preview-sessions

标识：recordPreview。权限：EMPLOYEE。
请求：PreviewSessionInput。成功：201，data 为 PreviewReceipt。
可见阅读时长至少5000ms才canAcknowledge=true。离线证据联网后检验，不能证明客户理解或防止被篡改客户端伪造。

#### 34 保存员工或客户签字

POST /snapshots/{snapshotId}/signatures

标识：createSignature。权限：EMPLOYEE。
请求：SignatureInput。成功：201，data 为 SignatureRecord。
同一快照必须先员工后客户；员工ID必须等于登录人；客户必须引用有效员工签名和阅读回执。签名附件应已VERIFIED。替换未发布凭证需supersedesSignatureId及replacementReason；替换员工凭证同时使关联客户凭证失效。已发布不可替换。

#### 35 登记客户缺席或拒签

POST /services/{serviceId}/confirmation-exceptions

标识：recordRefusal。权限：EMPLOYEE。
请求：RefusalInput。成功：201，data 为 Ack。
任务置待确认；记录不能代替客户签名，不能触发正式报告。

#### 36 签后未发布内容修订

POST /services/{serviceId}/draft-revisions

标识：reopenDraft。权限：EMPLOYEE。
请求：RevisionInput。成功：201，data 为 ServiceDraft。
复制旧内容为新草稿；旧快照及签字只对旧版本有效，不带入新版。已发布服务必须使用报告corrections接口。

#### 37 请求生成正式报告

POST /services/{serviceId}/publications

标识：publishReport。权限：EMPLOYEE。
请求：PublishInput。成功：202，data 为 PublicationJob。
重新检查权限、任务归属、附件和双签；不设常规主管审批。返回202只代表排队，不代表已完成。唯一约束serviceId+snapshotId，不因不同请求ID生成重复报告。

#### 38 查询报告生成结果

GET /publication-jobs/{jobId}

标识：getPublication。权限：EMPLOYEE。
请求：无。成功：200，data 为 PublicationJob。

#### 39 重试报告生成

POST /publication-jobs/{jobId}/retries

标识：retryPublication。权限：EMPLOYEE。
请求：{ reason }。成功：202，data 为 PublicationJob。
请求字段：reason（string，必填）。
仅可重试FAILED且canRetry=true的作业；复用原快照与报告版本，不创建另一个正式报告。

#### 40 我的历史报告

GET /reports

标识：listReports。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 分页<Report>。
查询参数：cursor（string，选填）；limit（integer，选填）；storeId（string / uuid，选填）；serviceId（string / uuid，选填）；status（ReportStatus，选填）。

#### 41 报告信息与版本

GET /reports/{reportId}

标识：getReport。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 Report。

#### 42 下载或系统分享PDF

GET /reports/{reportId}/content

标识：downloadReport。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，直接返回文件二进制，无JSON外层。
只允许PUBLISHED/SUPERSEDED；返回application/pdf和Content-Disposition。App保存到私有目录后经FileProvider调用安卓系统分享，无公开分享令牌。后续无法撤回接收人已保存的文件。

#### 43 创建报告更正草稿

POST /reports/{reportId}/corrections

标识：createCorrection。权限：EMPLOYEE。
请求：CorrectionInput。成功：201，data 为 ServiceDraft。
需report:correct权限；当前正式版本保持有效。新草稿需重新预览双签。新PDF发布成功时在同一事务把旧报告置SUPERSEDED。失败不改变旧报告。

### 附件

#### 44 创建或恢复附件上传

POST /attachment-upload-sessions

标识：createUpload。权限：EMPLOYEE。
请求：UploadInput。成功：201，data 为 UploadSession。
先检查当前授权或原下载凭证。原员工上传改派任务资料只准进入隔离区，不授予新任务访问。按attachmentId+sha256复用，禁止全局跨用户去重泄漏。

#### 45 上传完整附件二进制

PUT /attachment-upload-sessions/{uploadId}/content

标识：uploadBytes。权限：EMPLOYEE。
请求：二进制文件。成功：200，data 为 Ack。
Content-Type: application/octet-stream；首期完整文件重传，不承诺断点续传。校验大小、真实MIME及sha256；覆盖仅允许该会话同一内容。

#### 46 校验附件上传完成

POST /attachment-upload-sessions/{uploadId}/completions

标识：verifyUpload。权限：EMPLOYEE。
请求：UploadCompleteInput。成功：200，data 为 Attachment。
服务器自行重算哈希和大小后标记VERIFIED，不能信任客户端完成标记；失败可重传同一文件，不得替换已验证内容。

#### 47 查询附件状态

GET /attachments/{attachmentId}

标识：getAttachment。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 Attachment。

#### 48 读取受保护附件

GET /attachments/{attachmentId}/content

标识：downloadAttachment。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，直接返回文件二进制，无JSON外层。
门店和所属记录双重授权；签名仅服务授权人可读。隔离区原员工只能读自己的上传件，主管按异常权限读取。

### 离线

#### 49 提交离线服务包

POST /sync/submissions

标识：submitOfflineWork。权限：EMPLOYEE。
请求：OfflineSubmission。成功：202，data 为 SubmissionReceipt。
先上传附件，再提交元数据；一次只提交一项服务。接受后持久保存原包。后台原子校验任务、版本、台账变更、快照和签字；冲突不覆盖现有数据，保留为异常。通过后可按publicationRequest排队生成报告。

#### 50 查询离线提交状态

GET /sync/submissions/{submissionId}

标识：getSubmission。权限：EMPLOYEE。
请求：无。成功：200，data 为 SubmissionReceipt。

#### 51 同步异常列表

GET /sync/exceptions

标识：listSyncExceptions。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 分页<SyncException>。
查询参数：cursor（string，选填）；limit（integer，选填）。
员工只看本人异常，主管按区域权限查看。不得在异常提示中泄露新执行人的作业内容。

#### 52 查看同步异常

GET /sync/exceptions/{exceptionId}

标识：getSyncException。权限：各类已登录员工，按数据范围授权。
请求：无。成功：200，data 为 SyncException。

### 管理依赖

#### 53 处理改派和同步冲突

POST /sync/exceptions/{exceptionId}/resolutions

标识：resolveSyncException。权限：SUPERVISOR、ADMIN。
请求：ResolutionInput。成功：200，data 为 SyncException。
仅处理异常，不批准常规报告。改回原员工仍需刷新上下文并重提；重基导致正文变化必须重新双签。KEEP_AS_EVIDENCE仅归档原包，不发布报告。

#### 54 基础服务与虫情统计

GET /statistics/services

标识：getStatistics。权限：SUPERVISOR、ADMIN。
请求：无。成功：200，data 为 ServiceStatistics。
查询参数：startDate（string / date，必填）；endDate（string / date，必填）；storeId（string / uuid，选填）；employeeId（string / uuid，选填）。
按来源分组；UNKNOWN不参加求和。报告更正按每服务最新有效版本统计，不把被替代版本重复计算。


## 10 联调顺序与请求示例

### 10.1 联网服务顺序

登录并读取配置 → 拉取任务与离线上下文 → 下载校验必要资源 → 创建服务 → 保存草稿及上传附件 → 提交现场台账 → 完整性校验 → 冻结正文 → 员工签字 → 客户阅读并签字 → 请求报告生成 → 查询作业成功 → 下载正式 PDF。

客户界面不能凭本地开关伪造角色或访问其他页面。安卓端可提供临时客户模式锁定，只暴露当前快照；后端始终以员工会话操作并记录代理的客户现场签署身份，不能将其当作客户已登录。

### 10.2 关键请求示意

```http
PUT /api/v1/services/{serviceId}/draft
Authorization: Bearer <accessToken>
Idempotency-Key: <本次保存操作UUID>
If-Match: "draft-7"
Content-Type: application/json
```

请求体使用联调示例.json 中的 draftWrite；其中是完整 content，包含 checkin、客户反馈、现场发现、静态评估、点检、风险跟进、处置和总结，不是局部 PATCH。成功返回 draftRevision=8 及 ETag: "draft-8"。网络超时重试时保留请求体、If-Match 和 key，不主动把版本改为 8。

```http
POST /api/v1/services/{serviceId}/publications
Authorization: Bearer <accessToken>
Idempotency-Key: <发布请求UUID>
Content-Type: application/json
```

```json
{
  "id": "00000000-0000-4000-8000-000000000101",
  "snapshotId": "00000000-0000-4000-8000-000000000102",
  "staffSignatureId": "00000000-0000-4000-8000-000000000103",
  "customerSignatureId": "00000000-0000-4000-8000-000000000104"
}
```

HTTP 202 返回 PublicationJob。轮询 getPublication 至 SUCCEEDED；只有获得 reportId 且 getReport.status=PUBLISHED 后才显示完成并开放分享。FAILED 使用 retryPublication；BLOCKED 按错误处理，不自动改为成功。

### 10.3 离线场景

本地准备好的已签包使用同一 UUID、上下文、内容摘要和签名图片上传，不把本地签字转换成服务端新正文。联调示例.json 提供 offlineSubmission、snapshotPayload、contentHash、员工与客户凭证和阅读记录，可用于客户端与服务端 JCS 摘要一致性测试。示例附件摘要仅为合约测试值，不是可上传的真实文件。

## 11 验收与待确认配置

### 11.1 接口验收矩阵

| 场景 | 预期结果 |
|---|---|
| 正常服务全流程 | 从创建服务到正式 PDF 成功，任务才转 COMPLETED |
| 来源统计样例 | 客户飞虫 15、设施飞虫 10、其他 38 分开返回，无总和 63 |
| 无虫但有结构风险 | 允许记录建议和整改，不强制用药或虚构捕获数 |
| 未检查设施 | 原因必填；不计正常、不按零捕获求和 |
| 现场记录刷新或崩溃 | 本地恢复；服务端按草稿版本读取，不丢照片引用 |
| 同一次保存重试 | 同 key 同请求返回原结果，draftRevision 不二次递增 |
| 双设备保存同一草稿 | 后到请求返回 412，本地数据保留 |
| 附件中断重传 | 不产生重复附件；摘要错误不能 VERIFIED |
| 离线完整双签 | 联网后应用一次，正式报告只生成一份 |
| 离线改派或取消 | 原包隔离保留，不覆盖现任员工，也不发布 |
| 服务期间新增风险未获客户签字 | 台账可独立提交并持续跟进，任务仍待确认 |
| 客户阅读不足 5 秒 | 返回 PREVIEW_TOO_SHORT，不能创建有效客户签字 |
| 未选评价 | 保存为未评价，不能自动变成满分 |
| 签后换照片或改正文 | 摘要变化，旧签字不可沿用 |
| PDF 生成失败 | 服务未完成，原作业可重试，不重建签名 |
| 已发布报告更正 | 保留原版，新版需双签；成功后旧版被替代 |
| 风险在下次服务关闭 | 旧报告仍展示原快照，不追改历史统计 |
| 未授权ID或附件下载 | 不返回数据；错误不暴露其他门店是否存在 |
| 重放台账提交或更正报告 | 事件不重复，统计只取服务最新有效报告 |
| 更正时再次签字 | 原现场服务时长不变，更正签署时间另列 |

### 11.2 联调前需确认

| 项目 | 本版处理 |
|---|---|
| 服务域名、环境和技术栈 | 不预设实现框架；将占位地址替换为实际环境 |
| 访问令牌及刷新令牌期限 | Session 明确返回到期时间；具体期限由安全策略确认 |
| 离线授权期限和撤权处理 | AppConfig.offlineGrantHours 下发；过期资料隔离保留，不自动发布 |
| 单图、PDF、提交包与下载包上限 | AppConfig 下发，不在业务代码写死；超限不能静默丢记录 |
| 图片压缩和保留周期 | 压缩必须发生在冻结前；签后不改字节，保存周期统一配置 |
| 风险复核及报告更正授权 | 以 risk:review、report:correct 权限控制，明确哪些员工可执行 |
| 台账事件的更正操作 | 本版不可变事件先行；复杂撤销需后台补偿事件页与详细契约 |
| 应急任务近似重复规则 | 确认时间窗口和比较维度；ID唯一与业务去重同时保留 |
| 超范围签到阈值与定位精度 | 从公司配置确定；异常必须留原因，不强制阻止所有现场作业 |
| 报告编号格式 | 服务唯一编号加版本；最终前缀及编号规则由公司确认 |

以上待确认项不阻碍页面字段和核心流程评审，但上线前必须形成配置与验收值。正式开发还需补充后台完整接口、数据库与对象存储设计、运行监控、备份恢复和性能容量方案。

### 11.3 交付验证边界

本次检查接口定义结构、引用、operationId 唯一性、路径参数、请求与响应模型，以及示例数据和内容摘要的一致性，并对 Word 转 PDF 后的页面进行版式检查。检查不等于真实后端的鉴权、并发、离线恢复或报告生成测试；这些需要在实现后按上述矩阵联调验收。
