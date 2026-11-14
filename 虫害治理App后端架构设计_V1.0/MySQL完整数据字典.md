# MySQL 数据字典 V1.0

与 V001__initial_schema.sql 同源生成。目标 MySQL 8.4，全部业务 UUID 为 BINARY(16)。普通用户无永久删除入口，外键全部 RESTRICT。未列出外键的字典UUID、JSON引用、离线尚未入库ID，由应用按范围和版本校验。

共 44 张表；JSON承载完整业务结构，规范化JCS字节独立存BLOB。字段和API命名通过Mapper转换，不直接暴露数据库记录。

## 1 org_unit 区域与团队

所属模块：identity。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| parent_id | BINARY(16) NULL | 父组织，根节点为空 |
| code | VARCHAR(32) NOT NULL | 组织编号 |
| name | VARCHAR(100) NOT NULL | 区域或团队名称 |
| kind | VARCHAR(16) NOT NULL | REGION或TEAM |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | 启用状态 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_org_code (code)
- CHECK kind IN ('REGION','TEAM')
- CHECK status IN ('ACTIVE','DISABLED')
- 外键 (parent_id) → org_unit(id)

## 2 app_user 员工账号

所属模块：identity。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| org_id | BINARY(16) NOT NULL | 所属组织 |
| login_name | VARCHAR(64) NOT NULL | 规范化登录名 |
| password_hash | VARCHAR(255) NOT NULL | 密码强哈希，禁止明文 |
| name | VARCHAR(80) NOT NULL | 姓名 |
| phone_cipher | VARBINARY(512) NULL | 应用层加密手机号，密钥在数据库外 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | ACTIVE或DISABLED |
| permission_version | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 授权变化递增，令牌和离线授权校验使用 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_user_login (login_name)
- KEY ix_user_org (org_id,status)
- CHECK status IN ('ACTIVE','DISABLED')
- 外键 (org_id) → org_unit(id)

## 3 app_role 角色

所属模块：identity。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| code | VARCHAR(32) NOT NULL | 角色编码 |
| name | VARCHAR(80) NOT NULL | 角色名称 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | 角色状态 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_role_code (code)

## 4 user_role 员工角色关联

所属模块：identity。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| user_id | BINARY(16) NOT NULL | 员工 |
| role_id | BINARY(16) NOT NULL | 角色 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_user_role (user_id,role_id)
- 外键 (user_id) → app_user(id)
- 外键 (role_id) → app_role(id)

## 5 role_permission 角色动作权限

所属模块：identity。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| role_id | BINARY(16) NOT NULL | 角色 |
| permission_code | VARCHAR(100) NOT NULL | 后端白名单动作编码，不接受客户端自定义 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_role_permission (role_id,permission_code)
- 外键 (role_id) → app_role(id)

## 6 user_scope 员工数据范围

所属模块：identity。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| user_id | BINARY(16) NOT NULL | 员工 |
| scope_kind | VARCHAR(16) NOT NULL | ALL、ORG或STORE |
| org_id | BINARY(16) NULL | 组织范围 |
| store_id | BINARY(16) NULL | 指定门店范围 |
| scope_key | BINARY(16) GENERATED ALWAYS AS (COALESCE(org_id,store_id,UNHEX('00000000000000000000000000000000'))) STORED | 范围去重计算值 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_user_scope (user_id,scope_kind,scope_key)
- CHECK (scope_kind='ALL' AND org_id IS NULL AND store_id IS NULL) OR (scope_kind='ORG' AND org_id IS NOT NULL AND store_id IS NULL) OR (scope_kind='STORE' AND org_id IS NULL AND store_id IS NOT NULL)
- 外键 (user_id) → app_user(id)
- 外键 (org_id) → org_unit(id)
- 外键 (store_id) → store(id)

## 7 auth_session 设备会话

所属模块：identity。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| user_id | BINARY(16) NOT NULL | 员工 |
| device_id | VARCHAR(128) NOT NULL | 客户端设备标识，非可信硬件证明 |
| refresh_hash | BINARY(32) NOT NULL | 刷新令牌SHA256，只保存摘要 |
| permission_version | BIGINT UNSIGNED NOT NULL | 签发时权限版本 |
| expires_at | DATETIME(6) NOT NULL | 刷新有效期 |
| revoked_at | DATETIME(6) NULL | 注销时间 |
| last_seen_at | DATETIME(6) NOT NULL | 最后活动时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_refresh_hash (refresh_hash)
- KEY ix_session_user (user_id,revoked_at)
- 外键 (user_id) → app_user(id)

## 8 notification 站内通知

所属模块：identity。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| user_id | BINARY(16) NOT NULL | 接收员工 |
| kind | VARCHAR(32) NOT NULL | 任务或同步等通知类型 |
| payload_json | JSON NOT NULL | 通知内容及受保护业务资源ID |
| read_at | DATETIME(6) NULL | 已读时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_notification_user (user_id,created_at,id)
- 外键 (user_id) → app_user(id)

## 9 customer 客户

所属模块：masterdata。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| code | VARCHAR(40) NOT NULL | 客户编号 |
| name | VARCHAR(160) NOT NULL | 客户名称 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | 启用或归档 |
| revision | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 乐观版本 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_customer_code (code)
- CHECK status IN ('ACTIVE','ARCHIVED')

## 10 brand 客户品牌

所属模块：masterdata。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| customer_id | BINARY(16) NOT NULL | 所属客户 |
| name | VARCHAR(160) NOT NULL | 品牌名称 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | 启用或归档 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_brand_customer (customer_id,name)
- 外键 (customer_id) → customer(id)

## 11 store 门店

