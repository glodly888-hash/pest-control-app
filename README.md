# 虫害治理 App（Pest Control）

面向连锁门店虫害治理（PCO）的一线作业全栈应用：员工上门巡检、虫情发现、用药处置、风险跟进、离线作业、报告生成与客户签字确认的完整闭环。

- **员工端**：PWA（网页版），支持弱网/离线作业
- **后端**：Java 21 模块化单体，整洁架构，API 与 Worker 双进程
- **契约**：OpenAPI 单一来源，54 项 operationId，44 张表

> 仓库为**公开**仓库。代码中仅含开发环境默认凭据（本地容器密码、dev JWT secret、种子账号），均标注「生产必须用环境变量覆盖」；正式部署前请务必覆盖 `DB_PASSWORD` / `JWT_SECRET` 等环境变量。

---

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 4.1.1、MyBatis 之外纯 JdbcTemplate、Flyway、MySQL 8.4 |
| 前端 | Vue 3、TypeScript、Vite 6、Vue Router、Pinia、Axios、PWA（vite-plugin-pwa + Workbox） |
| 架构 | 整洁架构四层（domain → application → infrastructure → api/worker） |
| 离线 | IndexedDB + OPFS，发件箱模式（offline_submission + job_queue） |

---

## 目录结构

```
虫害治理程序/
├── pest-backend/                后端（模块化单体）
│   ├── pest-domain/             领域对象、规则、存储端口（零 HTTP/JDBC 依赖）
│   ├── pest-application/        用例编排、事务、权限策略
│   ├── pest-infrastructure/     JDBC、对象存储、PDF、队列适配
│   ├── pest-api/                REST DTO/Controller/安全（API 进程，端口 8080）
│   ├── pest-worker/             任务领取/心跳/执行重试（Worker 进程，端口 8081）
│   ├── pest-contract/           OpenAPI、联调示例、JCS 交叉验证向量
│   └── pest-db/                 Flyway 迁移 + 种子 SQL
├── pest-web/                    前端 PWA（Vue3 + Vite + TS）
└── 文档/
    ├── 虫害治理App开发需求文档_V1.0    需求 PRD
    ├── 虫害治理App后端架构设计_V1.0    后端架构设计
    ├── 虫害治理App接口文档_V1.0        OpenAPI 契约 + 字段字典
    ├── 虫害治理App白模UI_V1.0          白模低保真原型
    ├── 虫害治理App_Vue3原型_V1.0       Vue3 高保真原型
    └── PestControl_*                  开发计划 / 测试基线 / 契约修订工单 / 备案清单
```

---

## 快速启动

### 1. 准备 MySQL 8.4

后端默认连接 `localhost:3307/pest`（账号 `root`）。可用任意 MySQL 8.4 实例，或用以下命令启动本地开发容器：

```bash
docker run -d --name pest-mysql84 \
  -e MYSQL_ROOT_PASSWORD=pest_dev_root \
  -e MYSQL_DATABASE=pest \
  -p 3307:3306 \
  mysql:8.4
```

> Flyway 会在后端启动时自动执行 `pest-db` 里的迁移脚本；种子数据（`seed-dev.sql`）需手动导入，见下。

### 2. 启动后端

```bash
cd pest-backend

# 编译（本机 JDK17 可用 -Djava.version=17 覆盖；生产按架构要求用 JDK21）
./mvnw clean package -DskipTests

# 启动 API（端口 8080，context-path /api/v1）
SERVER_PORT=8080 java -jar pest-api/target/pest-api-0.1.0-SNAPSHOT.jar

# 启动 Worker（端口 8081）
java -jar pest-worker/target/pest-worker-0.1.0-SNAPSHOT.jar
```

关键环境变量（均有开发默认值，生产必须覆盖）：

| 变量 | 默认 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3307/pest` | 数据库连接 |
| `DB_USER` / `DB_PASSWORD` | `root` / `pest_dev_root` | 数据库凭据 |
| `JWT_SECRET` | dev 占位值 | 生产必须替换（≥32 字节） |
| `SERVER_PORT` | 8080 | API 端口 |

### 3. 导入种子数据（可选）

```bash
docker exec -i pest-mysql84 mysql -uroot -ppest_dev_root pest \
  < pest-backend/pest-db/src/main/resources/db/seed-dev.sql
```

种子账号：`employee01` / `password`（公司码 `SWSTEST`）。

### 4. 启动前端

```bash
cd pest-web
npm install
npm run dev   # 默认 5173，被占用时自动 +1
```

开发模式下 `vite.config.ts` 会把 `/api` 代理到后端（默认 8080）。浏览器访问 `http://localhost:5173`。

---

## 核心设计要点

- **整洁架构**：`api/worker → application → domain`，`infrastructure` 实现 `domain` 端口；application 层不依赖 Jackson，JSON 序列化下沉到 infrastructure。
- **双进程衔接**：API 与 Worker 通过持久化任务表 `job_queue` 协作，`FOR UPDATE SKIP LOCKED` + fencing token 租约，指数退避重试。
- **离线同步**：发件箱模式——提交事务内写 `offline_submission` + `job_queue`，Worker 异步消费应用；原包永不在回滚事务里，三层幂等。
- **业务硬约束**：服务完成 ≠ 风险关闭；数量四态（`EXACT/UNKNOWN/NOT_CHECKED/UNCONFIRMABLE`）；签字绑定不可变快照，更正生成新版本。

更完整的分层、11 个业务域、锁顺序等细节见 [`pest-backend/README.md`](pest-backend/README.md)。

---

## 文档导航

| 文档 | 内容 |
|---|---|
| [需求文档](虫害治理App开发需求文档_V1.0.md) | 业务需求、验收口径（口径冲突以 PRD 为准） |
| [后端架构设计](虫害治理App后端架构设计_V1.0/) | 分层、数据模型（44 表）、关键业务规则 |
| [接口文档](虫害治理App接口文档_V1.0/虫害治理App接口文档_V1.0.md) | OpenAPI 契约（54 项 operationId）+ 完整字段字典 |
| [契约测试基线](PestControl_契约测试基线_V1.0.md) | TC01–TC24 验收用例 |
| [契约修订工单](PestControl_契约修订工单_V1.0.md) | maxLength 契约差异与截断风险修订项 |
| [开发计划](PestControl_DevPlan_V1.0.pdf) | 18 周排期、G0–G6 里程碑 |

---

## 许可证

暂未指定开源许可证。代码仅供学习与协作参考，如需商用或二次分发请与项目方确认。
