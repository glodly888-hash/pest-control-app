# pest-backend 虫害治理 App 统一后端

模块化单体（Java 21 + Spring Boot + MySQL 8.4），API 与 Worker 双进程，共用领域模块与数据库，通过持久化任务表 `job_queue` 衔接。

## 模块与依赖方向

```
pest-backend/
  pom.xml                  根 POM，统一版本管理
  pest-domain/             领域对象、枚举、规则及存储端口（零 HTTP/JDBC 依赖）
  pest-application/        用例服务、事务编排、权限策略（依赖 domain 接口）
  pest-infrastructure/     JDBC、对象存储、PDF、队列适配（实现 domain 端口）
  pest-api/                REST DTO、Controller、安全过滤器（API 进程入口，端口 8080）
  pest-worker/             任务领取、心跳、执行和重试入口（Worker 进程，端口 8081）
  pest-contract/           既有 OpenAPI、联调例子、JCS 交叉验证向量（契约单一来源）
  pest-db/                 Flyway 迁移与关键 SQL（V001 不改，后续只新增版本）
```

依赖方向：`api/worker → application → domain`，`infrastructure` 实现 `domain` 端口。Controller 不直接访问数据库，领域对象不依赖 HTTP。

## 11 个业务模块（按领域分包）

| 模块 | 职责 | 主要数据 |
|---|---|---|
| identity | 登录、设备会话、角色、权限、数据范围 | app_user / app_role / user_scope / auth_session |
| masterdata | 客户品牌门店、分区、版本化字典须知模板 | customer / brand / store / config_release |
| dispatch | 周期计划、派工、改期改派取消、任务状态 | service_plan / service_task / task_change |
| workorder | 作业草稿、签到、校验、内容代次 | service / service_draft |
| ledger | 设施、长期风险、跟进、静态评估、现场提交 | facility / risk / ledger_event / field_commit |
| confirmation | 固定快照、阅读回执、双签、缺席拒签 | service_snapshot / service_signature |
| offline | 下载上下文、离线接收、原包保留和冲突处理 | offline_context / offline_submission / sync_exception |
| media | 受保护文件上传、摘要校验、读取、离线资料 | attachment / upload_session / offline_asset |
| reporting | PDF 生成、发布、报告版本和更正 | publication_job / service_report |
| analytics | 固定快照事实、当前正式版本统计 | snapshot_finding / snapshot_inspection / snapshot_treatment |
| platform | 幂等、审计、任务执行、导入导出 | idempotency_record / audit_log / job_queue |

## 关键业务规则（架构设计硬约束）

- 服务完成 ≠ 风险关闭；客户反馈/现场发现/设施捕获分开统计，不默认相加。
- 数量四态：`EXACT / UNKNOWN / NOT_CHECKED / UNCONFIRMABLE`，绝不自动补 0。
- 签字绑定固定正文（不可变快照），更正生成新版本，不覆盖旧版。
- 离线同步接收/应用分两个事务，原包永不在回滚事务里，三层幂等。
- 统一锁顺序：task → service → draft → 台账(UUID 排序) → snapshot/signature → publication_job → job_queue。

## 构建与运行

```bash
# 全量编译 + 测试
./mvnw clean verify

# 仅编译（跳过测试）
./mvnw clean package -DskipTests

# 启动 API（先保证 MySQL 8.4 可连，Flyway 自动迁移）
./mvnw -pl pest-api spring-boot:run

# 启动 Worker
./mvnw -pl pest-worker spring-boot:run
```

环境变量：`DB_URL` / `DB_USER` / `DB_PASSWORD`（凭据不写入源码）。

## 当前状态

骨架阶段：目录、POM、启动类、核心枚举、领域边界已就位；具体接口实现待契约冻结（W1）后按 OpenAPI 逐步落地。