所属模块：masterdata。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| customer_id | BINARY(16) NOT NULL | 所属客户 |
| brand_id | BINARY(16) NULL | 所属品牌，必须同客户 |
| org_id | BINARY(16) NOT NULL | 所属区域或团队 |
| owner_user_id | BINARY(16) NULL | 服务负责人 |
| code | VARCHAR(40) NOT NULL | 门店唯一编号 |
| name | VARCHAR(160) NOT NULL | 门店名称 |
| address | VARCHAR(500) NOT NULL | 完整地址 |
| timezone | VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' | IANA时区，用于计划日期 |
| latitude | DECIMAL(10,7) NULL | 纬度 |
| longitude | DECIMAL(10,7) NULL | 经度 |
| contact_cipher | VARBINARY(2048) NULL | 加密联系人资料 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | 启用或归档 |
| revision | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 乐观版本 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_store_code (code)
- KEY ix_store_scope (org_id,status,id)
- KEY ix_store_customer (customer_id,id)
- CHECK latitude IS NULL OR latitude BETWEEN -90 AND 90
- CHECK longitude IS NULL OR longitude BETWEEN -180 AND 180
- CHECK status IN ('ACTIVE','ARCHIVED')
- 外键 (customer_id) → customer(id)
- 外键 (brand_id) → brand(id)
- 外键 (org_id) → org_unit(id)
- 外键 (owner_user_id) → app_user(id)

## 12 store_area 门店分区

所属模块：masterdata。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| store_id | BINARY(16) NOT NULL | 所属门店 |
| code | VARCHAR(40) NOT NULL | 门店内分区编号 |
| name | VARCHAR(100) NOT NULL | 区域名称 |
| floor | VARCHAR(40) NULL | 楼层 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | 启用或归档 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_area_code (store_id,code)
- UNIQUE KEY uk_area_store (id,store_id)
- 外键 (store_id) → store(id)

## 13 config_release 版本化配置

所属模块：masterdata。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| kind | VARCHAR(24) NOT NULL | DICTIONARY、NOTICE、TEMPLATE、BRAND、APP |
| version_no | INT UNSIGNED NOT NULL | 同类递增版本 |
| payload_json | JSON NOT NULL | 字典项目稳定UUID及启停标记、须知正文、品牌或模板参数 |
| payload_hash | BINARY(32) NOT NULL | 规范化配置摘要 |
| published_by | BINARY(16) NOT NULL | 发布人员 |
| published_at | DATETIME(6) NOT NULL | 生效时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_config_version (kind,version_no)
- CHECK kind IN ('DICTIONARY','NOTICE','TEMPLATE','BRAND','APP')
- 外键 (published_by) → app_user(id)

## 14 offline_asset 离线模板与资料文件

所属模块：media。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| release_id | BINARY(16) NOT NULL | 配置版本 |
| object_key | VARCHAR(500) NOT NULL | 私有对象键 |
| sha256 | BINARY(32) NOT NULL | 原始文件摘要 |
| byte_size | BIGINT UNSIGNED NOT NULL | 文件大小 |
| mime_type | VARCHAR(100) NOT NULL | 真实内容类型 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- CHECK byte_size > 0
- 外键 (release_id) → config_release(id)

## 15 service_plan 服务计划

所属模块：dispatch。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| store_id | BINARY(16) NOT NULL | 门店 |
| assignee_id | BINARY(16) NOT NULL | 默认执行人员 |
| service_type | VARCHAR(24) NOT NULL | 常规、应急、复查 |
| recurrence_json | JSON NOT NULL | 单次或每周每月规则；不接受任意脚本 |
| start_date | DATE NOT NULL | 门店本地开始日期 |
| end_date | DATE NULL | 结束日期 |
| next_run_at | DATETIME(6) NULL | 下次展开时间UTC |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | ACTIVE、PAUSED、ARCHIVED |
| revision | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 计划版本 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_plan_due (status,next_run_at,id)
- CHECK service_type IN ('ROUTINE','EMERGENCY','RECHECK')
- CHECK end_date IS NULL OR end_date >= start_date
- 外键 (store_id) → store(id)
- 外键 (assignee_id) → app_user(id)

## 16 service_task 服务任务

所属模块：dispatch。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| store_id | BINARY(16) NOT NULL | 门店 |
| plan_id | BINARY(16) NULL | 来源计划，应急可空 |
| occurrence_key | VARCHAR(80) NULL | 稳定发生槽位（本地日期加序号），不含计划版本，防止改计划后重复生成 |
| assignee_id | BINARY(16) NOT NULL | 唯一主执行人 |
| service_type | VARCHAR(24) NOT NULL | 服务类型 |
| planned_date | DATE NOT NULL | 门店本地计划日期 |
| planned_start_at | DATETIME(6) NULL | 可选计划开始时间UTC |
| planned_end_at | DATETIME(6) NULL | 可选计划结束时间UTC |
| status | VARCHAR(24) NOT NULL DEFAULT 'PENDING' | 业务状态，不含上传状态 |
| assignment_version | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 每次改派取消递增 |
| revision | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 任务ETag版本 |
| creation_source | VARCHAR(24) NOT NULL | PLAN、DISPATCH、EMPLOYEE_EMERGENCY |
| instructions | TEXT NULL | 任务说明 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_plan_occurrence (plan_id,occurrence_key)
- UNIQUE KEY uk_task_store (id,store_id)
- KEY ix_task_assignee_date (assignee_id,planned_date,status,id)
- KEY ix_task_store_date (store_id,planned_date,id)
- CHECK status IN ('PENDING','IN_PROGRESS','AWAITING_CONFIRMATION','COMPLETED','CANCELLED')
- CHECK service_type IN ('ROUTINE','EMERGENCY','RECHECK')
- CHECK (plan_id IS NULL AND occurrence_key IS NULL) OR (plan_id IS NOT NULL AND occurrence_key IS NOT NULL)
- 外键 (store_id) → store(id)
- 外键 (plan_id) → service_plan(id)
- 外键 (assignee_id) → app_user(id)

