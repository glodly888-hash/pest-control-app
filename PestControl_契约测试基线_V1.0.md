# 虫害治理 App 契约测试基线 V1.0

> 生成日期：2026-11-12
> 用途：W1 契约冻结后，后端契约测试的字段长度权威依据
> 数据来源：openapi.yaml（已修订）× V001__initial_schema.sql（MySQL 8.4.13 复验通过）

---

## 一、基线结论

- 44 张表 + 96 条外键在 MySQL 8.4.13 一遍建成，无兼容问题。
- openapi.yaml 的 maxLength 已与物理列宽对齐（15 处存储字段 + 3 处 join 字段），`check_contract.py` 回归「无真实截断风险」。
- 本清单是契约测试的**字段长度权威值**，测试用例按此断言，禁止再出现「接口放宽、数据库截断」。

---

## 二、字段长度权威值（已对齐）

### 存储字段（maxLength == 物理列宽）

| schema 字段 | 权威 maxLength | 对应表列 |
|---|---|---|
| Store.code | 40 | store.code |
| Store.name | 160 | store.name |
| Store.address | 500 | store.address |
| Store.timezone | 64 | store.timezone |
| TaskChangeInput.reason | 1000 | task_change.reason |
| Facility.floor | 40 | facility.floor |
| Facility.position | 300 | facility.position |
| Facility.status | 16 | facility.status |
| Risk.position | 300 | risk.position |
| RiskFollowup.result | 32 | risk_followup.result |
| PublicationJob.status | 16 | publication_job.status |
| UploadSession.status | 16 | upload_session.status |
| Attachment.status | 16 | attachment.status |
| Attachment.purpose | 32 | attachment.purpose |
| SyncException.status | 24 | sync_exception.status |

### join 展示字段（maxLength == 来源表列宽）

| schema 字段 | 权威 maxLength | 来源表列 |
|---|---|---|
| Store.brandName | 160 | brand.name |
| Task.storeName | 160 | store.name |
| User.teamName | 100 | org_unit.name |

### 枚举/状态字段（应做「枚举值」校验，非仅长度）

| 字段 | 允许值来源 |
|---|---|
| Facility.status | 16 字符内的枚举 |
| PublicationJob.status | publication_job.status 枚举 |
| UploadSession.status | upload_session.status 枚举 |
| Attachment.status | attachment.status 枚举 |
| SyncException.status | sync_exception.status 枚举 |

> 这些字段本质是枚举，契约测试除长度外，应加**枚举白名单断言**（如 `status ∈ {ACTIVE, ARCHIVED}` 见 store 表 CHECK 约束）。

---

## 三、待确认项（未定权威值，测试前需拍板）

### 自由文本字段（需产品确认实际最长输入）

| 字段 | 当前值 | 待确认问题 |
|---|---|---|
| store.address | 500 | 地址最长多少字？超 500 需扩库 |
| facility.position | 300 | 位置描述最长多少字？ |
| risk.position | 300 | 位置描述最长多少字？ |
| task_change.reason | 1000 | 派工变更原因最长多少字？ |

### 解密字段（需后端给明文 maxLength）

| 字段 | 存储形式 | 待确认 |
|---|---|---|
| Store.contactName | contact_cipher VARBINARY 解密 | 姓名明文 maxLength |
| Store.contactPhone | contact_cipher VARBINARY 解密 | 电话明文 maxLength |
| Risk.contactName | risk.contact_cipher VARBINARY 解密 | 姓名明文 maxLength |

---

## 四、契约测试用例建议（最小集）

后端契约测试至少覆盖以下断言：

1. **长度边界**：对每个「权威 maxLength」字段，造一条 `长度 == maxLength` 的请求 → 通过；`长度 == maxLength + 1` → 返回 400（不是截断后 200）。
2. **枚举白名单**：status/type 类字段，非法枚举值 → 400。
3. **id 格式**：UUID v4 字符串 36 字符 ↔ BINARY(16) 双向转换正确（hex↔binary）。
4. **自由文本字段**：待产品确认后补边界用例。
5. **JSON 列字段**：content_json 内的长文本（如 Finding.description）不因长度被拒，只在 JSON 校验层约束。

---

## 五、回归验证

改完 openapi.yaml 后，跑：

```bash
python3 .workbuddy/build/check_contract.py
```

期望输出「无真实截断风险」。该脚本比对 openapi maxLength × DDL 列宽，是契约一致性的机器防线。