## 17 task_change 任务变更日志

所属模块：dispatch。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| task_id | BINARY(16) NOT NULL | 任务 |
| actor_id | BINARY(16) NOT NULL | 操作人 |
| reason | VARCHAR(1000) NOT NULL | 改期改派取消原因 |
| before_json | JSON NOT NULL | 变更前 |
| after_json | JSON NOT NULL | 变更后 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_change_task (task_id,created_at,id)
- 外键 (task_id) → service_task(id)
- 外键 (actor_id) → app_user(id)

## 18 facility 设施台账

所属模块：ledger。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| store_id | BINARY(16) NOT NULL | 所属门店 |
| area_id | BINARY(16) NULL | 所属区域 |
| code | VARCHAR(40) NOT NULL | 门店内编号，移除后不复用 |
| facility_type_id | BINARY(16) NOT NULL | 字典稳定项目UUID |
| position | VARCHAR(300) NOT NULL | 详细位置 |
| floor | VARCHAR(40) NULL | 楼层 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | 在用、停用、移除 |
| revision | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 台账版本 |
| last_event_id | BINARY(16) NULL | 最后已应用事件，事务内赋值 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_facility_code (store_id,code)
- UNIQUE KEY uk_facility_store (id,store_id)
- KEY ix_facility_store (store_id,status,id)
- CHECK status IN ('ACTIVE','INACTIVE','REMOVED')
- 外键 (store_id) → store(id)
- 外键 (area_id,store_id) → store_area(id,store_id)

## 19 risk 长期风险台账

所属模块：ledger。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| store_id | BINARY(16) NOT NULL | 所属门店 |
| area_id | BINARY(16) NULL | 所在区域 |
| risk_type_id | BINARY(16) NOT NULL | 风险分类字典UUID |
| pest_type_ids_json | JSON NOT NULL | 关联的多个虫种UUID，按接口保留全部 |
| position | VARCHAR(300) NOT NULL | 位置 |
| status | VARCHAR(24) NOT NULL DEFAULT 'PENDING' | 整改状态 |
| revision | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 乐观版本 |
| responsible_party | VARCHAR(100) NOT NULL | 客户或公司责任方 |
| contact_cipher | VARBINARY(2048) NULL | 责任联系人加密资料 |
| followup_user_id | BINARY(16) NOT NULL | 公司跟进人 |
| due_date | DATE NULL | 门店本地计划完成日期 |
| found_at | DATETIME(6) NOT NULL | 首次发现时间 |
| closed_at | DATETIME(6) NULL | 本次关闭时间，重开后置空，历史保留 |
| content_json | JSON NOT NULL | 描述、建议、照片引用等完整当前状态 |
| last_event_id | BINARY(16) NULL | 最后已应用事件 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_risk_store (id,store_id)
- KEY ix_risk_store_status (store_id,status,due_date,id)
- KEY ix_risk_owner (followup_user_id,status,due_date,id)
- CHECK status IN ('PENDING','IN_PROGRESS','AWAITING_REVIEW','CLOSED')
- CHECK (status='CLOSED' AND closed_at IS NOT NULL) OR (status<>'CLOSED' AND closed_at IS NULL)
- 外键 (store_id) → store(id)
- 外键 (area_id,store_id) → store_area(id,store_id)
- 外键 (followup_user_id) → app_user(id)

## 20 store_assessment 门店静态评估历史

所属模块：ledger。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| store_id | BINARY(16) NOT NULL | 门店 |
| revision | BIGINT UNSIGNED NOT NULL | 门店评估版本 |
| service_id | BINARY(16) NULL | 来源服务，后台代录可空 |
| actor_id | BINARY(16) NOT NULL | 评估人 |
| content_json | JSON NOT NULL | 各项状态、照片和备注 |
| occurred_at | DATETIME(6) NOT NULL | 现场评估时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_assessment_revision (store_id,revision)
- 外键 (store_id) → store(id)
- 外键 (service_id) → service(id)
- 外键 (actor_id) → app_user(id)

## 21 offline_context 不可变离线上下文

所属模块：offline。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| store_id | BINARY(16) NOT NULL | 授权门店 |
| task_id | BINARY(16) NULL | 常规任务上下文，门店应急上下文为空 |
| user_id | BINARY(16) NOT NULL | 原下载员工 |
| device_id | VARCHAR(128) NOT NULL | 下载设备 |
| assignment_version | BIGINT UNSIGNED NULL | 下载时派工版本 |
| permission_version | BIGINT UNSIGNED NOT NULL | 下载时权限版本 |
| context_digest | BINARY(32) NOT NULL | contextBody的JCS SHA256 |
| canonical_body | LONGBLOB NOT NULL | JCS UTF8原始规范化字节，不从MySQL JSON回算 |
| context_json | JSON NOT NULL | 可查询副本，非摘要依据 |
| expires_at | DATETIME(6) NOT NULL | 离线授权普通有效期 |
| evidence_until | DATETIME(6) NOT NULL | 仅隔离证据接收截止时间 |
| downloaded_at | DATETIME(6) NULL | 客户端校验全部资产后的回执时间 |
| revoked_at | DATETIME(6) NULL | 普通读写授权撤销时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_context_user (user_id,created_at,id)
- CHECK evidence_until >= expires_at
- 外键 (store_id) → store(id)
- 外键 (task_id) → service_task(id)
- 外键 (user_id) → app_user(id)

## 22 service 实际服务及报告版本头

所属模块：workorder。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| task_id | BINARY(16) NOT NULL | 一任务对应一次实际服务；更正不新建服务 |
| store_id | BINARY(16) NOT NULL | 门店快筛字段 |
| actor_id | BINARY(16) NOT NULL | 现场主执行员工 |
| context_id | BINARY(16) NOT NULL | 初始上下文 |
| phase | VARCHAR(24) NOT NULL DEFAULT 'EDITABLE' | 当前作业阶段，与任务业务状态独立 |
| head_revision | BIGINT UNSIGNED NOT NULL DEFAULT 1 | 当前内容代次，所有签字发布操作共同校验 |
| active_snapshot_id | BINARY(16) NULL | 当前待签或已发布快照 |
| current_report_id | BINARY(16) NULL | 当前正式报告指针，更正准备时仍指旧版 |
| service_started_at | DATETIME(6) NULL | 真实签到时间，不是上传时间 |
| service_ended_at | DATETIME(6) NULL | 真实现场确认时间，不随更正修改 |
| completed_at | DATETIME(6) NULL | 首次正式发布成功的服务器时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_service_task (task_id)
- UNIQUE KEY uk_service_store (id,store_id)
- KEY ix_service_actor (actor_id,created_at,id)
- CHECK phase IN ('EDITABLE','FROZEN','AWAITING_CONFIRMATION','PUBLISHING','PUBLISHED')
- 外键 (task_id,store_id) → service_task(id,store_id)
- 外键 (actor_id) → app_user(id)
- 外键 (context_id) → offline_context(id)
- 外键 (active_snapshot_id,id) → service_snapshot(id,service_id)
- 外键 (current_report_id,id) → service_report(id,service_id)

## 23 service_draft 当前完整作业草稿

所属模块：workorder。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| service_id | BINARY(16) NOT NULL | 服务 |
| context_id | BINARY(16) NOT NULL | 草稿使用的固定上下文 |
| revision | BIGINT UNSIGNED NOT NULL | If-Match对应版本 |
| field_commit_revision | BIGINT UNSIGNED NOT NULL DEFAULT 0 | 最近成功台账提交的草稿版本 |
| schema_version | VARCHAR(20) NOT NULL | ServiceContent格式版本 |
| content_json | JSON NOT NULL | 完整ServiceContent；所有巡检、处置、用药、总结、签到等 |
| correction_of_report_id | BINARY(16) NULL | 更正源报告 |
| saved_by | BINARY(16) NOT NULL | 最近保存员工 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_draft_service (service_id)
- CHECK field_commit_revision <= revision
- 外键 (service_id) → service(id)
- 外键 (context_id) → offline_context(id)
- 外键 (correction_of_report_id) → service_report(id)
- 外键 (saved_by) → app_user(id)

## 24 ledger_event 台账事件与业务幂等凭证

所属模块：ledger。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| service_id | BINARY(16) NULL | 服务内事件；独立跟进可空 |
| store_id | BINARY(16) NOT NULL | 所属门店 |
| entity_id | BINARY(16) NOT NULL | 设施、风险或评估业务ID |
| kind | VARCHAR(32) NOT NULL | 设施变更、新风险、跟进、评估 |
| expected_revision | BIGINT UNSIGNED NOT NULL | 应用前期望版本，新建为0 |
| result_revision | BIGINT UNSIGNED NOT NULL | 应用后版本 |
| payload_hash | BINARY(32) NOT NULL | 不可变事件内容摘要 |
| payload_json | JSON NOT NULL | 事件内容含变更前后、来源及照片引用 |
| actor_id | BINARY(16) NOT NULL | 执行人 |
| occurred_at | DATETIME(6) NOT NULL | 原始现场事件时间 |
| supersedes_event_id | BINARY(16) NULL | 显式更正的旧事件ID |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_event_entity (entity_id,created_at,id)
- KEY ix_event_service (service_id,id)
- CHECK result_revision > expected_revision
- 外键 (service_id) → service(id)
- 外键 (store_id) → store(id)
- 外键 (actor_id) → app_user(id)
- 外键 (supersedes_event_id) → ledger_event(id)

## 25 risk_followup 风险跟进历史

所属模块：ledger。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| risk_id | BINARY(16) NOT NULL | 长期风险 |
| ledger_event_id | BINARY(16) NOT NULL | 唯一应用事件 |
| service_id | BINARY(16) NULL | 服务内跟进或独立跟进 |
| from_status | VARCHAR(24) NOT NULL | 变更前状态 |
| to_status | VARCHAR(24) NOT NULL | 变更后状态 |
| result | VARCHAR(32) NOT NULL | 复核结果或未核查 |
| content_json | JSON NOT NULL | 说明、未核查原因、材料来源、改善照片 |
| occurred_at | DATETIME(6) NOT NULL | 实际跟进时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_followup_event (ledger_event_id)
- KEY ix_followup_risk (risk_id,occurred_at,id)
- 外键 (risk_id) → risk(id)
- 外键 (ledger_event_id) → ledger_event(id)
- 外键 (service_id) → service(id)

## 26 field_commit 现场台账提交回执

所属模块：ledger。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| service_id | BINARY(16) NOT NULL | 服务 |
| draft_revision | BIGINT UNSIGNED NOT NULL | 提交草稿版本 |
| request_hash | BINARY(32) NOT NULL | 提交内容摘要 |
| receipt_json | JSON NOT NULL | 每个事件ID及应用结果版本 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_commit_revision (service_id,draft_revision)
- 外键 (service_id) → service(id)

## 27 attachment 受保护原始附件

所属模块：media。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| store_id | BINARY(16) NOT NULL | 门店边界 |
| owner_id | BINARY(16) NOT NULL | 原上传人 |
| context_id | BINARY(16) NOT NULL | 授权来源 |
| purpose | VARCHAR(32) NOT NULL | PHOTO、SIGNATURE、SERVICE_FILE等 |
| scope | VARCHAR(16) NOT NULL DEFAULT 'NORMAL' | NORMAL或QUARANTINE |
| status | VARCHAR(16) NOT NULL DEFAULT 'PENDING' | PENDING、VERIFYING、READY、FAILED |
| object_key | VARCHAR(500) NOT NULL | 不可变私有对象键 |
| sha256 | BINARY(32) NOT NULL | 预期原始文件SHA256 |
| byte_size | BIGINT UNSIGNED NOT NULL | 预期字节数 |
| mime_type | VARCHAR(100) NOT NULL | 内容识别后的MIME |
| verified_at | DATETIME(6) NULL | 摘要、大小、文件类型等校验通过时间 |
| failure_code | VARCHAR(80) NULL | 失败原因 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_attachment_owner (owner_id,status,created_at,id)
- CHECK byte_size > 0
- CHECK status IN ('PENDING','VERIFYING','READY','FAILED')
- CHECK scope IN ('NORMAL','QUARANTINE')
- CHECK (status='READY' AND verified_at IS NOT NULL) OR status<>'READY'
- 外键 (store_id) → store(id)
- 外键 (owner_id) → app_user(id)
- 外键 (context_id) → offline_context(id)

## 28 upload_session 附件整文件上传会话

所属模块：media。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| attachment_id | BINARY(16) NOT NULL | 目标附件 |
| user_id | BINARY(16) NOT NULL | 上传人 |
| temporary_key | VARCHAR(500) NOT NULL | 暂存对象键 |
| status | VARCHAR(16) NOT NULL DEFAULT 'OPEN' | OPEN、RECEIVED、VERIFIED、EXPIRED |
| expires_at | DATETIME(6) NOT NULL | 本次上传会话有效期 |
| received_size | BIGINT UNSIGNED NOT NULL DEFAULT 0 | 接收大小 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_upload_attachment (attachment_id,created_at,id)
- 外键 (attachment_id) → attachment(id)
- 外键 (user_id) → app_user(id)

## 29 service_snapshot 不可变已冻结正文

所属模块：confirmation。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| service_id | BINARY(16) NOT NULL | 实际服务 |
| context_id | BINARY(16) NOT NULL | 固定上下文 |
| content_version | INT UNSIGNED NOT NULL | 服务内内容版本 |
| head_revision | BIGINT UNSIGNED NOT NULL | 冻结时服务代次 |
| draft_revision | BIGINT UNSIGNED NOT NULL | 冻结时草稿版本 |
| content_hash | BINARY(32) NOT NULL | SnapshotPayload JCS SHA256 |
| canonical_payload | LONGBLOB NOT NULL | JCS UTF8原字节，是签字和渲染正文依据 |
| payload_json | JSON NOT NULL | 校验后的查询副本，禁止回算签字摘要 |
| template_release_id | BINARY(16) NOT NULL | 固定报告模板版本 |
| renderer_version | VARCHAR(100) NOT NULL | 渲染镜像及字体版本标识 |
| invalidated_at | DATETIME(6) NULL | 签后重开时作废标记，正文不可修改 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_snapshot_version (service_id,content_version)
- UNIQUE KEY uk_snapshot_service (id,service_id)
- 外键 (service_id) → service(id)
- 外键 (context_id) → offline_context(id)
- 外键 (template_release_id) → config_release(id)

## 30 snapshot_attachment 快照附件清单

所属模块：confirmation。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| snapshot_id | BINARY(16) NOT NULL | 快照 |
| attachment_id | BINARY(16) NOT NULL | 受保护附件 |
| sha256 | BINARY(32) NOT NULL | 快照期望附件原始摘要 |
| purpose | VARCHAR(32) NOT NULL | 在报告中的用途 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_snapshot_attachment (snapshot_id,attachment_id)
- 外键 (snapshot_id) → service_snapshot(id)
- 外键 (attachment_id) → attachment(id)

## 31 preview_session 客户阅读回执

所属模块：confirmation。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| snapshot_id | BINARY(16) NOT NULL | 预览的固定正文 |
| user_id | BINARY(16) NOT NULL | 承载确认的员工 |
| content_hash | BINARY(32) NOT NULL | 预览正文摘要 |
| started_at | DATETIME(6) NOT NULL | 开始显示时间 |
| completed_at | DATETIME(6) NOT NULL | 主动确认时间 |
| visible_duration_ms | INT UNSIGNED NOT NULL | 前台实际可见时长，至少5000 |
| is_offline | BOOLEAN NOT NULL | 离线回执不能视为可信服务器计时 |
| device_id | VARCHAR(128) NOT NULL | 设备 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- CHECK visible_duration_ms >= 5000
- CHECK completed_at >= started_at
- 外键 (snapshot_id) → service_snapshot(id)
- 外键 (user_id) → app_user(id)

## 32 service_signature 签署凭证历史

所属模块：confirmation。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| snapshot_id | BINARY(16) NOT NULL | 固定正文快照 |
| role | VARCHAR(16) NOT NULL | STAFF或CLIENT |
| signer_name | VARCHAR(80) NOT NULL | 签署人姓名 |
| staff_user_id | BINARY(16) NULL | 员工签署人 |
| staff_signature_id | BINARY(16) NULL | 客户签署绑定的员工签字 |
| preview_session_id | BINARY(16) NULL | 客户阅读回执 |
| attachment_id | BINARY(16) NOT NULL | 签字图片 |
| content_hash | BINARY(32) NOT NULL | 签署正文摘要 |
| attestation_hash | BINARY(32) NOT NULL | 签署凭证JCS摘要 |
| canonical_attestation | LONGBLOB NOT NULL | 签署凭证规范字节，不含attestationHash自身 |
| signed_at | DATETIME(6) NOT NULL | 设备签署时间 |
| rating | TINYINT UNSIGNED NULL | 客户可选1至5星 |
| comment | TEXT NULL | 客户评价，不改正文 |
| supersedes_id | BINARY(16) NULL | 被替换签字 |
| replacement_reason | VARCHAR(1000) NULL | 替换原因 |
| invalidated_at | DATETIME(6) NULL | 作废时间 |
| active_role | VARCHAR(16) GENERATED ALWAYS AS (CASE WHEN invalidated_at IS NULL THEN role ELSE NULL END) STORED | 有效签字唯一约束 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_signature_active (snapshot_id,active_role)
- CHECK role IN ('STAFF','CLIENT')
- CHECK rating IS NULL OR rating BETWEEN 1 AND 5
- CHECK (role='STAFF' AND staff_user_id IS NOT NULL AND staff_signature_id IS NULL AND preview_session_id IS NULL AND rating IS NULL) OR (role='CLIENT' AND staff_user_id IS NULL AND staff_signature_id IS NOT NULL AND preview_session_id IS NOT NULL)
- CHECK (supersedes_id IS NULL AND replacement_reason IS NULL) OR (supersedes_id IS NOT NULL AND replacement_reason IS NOT NULL)
- 外键 (snapshot_id) → service_snapshot(id)
- 外键 (staff_user_id) → app_user(id)
- 外键 (staff_signature_id) → service_signature(id)
- 外键 (preview_session_id) → preview_session(id)
- 外键 (attachment_id) → attachment(id)
- 外键 (supersedes_id) → service_signature(id)

## 33 confirmation_exception 客户缺席或拒签记录

所属模块：confirmation。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| service_id | BINARY(16) NOT NULL | 服务 |
| snapshot_id | BINARY(16) NULL | 对应预览正文 |
| reason_code | VARCHAR(24) NOT NULL | ABSENT、REFUSED等 |
| reason | VARCHAR(1000) NOT NULL | 具体原因 |
| actor_id | BINARY(16) NOT NULL | 登记员工 |
| occurred_at | DATETIME(6) NOT NULL | 现场发生时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_confirmation_service (service_id,created_at,id)
- 外键 (service_id) → service(id)
- 外键 (snapshot_id) → service_snapshot(id)
- 外键 (actor_id) → app_user(id)

## 34 offline_submission 持久化离线提交包

所属模块：offline。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| user_id | BINARY(16) NOT NULL | 原提交员工 |
| context_id | BINARY(16) NOT NULL | 原授权上下文 |
| service_id | BINARY(16) NOT NULL | 包内服务UUID，接收时可能尚未创建，不设FK |
| task_id | BINARY(16) NOT NULL | 包内任务UUID，应急任务可能尚未创建，不设FK |
| assignment_version | BIGINT UNSIGNED NOT NULL | 客户端已知派工版本 |
| request_hash | BINARY(32) NOT NULL | 包的规范摘要 |
| canonical_payload | LONGBLOB NOT NULL | 原始完整包规范字节，先持久化再异步校验 |
| status | VARCHAR(24) NOT NULL DEFAULT 'RECEIVED' | 接收、应用、冲突或拒绝 |
| result_json | JSON NULL | 应用回执或错误摘要 |
| received_at | DATETIME(6) NOT NULL | 服务器接收时间 |
| applied_at | DATETIME(6) NULL | 成功应用时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_submission_user (user_id,received_at,id)
- KEY ix_submission_queue (status,received_at,id)
- CHECK status IN ('RECEIVED','PROCESSING','APPLIED','CONFLICT','REJECTED')
- 外键 (user_id) → app_user(id)
- 外键 (context_id) → offline_context(id)

## 35 sync_exception 隔离同步异常

所属模块：offline。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| submission_id | BINARY(16) NOT NULL | 保留的原始提交包 |
| kind | VARCHAR(32) NOT NULL | 改派、取消、版本冲突等 |
| status | VARCHAR(24) NOT NULL DEFAULT 'OPEN' | OPEN、RESOLVED；拒绝采用记录在resolution_json |
| resolution_json | JSON NULL | 处理方案、保留证据、目标服务与逐项选择 |
| resolved_by | BINARY(16) NULL | 有范围权限的处理人 |
| resolved_at | DATETIME(6) NULL | 处理时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_exception_submission (submission_id)
- KEY ix_exception_queue (status,created_at,id)
- 外键 (submission_id) → offline_submission(id)
- 外键 (resolved_by) → app_user(id)

## 36 publication_job 报告生成业务任务

所属模块：reporting。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| service_id | BINARY(16) NOT NULL | 服务 |
| snapshot_id | BINARY(16) NOT NULL | 固定正文 |
| head_revision | BIGINT UNSIGNED NOT NULL | 预期服务代次 |
| staff_signature_id | BINARY(16) NOT NULL | 固定员工签署凭证 |
| client_signature_id | BINARY(16) NOT NULL | 固定客户签署凭证 |
| status | VARCHAR(16) NOT NULL DEFAULT 'QUEUED' | QUEUED、RUNNING、SUCCEEDED、FAILED、BLOCKED |
| attempt_no | INT UNSIGNED NOT NULL DEFAULT 0 | 尝试次数 |
| error_code | VARCHAR(80) NULL | 失败编码 |
| error_message | VARCHAR(1000) NULL | 安全的可展示失败原因 |
| report_id | BINARY(16) NULL | 成功生成报告 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_publication_snapshot (service_id,snapshot_id)
- KEY ix_publication_status (status,created_at,id)
- CHECK status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','BLOCKED')
- 外键 (snapshot_id,service_id) → service_snapshot(id,service_id)
- 外键 (staff_signature_id) → service_signature(id)
- 外键 (client_signature_id) → service_signature(id)
- 外键 (report_id) → service_report(id)

## 37 service_report 正式报告版本

所属模块：reporting。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| service_id | BINARY(16) NOT NULL | 同一次实际服务 |
| snapshot_id | BINARY(16) NOT NULL | 已发布固定正文 |
| version_no | INT UNSIGNED NOT NULL | 正式报告版本 |
| report_number | VARCHAR(80) NOT NULL | 全局唯一对外编号，编号规则由公司配置 |
| status | VARCHAR(16) NOT NULL | PUBLISHED或SUPERSEDED |
| active_service_id | BINARY(16) GENERATED ALWAYS AS (CASE WHEN status='PUBLISHED' THEN service_id ELSE NULL END) STORED | 每个服务最多一个当前正式报告 |
| staff_signature_id | BINARY(16) NOT NULL | 报告实际嵌入员工签署 |
| client_signature_id | BINARY(16) NOT NULL | 报告实际嵌入客户签署 |
| content_hash | BINARY(32) NOT NULL | 对应正文SHA256 |
| pdf_sha256 | BINARY(32) NOT NULL | 最终PDF字节摘要 |
| object_key | VARCHAR(500) NOT NULL | 不可变私有PDF对象键 |
| byte_size | BIGINT UNSIGNED NOT NULL | PDF字节数 |
| supersedes_report_id | BINARY(16) NULL | 替代的旧版报告 |
| published_at | DATETIME(6) NOT NULL | 服务器发布成功时间 |
| service_started_at | DATETIME(6) NOT NULL | 本次服务真实到店时间快照 |
| service_ended_at | DATETIME(6) NOT NULL | 本次服务真实结束时间快照 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_report_version (service_id,version_no)
- UNIQUE KEY uk_report_snapshot (service_id,snapshot_id)
- UNIQUE KEY uk_report_number (report_number)
- UNIQUE KEY uk_report_current (active_service_id)
- UNIQUE KEY uk_report_service (id,service_id)
- KEY ix_report_published (published_at,id)
- CHECK status IN ('PUBLISHED','SUPERSEDED')
- CHECK byte_size > 0
- CHECK service_ended_at >= service_started_at
- 外键 (snapshot_id,service_id) → service_snapshot(id,service_id)
- 外键 (staff_signature_id) → service_signature(id)
- 外键 (client_signature_id) → service_signature(id)
- 外键 (supersedes_report_id) → service_report(id)

## 38 snapshot_finding 虫情与捕获统计事实

所属模块：analytics。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| snapshot_id | BINARY(16) NOT NULL | 固定快照 |
| store_id | BINARY(16) NOT NULL | 门店 |
| source_record_id | BINARY(16) NOT NULL | 原始Finding.id；设施捕获用Inspection.id，与虫种组合唯一 |
| source | VARCHAR(24) NOT NULL | CUSTOMER、ON_SITE、FACILITY |
| pest_type_id | BINARY(16) NOT NULL | 虫种稳定UUID |
| quantity_state | VARCHAR(20) NOT NULL | EXACT、UNKNOWN |
| quantity | INT UNSIGNED NULL | 已知时为0至1000000000的整数 |
| event_group_id | BINARY(16) NULL | 同一虫情事件关联；不得跨来源直接合计 |
| facility_id | BINARY(16) NULL | 设施来源时必填 |
| occurred_at | DATETIME(6) NOT NULL | 发现或检查时间 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_finding_source (snapshot_id,source,source_record_id,pest_type_id)
- KEY ix_finding_chart (store_id,source,pest_type_id,occurred_at,id)
- CHECK quantity IS NULL OR quantity <= 1000000000
- CHECK source IN ('CUSTOMER','ON_SITE','FACILITY')
- CHECK (quantity_state='EXACT' AND quantity IS NOT NULL) OR (quantity_state='UNKNOWN' AND quantity IS NULL)
- CHECK (source='FACILITY' AND facility_id IS NOT NULL) OR (source IN ('CUSTOMER','ON_SITE') AND facility_id IS NULL)
- 外键 (snapshot_id) → service_snapshot(id)
- 外键 (store_id) → store(id)
- 外键 (facility_id,store_id) → facility(id,store_id)

## 39 snapshot_inspection 设施检查覆盖统计事实

所属模块：analytics。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| snapshot_id | BINARY(16) NOT NULL | 固定快照 |
| facility_id | BINARY(16) NOT NULL | 设施 |
| store_id | BINARY(16) NOT NULL | 门店 |
| check_result | VARCHAR(24) NOT NULL | CHECKED、NOT_CHECKED、UNCONFIRMABLE |
| condition_code | VARCHAR(32) NULL | 设施状态，未检查或无法确认时为空 |
| consumable_action | VARCHAR(24) NULL | REPLACED、NOT_REPLACED、NOT_APPLICABLE；不折算布尔值 |
| not_checked_reason | VARCHAR(1000) NULL | 未检查原因 |
| content_json | JSON NOT NULL | 检查记录完整查询副本 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_inspection_snapshot (snapshot_id,facility_id)
- KEY ix_inspection_store (store_id,check_result,id)
- CHECK (check_result='CHECKED' AND condition_code IS NOT NULL AND consumable_action IS NOT NULL AND not_checked_reason IS NULL) OR (check_result IN ('NOT_CHECKED','UNCONFIRMABLE') AND condition_code IS NULL AND consumable_action IS NULL AND CHAR_LENGTH(TRIM(not_checked_reason))>0 AND not_checked_reason IS NOT NULL)
- 外键 (snapshot_id) → service_snapshot(id)
- 外键 (facility_id,store_id) → facility(id,store_id)

## 40 snapshot_treatment 处置与用药查询事实

所属模块：analytics。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| snapshot_id | BINARY(16) NOT NULL | 固定快照 |
| source_record_id | BINARY(16) NOT NULL | 处置UUID |
| kind | VARCHAR(32) NOT NULL | 清洁、封堵、物理、用药、观察等 |
| purpose | VARCHAR(1000) NULL | 预防性处理目的 |
| content_json | JSON NOT NULL | 关联虫情设施风险UUID、实际用药、浓度和用量字符串及单位 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_treatment_snapshot (snapshot_id,source_record_id)
- 外键 (snapshot_id) → service_snapshot(id)

## 41 job_queue 持久化异步任务与事务发件箱

所属模块：platform。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| kind | VARCHAR(40) NOT NULL | SYNC_APPLY、REPORT_RENDER、EXPORT等 |
| business_id | BINARY(16) NOT NULL | 业务对象ID |
| status | VARCHAR(16) NOT NULL DEFAULT 'READY' | READY、RUNNING、DONE、DEAD |
| payload_json | JSON NOT NULL | 最小引用数据，禁止放二进制或重复完整服务正文 |
| attempts | INT UNSIGNED NOT NULL DEFAULT 0 | 尝试次数 |
| max_attempts | INT UNSIGNED NOT NULL DEFAULT 5 | 可配置上限 |
| next_run_at | DATETIME(6) NOT NULL | 可运行时间 |
| lease_owner | VARCHAR(100) NULL | 工作进程标识 |
| lease_until | DATETIME(6) NULL | 租约截止时间 |
| fencing_token | BIGINT UNSIGNED NOT NULL DEFAULT 0 | 每次领取递增，最终提交校验 |
| last_error | VARCHAR(1000) NULL | 安全错误说明 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_job_business (kind,business_id)
- KEY ix_job_ready (status,next_run_at,id)
- KEY ix_job_lease (status,lease_until,id)
- CHECK status IN ('READY','RUNNING','DONE','DEAD')

## 42 idempotency_record HTTP幂等结果

所属模块：platform。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| actor_id | BINARY(16) NOT NULL | 当前员工 |
| operation_id | VARCHAR(100) NOT NULL | OpenAPI operationId |
| key_hash | BINARY(32) NOT NULL | Idempotency-Key摘要 |
| request_hash | BINARY(32) NOT NULL | 规范化请求摘要，含资源路径和版本 |
| status | VARCHAR(16) NOT NULL | IN_PROGRESS、COMPLETED |
| http_status | SMALLINT UNSIGNED NULL | 最终HTTP状态 |
| response_json | JSON NULL | 安全响应或业务回执引用 |
| expires_at | DATETIME(6) NOT NULL | 清理期限；业务唯一ID永久防重复 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- UNIQUE KEY uk_idempotency (actor_id,operation_id,key_hash)
- KEY ix_idempotency_expiry (expires_at,id)
- 外键 (actor_id) → app_user(id)

## 43 audit_log 关键审计记录

所属模块：platform。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| actor_id | BINARY(16) NULL | 人员操作或系统任务 |
| request_id | VARCHAR(80) NOT NULL | 请求跟踪ID |
| action | VARCHAR(100) NOT NULL | 派工、风险关闭、导出、签字更正等动作 |
| resource_type | VARCHAR(64) NOT NULL | 目标类型 |
| resource_id | BINARY(16) NULL | 目标业务ID |
| store_id | BINARY(16) NULL | 所属门店 |
| result | VARCHAR(24) NOT NULL | 成功或拒绝 |
| reason | VARCHAR(1000) NULL | 操作原因 |
| summary_json | JSON NOT NULL | 字段级变化摘要，不记录令牌及完整敏感正文 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- KEY ix_audit_resource (resource_type,resource_id,created_at,id)
- KEY ix_audit_actor (actor_id,created_at,id)
- 外键 (actor_id) → app_user(id)
- 外键 (store_id) → store(id)

## 44 import_export_job 后台批量任务

所属模块：platform。

| 字段 | MySQL 类型与约束 | 说明 |
|---|---|---|
| id | BINARY(16) NOT NULL | UUIDv4；与API id相同；不做时间位交换 |
| kind | VARCHAR(16) NOT NULL | IMPORT或EXPORT |
| actor_id | BINARY(16) NOT NULL | 申请人 |
| scope_json | JSON NOT NULL | 申请时范围及过滤条件；执行和下载仍复核现权限 |
| status | VARCHAR(24) NOT NULL | PREVIEW、QUEUED、RUNNING、SUCCEEDED、FAILED |
| input_object_key | VARCHAR(500) NULL | 导入暂存文件 |
| output_object_key | VARCHAR(500) NULL | 错误预览或导出文件 |
| result_json | JSON NULL | 数量、逐行错误及提交摘要 |
| expires_at | DATETIME(6) NULL | 临时文件保留截止 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 服务器创建时间，UTC |
| updated_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 服务器更新时间，UTC |

索引与约束：

- PRIMARY KEY (id)
- CHECK kind IN ('IMPORT','EXPORT')
- 外键 (actor_id) → app_user(id)

