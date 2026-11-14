-- 虫害治理App V1.0 初始物理模型；目标 MySQL 8.4 LTS / InnoDB。
-- 仅对空的独立schema执行；不含DROP、不创建账号、不设默认口令。
-- 业务UUID使用UNHEX(REPLACE(uuid,'-',''))或Java 16字节绑定；不启用swap_flag。
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- identity / 区域与团队
CREATE TABLE org_unit (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  parent_id BINARY(16) NULL COMMENT '父组织，根节点为空',
  code VARCHAR(32) NOT NULL COMMENT '组织编号',
  name VARCHAR(100) NOT NULL COMMENT '区域或团队名称',
  kind VARCHAR(16) NOT NULL COMMENT 'REGION或TEAM',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '启用状态',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_org_code (code),
  CONSTRAINT ck_org_unit_1 CHECK (kind IN ('REGION','TEAM')),
  CONSTRAINT ck_org_unit_2 CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='区域与团队';

-- identity / 员工账号
CREATE TABLE app_user (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  org_id BINARY(16) NOT NULL COMMENT '所属组织',
  login_name VARCHAR(64) NOT NULL COMMENT '规范化登录名',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码强哈希，禁止明文',
  name VARCHAR(80) NOT NULL COMMENT '姓名',
  phone_cipher VARBINARY(512) NULL COMMENT '应用层加密手机号，密钥在数据库外',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE或DISABLED',
  permission_version BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '授权变化递增，令牌和离线授权校验使用',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_login (login_name),
  KEY ix_user_org (org_id,status),
  CONSTRAINT ck_app_user_1 CHECK (status IN ('ACTIVE','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工账号';

-- identity / 角色
CREATE TABLE app_role (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  code VARCHAR(32) NOT NULL COMMENT '角色编码',
  name VARCHAR(80) NOT NULL COMMENT '角色名称',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色状态',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色';

-- identity / 员工角色关联
CREATE TABLE user_role (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  user_id BINARY(16) NOT NULL COMMENT '员工',
  role_id BINARY(16) NOT NULL COMMENT '角色',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id,role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工角色关联';

-- identity / 角色动作权限
CREATE TABLE role_permission (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  role_id BINARY(16) NOT NULL COMMENT '角色',
  permission_code VARCHAR(100) NOT NULL COMMENT '后端白名单动作编码，不接受客户端自定义',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_permission (role_id,permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色动作权限';

-- identity / 员工数据范围
CREATE TABLE user_scope (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  user_id BINARY(16) NOT NULL COMMENT '员工',
  scope_kind VARCHAR(16) NOT NULL COMMENT 'ALL、ORG或STORE',
  org_id BINARY(16) NULL COMMENT '组织范围',
  store_id BINARY(16) NULL COMMENT '指定门店范围',
  scope_key BINARY(16) GENERATED ALWAYS AS (COALESCE(org_id,store_id,UNHEX('00000000000000000000000000000000'))) STORED COMMENT '范围去重计算值',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_scope (user_id,scope_kind,scope_key),
  CONSTRAINT ck_user_scope_1 CHECK ((scope_kind='ALL' AND org_id IS NULL AND store_id IS NULL) OR (scope_kind='ORG' AND org_id IS NOT NULL AND store_id IS NULL) OR (scope_kind='STORE' AND org_id IS NULL AND store_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='员工数据范围';

-- identity / 设备会话
CREATE TABLE auth_session (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  user_id BINARY(16) NOT NULL COMMENT '员工',
  device_id VARCHAR(128) NOT NULL COMMENT '客户端设备标识，非可信硬件证明',
  refresh_hash BINARY(32) NOT NULL COMMENT '刷新令牌SHA256，只保存摘要',
  permission_version BIGINT UNSIGNED NOT NULL COMMENT '签发时权限版本',
  expires_at DATETIME(6) NOT NULL COMMENT '刷新有效期',
  revoked_at DATETIME(6) NULL COMMENT '注销时间',
  last_seen_at DATETIME(6) NOT NULL COMMENT '最后活动时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_refresh_hash (refresh_hash),
  KEY ix_session_user (user_id,revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备会话';

-- identity / 站内通知
CREATE TABLE notification (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  user_id BINARY(16) NOT NULL COMMENT '接收员工',
  kind VARCHAR(32) NOT NULL COMMENT '任务或同步等通知类型',
  payload_json JSON NOT NULL COMMENT '通知内容及受保护业务资源ID',
  read_at DATETIME(6) NULL COMMENT '已读时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  KEY ix_notification_user (user_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知';

-- masterdata / 客户
CREATE TABLE customer (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  code VARCHAR(40) NOT NULL COMMENT '客户编号',
  name VARCHAR(160) NOT NULL COMMENT '客户名称',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '启用或归档',
  revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_customer_code (code),
  CONSTRAINT ck_customer_1 CHECK (status IN ('ACTIVE','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户';

-- masterdata / 客户品牌
CREATE TABLE brand (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  customer_id BINARY(16) NOT NULL COMMENT '所属客户',
  name VARCHAR(160) NOT NULL COMMENT '品牌名称',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '启用或归档',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_brand_customer (customer_id,name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户品牌';

-- masterdata / 门店
CREATE TABLE store (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  customer_id BINARY(16) NOT NULL COMMENT '所属客户',
  brand_id BINARY(16) NULL COMMENT '所属品牌，必须同客户',
  org_id BINARY(16) NOT NULL COMMENT '所属区域或团队',
  owner_user_id BINARY(16) NULL COMMENT '服务负责人',
  code VARCHAR(40) NOT NULL COMMENT '门店唯一编号',
  name VARCHAR(160) NOT NULL COMMENT '门店名称',
  address VARCHAR(500) NOT NULL COMMENT '完整地址',
  timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT 'IANA时区，用于计划日期',
  latitude DECIMAL(10,7) NULL COMMENT '纬度',
  longitude DECIMAL(10,7) NULL COMMENT '经度',
  contact_cipher VARBINARY(2048) NULL COMMENT '加密联系人资料',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '启用或归档',
  revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_store_code (code),
  KEY ix_store_scope (org_id,status,id),
  KEY ix_store_customer (customer_id,id),
  CONSTRAINT ck_store_1 CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
  CONSTRAINT ck_store_2 CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
  CONSTRAINT ck_store_3 CHECK (status IN ('ACTIVE','ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店';

-- masterdata / 门店分区
CREATE TABLE store_area (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  store_id BINARY(16) NOT NULL COMMENT '所属门店',
  code VARCHAR(40) NOT NULL COMMENT '门店内分区编号',
  name VARCHAR(100) NOT NULL COMMENT '区域名称',
  floor VARCHAR(40) NULL COMMENT '楼层',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '启用或归档',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_area_code (store_id,code),
  UNIQUE KEY uk_area_store (id,store_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店分区';

-- masterdata / 版本化配置
CREATE TABLE config_release (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  kind VARCHAR(24) NOT NULL COMMENT 'DICTIONARY、NOTICE、TEMPLATE、BRAND、APP',
  version_no INT UNSIGNED NOT NULL COMMENT '同类递增版本',
  payload_json JSON NOT NULL COMMENT '字典项目稳定UUID及启停标记、须知正文、品牌或模板参数',
  payload_hash BINARY(32) NOT NULL COMMENT '规范化配置摘要',
  published_by BINARY(16) NOT NULL COMMENT '发布人员',
  published_at DATETIME(6) NOT NULL COMMENT '生效时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_version (kind,version_no),
  CONSTRAINT ck_config_release_1 CHECK (kind IN ('DICTIONARY','NOTICE','TEMPLATE','BRAND','APP'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='版本化配置';

-- media / 离线模板与资料文件
CREATE TABLE offline_asset (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  release_id BINARY(16) NOT NULL COMMENT '配置版本',
  object_key VARCHAR(500) NOT NULL COMMENT '私有对象键',
  sha256 BINARY(32) NOT NULL COMMENT '原始文件摘要',
  byte_size BIGINT UNSIGNED NOT NULL COMMENT '文件大小',
  mime_type VARCHAR(100) NOT NULL COMMENT '真实内容类型',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_offline_asset_1 CHECK (byte_size > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='离线模板与资料文件';

-- dispatch / 服务计划
CREATE TABLE service_plan (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  store_id BINARY(16) NOT NULL COMMENT '门店',
  assignee_id BINARY(16) NOT NULL COMMENT '默认执行人员',
  service_type VARCHAR(24) NOT NULL COMMENT '常规、应急、复查',
  recurrence_json JSON NOT NULL COMMENT '单次或每周每月规则；不接受任意脚本',
  start_date DATE NOT NULL COMMENT '门店本地开始日期',
  end_date DATE NULL COMMENT '结束日期',
  next_run_at DATETIME(6) NULL COMMENT '下次展开时间UTC',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE、PAUSED、ARCHIVED',
  revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '计划版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  KEY ix_plan_due (status,next_run_at,id),
  CONSTRAINT ck_service_plan_1 CHECK (service_type IN ('ROUTINE','EMERGENCY','RECHECK')),
  CONSTRAINT ck_service_plan_2 CHECK (end_date IS NULL OR end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务计划';

-- dispatch / 服务任务
CREATE TABLE service_task (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  store_id BINARY(16) NOT NULL COMMENT '门店',
  plan_id BINARY(16) NULL COMMENT '来源计划，应急可空',
  occurrence_key VARCHAR(80) NULL COMMENT '稳定发生槽位（本地日期加序号），不含计划版本，防止改计划后重复生成',
  assignee_id BINARY(16) NOT NULL COMMENT '唯一主执行人',
  service_type VARCHAR(24) NOT NULL COMMENT '服务类型',
  planned_date DATE NOT NULL COMMENT '门店本地计划日期',
  planned_start_at DATETIME(6) NULL COMMENT '可选计划开始时间UTC',
  planned_end_at DATETIME(6) NULL COMMENT '可选计划结束时间UTC',
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '业务状态，不含上传状态',
  assignment_version BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每次改派取消递增',
  revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '任务ETag版本',
  creation_source VARCHAR(24) NOT NULL COMMENT 'PLAN、DISPATCH、EMPLOYEE_EMERGENCY',
  instructions TEXT NULL COMMENT '任务说明',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_plan_occurrence (plan_id,occurrence_key),
  UNIQUE KEY uk_task_store (id,store_id),
  KEY ix_task_assignee_date (assignee_id,planned_date,status,id),
  KEY ix_task_store_date (store_id,planned_date,id),
  CONSTRAINT ck_service_task_1 CHECK (status IN ('PENDING','IN_PROGRESS','AWAITING_CONFIRMATION','COMPLETED','CANCELLED')),
  CONSTRAINT ck_service_task_2 CHECK (service_type IN ('ROUTINE','EMERGENCY','RECHECK')),
  CONSTRAINT ck_service_task_3 CHECK ((plan_id IS NULL AND occurrence_key IS NULL) OR (plan_id IS NOT NULL AND occurrence_key IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务任务';

-- dispatch / 任务变更日志
CREATE TABLE task_change (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  task_id BINARY(16) NOT NULL COMMENT '任务',
  actor_id BINARY(16) NOT NULL COMMENT '操作人',
  reason VARCHAR(1000) NOT NULL COMMENT '改期改派取消原因',
  before_json JSON NOT NULL COMMENT '变更前',
  after_json JSON NOT NULL COMMENT '变更后',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  KEY ix_change_task (task_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务变更日志';

-- ledger / 设施台账
CREATE TABLE facility (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  store_id BINARY(16) NOT NULL COMMENT '所属门店',
  area_id BINARY(16) NULL COMMENT '所属区域',
  code VARCHAR(40) NOT NULL COMMENT '门店内编号，移除后不复用',
  facility_type_id BINARY(16) NOT NULL COMMENT '字典稳定项目UUID',
  position VARCHAR(300) NOT NULL COMMENT '详细位置',
  floor VARCHAR(40) NULL COMMENT '楼层',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '在用、停用、移除',
  revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '台账版本',
  last_event_id BINARY(16) NULL COMMENT '最后已应用事件，事务内赋值',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_facility_code (store_id,code),
  UNIQUE KEY uk_facility_store (id,store_id),
  KEY ix_facility_store (store_id,status,id),
  CONSTRAINT ck_facility_1 CHECK (status IN ('ACTIVE','INACTIVE','REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设施台账';

-- ledger / 长期风险台账
CREATE TABLE risk (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  store_id BINARY(16) NOT NULL COMMENT '所属门店',
  area_id BINARY(16) NULL COMMENT '所在区域',
  risk_type_id BINARY(16) NOT NULL COMMENT '风险分类字典UUID',
  pest_type_ids_json JSON NOT NULL COMMENT '关联的多个虫种UUID，按接口保留全部',
  position VARCHAR(300) NOT NULL COMMENT '位置',
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT '整改状态',
  revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观版本',
  responsible_party VARCHAR(100) NOT NULL COMMENT '客户或公司责任方',
  contact_cipher VARBINARY(2048) NULL COMMENT '责任联系人加密资料',
  followup_user_id BINARY(16) NOT NULL COMMENT '公司跟进人',
  due_date DATE NULL COMMENT '门店本地计划完成日期',
  found_at DATETIME(6) NOT NULL COMMENT '首次发现时间',
  closed_at DATETIME(6) NULL COMMENT '本次关闭时间，重开后置空，历史保留',
  content_json JSON NOT NULL COMMENT '描述、建议、照片引用等完整当前状态',
  last_event_id BINARY(16) NULL COMMENT '最后已应用事件',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_risk_store (id,store_id),
  KEY ix_risk_store_status (store_id,status,due_date,id),
  KEY ix_risk_owner (followup_user_id,status,due_date,id),
  CONSTRAINT ck_risk_1 CHECK (status IN ('PENDING','IN_PROGRESS','AWAITING_REVIEW','CLOSED')),
  CONSTRAINT ck_risk_2 CHECK ((status='CLOSED' AND closed_at IS NOT NULL) OR (status<>'CLOSED' AND closed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='长期风险台账';

-- ledger / 门店静态评估历史
CREATE TABLE store_assessment (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  store_id BINARY(16) NOT NULL COMMENT '门店',
  revision BIGINT UNSIGNED NOT NULL COMMENT '门店评估版本',
  service_id BINARY(16) NULL COMMENT '来源服务，后台代录可空',
  actor_id BINARY(16) NOT NULL COMMENT '评估人',
  content_json JSON NOT NULL COMMENT '各项状态、照片和备注',
  occurred_at DATETIME(6) NOT NULL COMMENT '现场评估时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_assessment_revision (store_id,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='门店静态评估历史';

-- offline / 不可变离线上下文
CREATE TABLE offline_context (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  store_id BINARY(16) NOT NULL COMMENT '授权门店',
  task_id BINARY(16) NULL COMMENT '常规任务上下文，门店应急上下文为空',
  user_id BINARY(16) NOT NULL COMMENT '原下载员工',
  device_id VARCHAR(128) NOT NULL COMMENT '下载设备',
  assignment_version BIGINT UNSIGNED NULL COMMENT '下载时派工版本',
  permission_version BIGINT UNSIGNED NOT NULL COMMENT '下载时权限版本',
  context_digest BINARY(32) NOT NULL COMMENT 'contextBody的JCS SHA256',
  canonical_body LONGBLOB NOT NULL COMMENT 'JCS UTF8原始规范化字节，不从MySQL JSON回算',
  context_json JSON NOT NULL COMMENT '可查询副本，非摘要依据',
  expires_at DATETIME(6) NOT NULL COMMENT '离线授权普通有效期',
  evidence_until DATETIME(6) NOT NULL COMMENT '仅隔离证据接收截止时间',
  downloaded_at DATETIME(6) NULL COMMENT '客户端校验全部资产后的回执时间',
  revoked_at DATETIME(6) NULL COMMENT '普通读写授权撤销时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  KEY ix_context_user (user_id,created_at,id),
  CONSTRAINT ck_offline_context_1 CHECK (evidence_until >= expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变离线上下文';

-- workorder / 实际服务及报告版本头
CREATE TABLE service (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  task_id BINARY(16) NOT NULL COMMENT '一任务对应一次实际服务；更正不新建服务',
  store_id BINARY(16) NOT NULL COMMENT '门店快筛字段',
  actor_id BINARY(16) NOT NULL COMMENT '现场主执行员工',
  context_id BINARY(16) NOT NULL COMMENT '初始上下文',
  phase VARCHAR(24) NOT NULL DEFAULT 'EDITABLE' COMMENT '当前作业阶段，与任务业务状态独立',
  head_revision BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '当前内容代次，所有签字发布操作共同校验',
  active_snapshot_id BINARY(16) NULL COMMENT '当前待签或已发布快照',
  current_report_id BINARY(16) NULL COMMENT '当前正式报告指针，更正准备时仍指旧版',
  service_started_at DATETIME(6) NULL COMMENT '真实签到时间，不是上传时间',
  service_ended_at DATETIME(6) NULL COMMENT '真实现场确认时间，不随更正修改',
  completed_at DATETIME(6) NULL COMMENT '首次正式发布成功的服务器时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_service_task (task_id),
  UNIQUE KEY uk_service_store (id,store_id),
  KEY ix_service_actor (actor_id,created_at,id),
  CONSTRAINT ck_service_1 CHECK (phase IN ('EDITABLE','FROZEN','AWAITING_CONFIRMATION','PUBLISHING','PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实际服务及报告版本头';

-- workorder / 当前完整作业草稿
CREATE TABLE service_draft (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  service_id BINARY(16) NOT NULL COMMENT '服务',
  context_id BINARY(16) NOT NULL COMMENT '草稿使用的固定上下文',
  revision BIGINT UNSIGNED NOT NULL COMMENT 'If-Match对应版本',
  field_commit_revision BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最近成功台账提交的草稿版本',
  schema_version VARCHAR(20) NOT NULL COMMENT 'ServiceContent格式版本',
  content_json JSON NOT NULL COMMENT '完整ServiceContent；所有巡检、处置、用药、总结、签到等',
  correction_of_report_id BINARY(16) NULL COMMENT '更正源报告',
  saved_by BINARY(16) NOT NULL COMMENT '最近保存员工',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_draft_service (service_id),
  CONSTRAINT ck_service_draft_1 CHECK (field_commit_revision <= revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='当前完整作业草稿';

-- ledger / 台账事件与业务幂等凭证
CREATE TABLE ledger_event (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  service_id BINARY(16) NULL COMMENT '服务内事件；独立跟进可空',
  store_id BINARY(16) NOT NULL COMMENT '所属门店',
  entity_id BINARY(16) NOT NULL COMMENT '设施、风险或评估业务ID',
  kind VARCHAR(32) NOT NULL COMMENT '设施变更、新风险、跟进、评估',
  expected_revision BIGINT UNSIGNED NOT NULL COMMENT '应用前期望版本，新建为0',
  result_revision BIGINT UNSIGNED NOT NULL COMMENT '应用后版本',
  payload_hash BINARY(32) NOT NULL COMMENT '不可变事件内容摘要',
  payload_json JSON NOT NULL COMMENT '事件内容含变更前后、来源及照片引用',
  actor_id BINARY(16) NOT NULL COMMENT '执行人',
  occurred_at DATETIME(6) NOT NULL COMMENT '原始现场事件时间',
  supersedes_event_id BINARY(16) NULL COMMENT '显式更正的旧事件ID',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  KEY ix_event_entity (entity_id,created_at,id),
  KEY ix_event_service (service_id,id),
  CONSTRAINT ck_ledger_event_1 CHECK (result_revision > expected_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='台账事件与业务幂等凭证';

-- ledger / 风险跟进历史
CREATE TABLE risk_followup (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  risk_id BINARY(16) NOT NULL COMMENT '长期风险',
  ledger_event_id BINARY(16) NOT NULL COMMENT '唯一应用事件',
  service_id BINARY(16) NULL COMMENT '服务内跟进或独立跟进',
  from_status VARCHAR(24) NOT NULL COMMENT '变更前状态',
  to_status VARCHAR(24) NOT NULL COMMENT '变更后状态',
  result VARCHAR(32) NOT NULL COMMENT '复核结果或未核查',
  content_json JSON NOT NULL COMMENT '说明、未核查原因、材料来源、改善照片',
  occurred_at DATETIME(6) NOT NULL COMMENT '实际跟进时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_followup_event (ledger_event_id),
  KEY ix_followup_risk (risk_id,occurred_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风险跟进历史';

-- ledger / 现场台账提交回执
CREATE TABLE field_commit (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  service_id BINARY(16) NOT NULL COMMENT '服务',
  draft_revision BIGINT UNSIGNED NOT NULL COMMENT '提交草稿版本',
  request_hash BINARY(32) NOT NULL COMMENT '提交内容摘要',
  receipt_json JSON NOT NULL COMMENT '每个事件ID及应用结果版本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commit_revision (service_id,draft_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='现场台账提交回执';

-- media / 受保护原始附件
CREATE TABLE attachment (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  store_id BINARY(16) NOT NULL COMMENT '门店边界',
  owner_id BINARY(16) NOT NULL COMMENT '原上传人',
  context_id BINARY(16) NOT NULL COMMENT '授权来源',
  purpose VARCHAR(32) NOT NULL COMMENT 'PHOTO、SIGNATURE、SERVICE_FILE等',
  scope VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL或QUARANTINE',
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING、VERIFYING、READY、FAILED',
  object_key VARCHAR(500) NOT NULL COMMENT '不可变私有对象键',
  sha256 BINARY(32) NOT NULL COMMENT '预期原始文件SHA256',
  byte_size BIGINT UNSIGNED NOT NULL COMMENT '预期字节数',
  mime_type VARCHAR(100) NOT NULL COMMENT '内容识别后的MIME',
  verified_at DATETIME(6) NULL COMMENT '摘要、大小、文件类型等校验通过时间',
  failure_code VARCHAR(80) NULL COMMENT '失败原因',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  KEY ix_attachment_owner (owner_id,status,created_at,id),
  CONSTRAINT ck_attachment_1 CHECK (byte_size > 0),
  CONSTRAINT ck_attachment_2 CHECK (status IN ('PENDING','VERIFYING','READY','FAILED')),
  CONSTRAINT ck_attachment_3 CHECK (scope IN ('NORMAL','QUARANTINE')),
  CONSTRAINT ck_attachment_4 CHECK ((status='READY' AND verified_at IS NOT NULL) OR status<>'READY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受保护原始附件';

-- media / 附件整文件上传会话
CREATE TABLE upload_session (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  attachment_id BINARY(16) NOT NULL COMMENT '目标附件',
  user_id BINARY(16) NOT NULL COMMENT '上传人',
  temporary_key VARCHAR(500) NOT NULL COMMENT '暂存对象键',
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN、RECEIVED、VERIFIED、EXPIRED',
  expires_at DATETIME(6) NOT NULL COMMENT '本次上传会话有效期',
  received_size BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '接收大小',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  KEY ix_upload_attachment (attachment_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='附件整文件上传会话';

-- confirmation / 不可变已冻结正文
CREATE TABLE service_snapshot (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  service_id BINARY(16) NOT NULL COMMENT '实际服务',
  context_id BINARY(16) NOT NULL COMMENT '固定上下文',
  content_version INT UNSIGNED NOT NULL COMMENT '服务内内容版本',
  head_revision BIGINT UNSIGNED NOT NULL COMMENT '冻结时服务代次',
  draft_revision BIGINT UNSIGNED NOT NULL COMMENT '冻结时草稿版本',
  content_hash BINARY(32) NOT NULL COMMENT 'SnapshotPayload JCS SHA256',
  canonical_payload LONGBLOB NOT NULL COMMENT 'JCS UTF8原字节，是签字和渲染正文依据',
  payload_json JSON NOT NULL COMMENT '校验后的查询副本，禁止回算签字摘要',
  template_release_id BINARY(16) NOT NULL COMMENT '固定报告模板版本',
  renderer_version VARCHAR(100) NOT NULL COMMENT '渲染镜像及字体版本标识',
  invalidated_at DATETIME(6) NULL COMMENT '签后重开时作废标记，正文不可修改',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_snapshot_version (service_id,content_version),
  UNIQUE KEY uk_snapshot_service (id,service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='不可变已冻结正文';

-- confirmation / 快照附件清单
CREATE TABLE snapshot_attachment (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  snapshot_id BINARY(16) NOT NULL COMMENT '快照',
  attachment_id BINARY(16) NOT NULL COMMENT '受保护附件',
  sha256 BINARY(32) NOT NULL COMMENT '快照期望附件原始摘要',
  purpose VARCHAR(32) NOT NULL COMMENT '在报告中的用途',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_snapshot_attachment (snapshot_id,attachment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='快照附件清单';

-- confirmation / 客户阅读回执
CREATE TABLE preview_session (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  snapshot_id BINARY(16) NOT NULL COMMENT '预览的固定正文',
  user_id BINARY(16) NOT NULL COMMENT '承载确认的员工',
  content_hash BINARY(32) NOT NULL COMMENT '预览正文摘要',
  started_at DATETIME(6) NOT NULL COMMENT '开始显示时间',
  completed_at DATETIME(6) NOT NULL COMMENT '主动确认时间',
  visible_duration_ms INT UNSIGNED NOT NULL COMMENT '前台实际可见时长，至少5000',
  is_offline BOOLEAN NOT NULL COMMENT '离线回执不能视为可信服务器计时',
  device_id VARCHAR(128) NOT NULL COMMENT '设备',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_preview_session_1 CHECK (visible_duration_ms >= 5000),
  CONSTRAINT ck_preview_session_2 CHECK (completed_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户阅读回执';

-- confirmation / 签署凭证历史
CREATE TABLE service_signature (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  snapshot_id BINARY(16) NOT NULL COMMENT '固定正文快照',
  role VARCHAR(16) NOT NULL COMMENT 'STAFF或CLIENT',
  signer_name VARCHAR(80) NOT NULL COMMENT '签署人姓名',
  staff_user_id BINARY(16) NULL COMMENT '员工签署人',
  staff_signature_id BINARY(16) NULL COMMENT '客户签署绑定的员工签字',
  preview_session_id BINARY(16) NULL COMMENT '客户阅读回执',
  attachment_id BINARY(16) NOT NULL COMMENT '签字图片',
  content_hash BINARY(32) NOT NULL COMMENT '签署正文摘要',
  attestation_hash BINARY(32) NOT NULL COMMENT '签署凭证JCS摘要',
  canonical_attestation LONGBLOB NOT NULL COMMENT '签署凭证规范字节，不含attestationHash自身',
  signed_at DATETIME(6) NOT NULL COMMENT '设备签署时间',
  rating TINYINT UNSIGNED NULL COMMENT '客户可选1至5星',
  comment TEXT NULL COMMENT '客户评价，不改正文',
  supersedes_id BINARY(16) NULL COMMENT '被替换签字',
  replacement_reason VARCHAR(1000) NULL COMMENT '替换原因',
  invalidated_at DATETIME(6) NULL COMMENT '作废时间',
  active_role VARCHAR(16) GENERATED ALWAYS AS (CASE WHEN invalidated_at IS NULL THEN role ELSE NULL END) STORED COMMENT '有效签字唯一约束',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_signature_active (snapshot_id,active_role),
  CONSTRAINT ck_service_signature_1 CHECK (role IN ('STAFF','CLIENT')),
  CONSTRAINT ck_service_signature_2 CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
  CONSTRAINT ck_service_signature_3 CHECK ((role='STAFF' AND staff_user_id IS NOT NULL AND staff_signature_id IS NULL AND preview_session_id IS NULL AND rating IS NULL) OR (role='CLIENT' AND staff_user_id IS NULL AND staff_signature_id IS NOT NULL AND preview_session_id IS NOT NULL)),
  CONSTRAINT ck_service_signature_4 CHECK ((supersedes_id IS NULL AND replacement_reason IS NULL) OR (supersedes_id IS NOT NULL AND replacement_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签署凭证历史';

-- confirmation / 客户缺席或拒签记录
CREATE TABLE confirmation_exception (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  service_id BINARY(16) NOT NULL COMMENT '服务',
  snapshot_id BINARY(16) NULL COMMENT '对应预览正文',
  reason_code VARCHAR(24) NOT NULL COMMENT 'ABSENT、REFUSED等',
  reason VARCHAR(1000) NOT NULL COMMENT '具体原因',
  actor_id BINARY(16) NOT NULL COMMENT '登记员工',
  occurred_at DATETIME(6) NOT NULL COMMENT '现场发生时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  KEY ix_confirmation_service (service_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户缺席或拒签记录';

-- offline / 持久化离线提交包
CREATE TABLE offline_submission (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  user_id BINARY(16) NOT NULL COMMENT '原提交员工',
  context_id BINARY(16) NOT NULL COMMENT '原授权上下文',
  service_id BINARY(16) NOT NULL COMMENT '包内服务UUID，接收时可能尚未创建，不设FK',
  task_id BINARY(16) NOT NULL COMMENT '包内任务UUID，应急任务可能尚未创建，不设FK',
  assignment_version BIGINT UNSIGNED NOT NULL COMMENT '客户端已知派工版本',
  request_hash BINARY(32) NOT NULL COMMENT '包的规范摘要',
  canonical_payload LONGBLOB NOT NULL COMMENT '原始完整包规范字节，先持久化再异步校验',
  status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED' COMMENT '接收、应用、冲突或拒绝',
  result_json JSON NULL COMMENT '应用回执或错误摘要',
  received_at DATETIME(6) NOT NULL COMMENT '服务器接收时间',
  applied_at DATETIME(6) NULL COMMENT '成功应用时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  KEY ix_submission_user (user_id,received_at,id),
  KEY ix_submission_queue (status,received_at,id),
  CONSTRAINT ck_offline_submission_1 CHECK (status IN ('RECEIVED','PROCESSING','APPLIED','CONFLICT','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持久化离线提交包';

-- offline / 隔离同步异常
CREATE TABLE sync_exception (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  submission_id BINARY(16) NOT NULL COMMENT '保留的原始提交包',
  kind VARCHAR(32) NOT NULL COMMENT '改派、取消、版本冲突等',
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN、RESOLVED；拒绝采用记录在resolution_json',
  resolution_json JSON NULL COMMENT '处理方案、保留证据、目标服务与逐项选择',
  resolved_by BINARY(16) NULL COMMENT '有范围权限的处理人',
  resolved_at DATETIME(6) NULL COMMENT '处理时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_exception_submission (submission_id),
  KEY ix_exception_queue (status,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='隔离同步异常';

-- reporting / 报告生成业务任务
CREATE TABLE publication_job (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  service_id BINARY(16) NOT NULL COMMENT '服务',
  snapshot_id BINARY(16) NOT NULL COMMENT '固定正文',
  head_revision BIGINT UNSIGNED NOT NULL COMMENT '预期服务代次',
  staff_signature_id BINARY(16) NOT NULL COMMENT '固定员工签署凭证',
  client_signature_id BINARY(16) NOT NULL COMMENT '固定客户签署凭证',
  status VARCHAR(16) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED、RUNNING、SUCCEEDED、FAILED、BLOCKED',
  attempt_no INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '尝试次数',
  error_code VARCHAR(80) NULL COMMENT '失败编码',
  error_message VARCHAR(1000) NULL COMMENT '安全的可展示失败原因',
  report_id BINARY(16) NULL COMMENT '成功生成报告',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_publication_snapshot (service_id,snapshot_id),
  KEY ix_publication_status (status,created_at,id),
  CONSTRAINT ck_publication_job_1 CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','BLOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报告生成业务任务';

-- reporting / 正式报告版本
CREATE TABLE service_report (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  service_id BINARY(16) NOT NULL COMMENT '同一次实际服务',
  snapshot_id BINARY(16) NOT NULL COMMENT '已发布固定正文',
  version_no INT UNSIGNED NOT NULL COMMENT '正式报告版本',
  report_number VARCHAR(80) NOT NULL COMMENT '全局唯一对外编号，编号规则由公司配置',
  status VARCHAR(16) NOT NULL COMMENT 'PUBLISHED或SUPERSEDED',
  active_service_id BINARY(16) GENERATED ALWAYS AS (CASE WHEN status='PUBLISHED' THEN service_id ELSE NULL END) STORED COMMENT '每个服务最多一个当前正式报告',
  staff_signature_id BINARY(16) NOT NULL COMMENT '报告实际嵌入员工签署',
  client_signature_id BINARY(16) NOT NULL COMMENT '报告实际嵌入客户签署',
  content_hash BINARY(32) NOT NULL COMMENT '对应正文SHA256',
  pdf_sha256 BINARY(32) NOT NULL COMMENT '最终PDF字节摘要',
  object_key VARCHAR(500) NOT NULL COMMENT '不可变私有PDF对象键',
  byte_size BIGINT UNSIGNED NOT NULL COMMENT 'PDF字节数',
  supersedes_report_id BINARY(16) NULL COMMENT '替代的旧版报告',
  published_at DATETIME(6) NOT NULL COMMENT '服务器发布成功时间',
  service_started_at DATETIME(6) NOT NULL COMMENT '本次服务真实到店时间快照',
  service_ended_at DATETIME(6) NOT NULL COMMENT '本次服务真实结束时间快照',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_version (service_id,version_no),
  UNIQUE KEY uk_report_snapshot (service_id,snapshot_id),
  UNIQUE KEY uk_report_number (report_number),
  UNIQUE KEY uk_report_current (active_service_id),
  UNIQUE KEY uk_report_service (id,service_id),
  KEY ix_report_published (published_at,id),
  CONSTRAINT ck_service_report_1 CHECK (status IN ('PUBLISHED','SUPERSEDED')),
  CONSTRAINT ck_service_report_2 CHECK (byte_size > 0),
  CONSTRAINT ck_service_report_3 CHECK (service_ended_at >= service_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='正式报告版本';

-- analytics / 虫情与捕获统计事实
CREATE TABLE snapshot_finding (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  snapshot_id BINARY(16) NOT NULL COMMENT '固定快照',
  store_id BINARY(16) NOT NULL COMMENT '门店',
  source_record_id BINARY(16) NOT NULL COMMENT '原始Finding.id；设施捕获用Inspection.id，与虫种组合唯一',
  source VARCHAR(24) NOT NULL COMMENT 'CUSTOMER、ON_SITE、FACILITY',
  pest_type_id BINARY(16) NOT NULL COMMENT '虫种稳定UUID',
  quantity_state VARCHAR(20) NOT NULL COMMENT 'EXACT、UNKNOWN',
  quantity INT UNSIGNED NULL COMMENT '已知时为0至1000000000的整数',
  event_group_id BINARY(16) NULL COMMENT '同一虫情事件关联；不得跨来源直接合计',
  facility_id BINARY(16) NULL COMMENT '设施来源时必填',
  occurred_at DATETIME(6) NOT NULL COMMENT '发现或检查时间',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_finding_source (snapshot_id,source,source_record_id,pest_type_id),
  KEY ix_finding_chart (store_id,source,pest_type_id,occurred_at,id),
  CONSTRAINT ck_snapshot_finding_1 CHECK (quantity IS NULL OR quantity <= 1000000000),
  CONSTRAINT ck_snapshot_finding_2 CHECK (source IN ('CUSTOMER','ON_SITE','FACILITY')),
  CONSTRAINT ck_snapshot_finding_3 CHECK ((quantity_state='EXACT' AND quantity IS NOT NULL) OR (quantity_state='UNKNOWN' AND quantity IS NULL)),
  CONSTRAINT ck_snapshot_finding_4 CHECK ((source='FACILITY' AND facility_id IS NOT NULL) OR (source IN ('CUSTOMER','ON_SITE') AND facility_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='虫情与捕获统计事实';

-- analytics / 设施检查覆盖统计事实
CREATE TABLE snapshot_inspection (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  snapshot_id BINARY(16) NOT NULL COMMENT '固定快照',
  facility_id BINARY(16) NOT NULL COMMENT '设施',
  store_id BINARY(16) NOT NULL COMMENT '门店',
  check_result VARCHAR(24) NOT NULL COMMENT 'CHECKED、NOT_CHECKED、UNCONFIRMABLE',
  condition_code VARCHAR(32) NULL COMMENT '设施状态，未检查或无法确认时为空',
  consumable_action VARCHAR(24) NULL COMMENT 'REPLACED、NOT_REPLACED、NOT_APPLICABLE；不折算布尔值',
  not_checked_reason VARCHAR(1000) NULL COMMENT '未检查原因',
  content_json JSON NOT NULL COMMENT '检查记录完整查询副本',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_inspection_snapshot (snapshot_id,facility_id),
  KEY ix_inspection_store (store_id,check_result,id),
  CONSTRAINT ck_snapshot_inspection_1 CHECK ((check_result='CHECKED' AND condition_code IS NOT NULL AND consumable_action IS NOT NULL AND not_checked_reason IS NULL) OR (check_result IN ('NOT_CHECKED','UNCONFIRMABLE') AND condition_code IS NULL AND consumable_action IS NULL AND CHAR_LENGTH(TRIM(not_checked_reason))>0 AND not_checked_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设施检查覆盖统计事实';

-- analytics / 处置与用药查询事实
CREATE TABLE snapshot_treatment (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  snapshot_id BINARY(16) NOT NULL COMMENT '固定快照',
  source_record_id BINARY(16) NOT NULL COMMENT '处置UUID',
  kind VARCHAR(32) NOT NULL COMMENT '清洁、封堵、物理、用药、观察等',
  purpose VARCHAR(1000) NULL COMMENT '预防性处理目的',
  content_json JSON NOT NULL COMMENT '关联虫情设施风险UUID、实际用药、浓度和用量字符串及单位',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_treatment_snapshot (snapshot_id,source_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='处置与用药查询事实';

-- platform / 持久化异步任务与事务发件箱
CREATE TABLE job_queue (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  kind VARCHAR(40) NOT NULL COMMENT 'SYNC_APPLY、REPORT_RENDER、EXPORT等',
  business_id BINARY(16) NOT NULL COMMENT '业务对象ID',
  status VARCHAR(16) NOT NULL DEFAULT 'READY' COMMENT 'READY、RUNNING、DONE、DEAD',
  payload_json JSON NOT NULL COMMENT '最小引用数据，禁止放二进制或重复完整服务正文',
  attempts INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '尝试次数',
  max_attempts INT UNSIGNED NOT NULL DEFAULT 5 COMMENT '可配置上限',
  next_run_at DATETIME(6) NOT NULL COMMENT '可运行时间',
  lease_owner VARCHAR(100) NULL COMMENT '工作进程标识',
  lease_until DATETIME(6) NULL COMMENT '租约截止时间',
  fencing_token BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '每次领取递增，最终提交校验',
  last_error VARCHAR(1000) NULL COMMENT '安全错误说明',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_job_business (kind,business_id),
  KEY ix_job_ready (status,next_run_at,id),
  KEY ix_job_lease (status,lease_until,id),
  CONSTRAINT ck_job_queue_1 CHECK (status IN ('READY','RUNNING','DONE','DEAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='持久化异步任务与事务发件箱';

-- platform / HTTP幂等结果
CREATE TABLE idempotency_record (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  actor_id BINARY(16) NOT NULL COMMENT '当前员工',
  operation_id VARCHAR(100) NOT NULL COMMENT 'OpenAPI operationId',
  key_hash BINARY(32) NOT NULL COMMENT 'Idempotency-Key摘要',
  request_hash BINARY(32) NOT NULL COMMENT '规范化请求摘要，含资源路径和版本',
  status VARCHAR(16) NOT NULL COMMENT 'IN_PROGRESS、COMPLETED',
  http_status SMALLINT UNSIGNED NULL COMMENT '最终HTTP状态',
  response_json JSON NULL COMMENT '安全响应或业务回执引用',
  expires_at DATETIME(6) NOT NULL COMMENT '清理期限；业务唯一ID永久防重复',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  UNIQUE KEY uk_idempotency (actor_id,operation_id,key_hash),
  KEY ix_idempotency_expiry (expires_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='HTTP幂等结果';

-- platform / 关键审计记录
CREATE TABLE audit_log (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  actor_id BINARY(16) NULL COMMENT '人员操作或系统任务',
  request_id VARCHAR(80) NOT NULL COMMENT '请求跟踪ID',
  action VARCHAR(100) NOT NULL COMMENT '派工、风险关闭、导出、签字更正等动作',
  resource_type VARCHAR(64) NOT NULL COMMENT '目标类型',
  resource_id BINARY(16) NULL COMMENT '目标业务ID',
  store_id BINARY(16) NULL COMMENT '所属门店',
  result VARCHAR(24) NOT NULL COMMENT '成功或拒绝',
  reason VARCHAR(1000) NULL COMMENT '操作原因',
  summary_json JSON NOT NULL COMMENT '字段级变化摘要，不记录令牌及完整敏感正文',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  PRIMARY KEY (id),
  KEY ix_audit_resource (resource_type,resource_id,created_at,id),
  KEY ix_audit_actor (actor_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关键审计记录';

-- platform / 后台批量任务
CREATE TABLE import_export_job (
  id BINARY(16) NOT NULL COMMENT 'UUIDv4；与API id相同；不做时间位交换',
  kind VARCHAR(16) NOT NULL COMMENT 'IMPORT或EXPORT',
  actor_id BINARY(16) NOT NULL COMMENT '申请人',
  scope_json JSON NOT NULL COMMENT '申请时范围及过滤条件；执行和下载仍复核现权限',
  status VARCHAR(24) NOT NULL COMMENT 'PREVIEW、QUEUED、RUNNING、SUCCEEDED、FAILED',
  input_object_key VARCHAR(500) NULL COMMENT '导入暂存文件',
  output_object_key VARCHAR(500) NULL COMMENT '错误预览或导出文件',
  result_json JSON NULL COMMENT '数量、逐行错误及提交摘要',
  expires_at DATETIME(6) NULL COMMENT '临时文件保留截止',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '服务器创建时间，UTC',
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '服务器更新时间，UTC',
  PRIMARY KEY (id),
  CONSTRAINT ck_import_export_job_1 CHECK (kind IN ('IMPORT','EXPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台批量任务';

ALTER TABLE org_unit ADD CONSTRAINT fk_org_unit_1 FOREIGN KEY (parent_id) REFERENCES org_unit (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_1 FOREIGN KEY (org_id) REFERENCES org_unit (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE user_role ADD CONSTRAINT fk_user_role_1 FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE user_role ADD CONSTRAINT fk_user_role_2 FOREIGN KEY (role_id) REFERENCES app_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE role_permission ADD CONSTRAINT fk_role_permission_1 FOREIGN KEY (role_id) REFERENCES app_role (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE user_scope ADD CONSTRAINT fk_user_scope_1 FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE user_scope ADD CONSTRAINT fk_user_scope_2 FOREIGN KEY (org_id) REFERENCES org_unit (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE user_scope ADD CONSTRAINT fk_user_scope_3 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE auth_session ADD CONSTRAINT fk_auth_session_1 FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE notification ADD CONSTRAINT fk_notification_1 FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE brand ADD CONSTRAINT fk_brand_1 FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE store ADD CONSTRAINT fk_store_1 FOREIGN KEY (customer_id) REFERENCES customer (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE store ADD CONSTRAINT fk_store_2 FOREIGN KEY (brand_id) REFERENCES brand (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE store ADD CONSTRAINT fk_store_3 FOREIGN KEY (org_id) REFERENCES org_unit (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE store ADD CONSTRAINT fk_store_4 FOREIGN KEY (owner_user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE store_area ADD CONSTRAINT fk_store_area_1 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE config_release ADD CONSTRAINT fk_config_release_1 FOREIGN KEY (published_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE offline_asset ADD CONSTRAINT fk_offline_asset_1 FOREIGN KEY (release_id) REFERENCES config_release (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_plan ADD CONSTRAINT fk_service_plan_1 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_plan ADD CONSTRAINT fk_service_plan_2 FOREIGN KEY (assignee_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_task ADD CONSTRAINT fk_service_task_1 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_task ADD CONSTRAINT fk_service_task_2 FOREIGN KEY (plan_id) REFERENCES service_plan (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_task ADD CONSTRAINT fk_service_task_3 FOREIGN KEY (assignee_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE task_change ADD CONSTRAINT fk_task_change_1 FOREIGN KEY (task_id) REFERENCES service_task (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE task_change ADD CONSTRAINT fk_task_change_2 FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE facility ADD CONSTRAINT fk_facility_1 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE facility ADD CONSTRAINT fk_facility_2 FOREIGN KEY (area_id,store_id) REFERENCES store_area (id,store_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE risk ADD CONSTRAINT fk_risk_1 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE risk ADD CONSTRAINT fk_risk_2 FOREIGN KEY (area_id,store_id) REFERENCES store_area (id,store_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE risk ADD CONSTRAINT fk_risk_3 FOREIGN KEY (followup_user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE store_assessment ADD CONSTRAINT fk_store_assessment_1 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE store_assessment ADD CONSTRAINT fk_store_assessment_2 FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE store_assessment ADD CONSTRAINT fk_store_assessment_3 FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE offline_context ADD CONSTRAINT fk_offline_context_1 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE offline_context ADD CONSTRAINT fk_offline_context_2 FOREIGN KEY (task_id) REFERENCES service_task (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE offline_context ADD CONSTRAINT fk_offline_context_3 FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service ADD CONSTRAINT fk_service_1 FOREIGN KEY (task_id,store_id) REFERENCES service_task (id,store_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service ADD CONSTRAINT fk_service_2 FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service ADD CONSTRAINT fk_service_3 FOREIGN KEY (context_id) REFERENCES offline_context (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service ADD CONSTRAINT fk_service_4 FOREIGN KEY (active_snapshot_id,id) REFERENCES service_snapshot (id,service_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service ADD CONSTRAINT fk_service_5 FOREIGN KEY (current_report_id,id) REFERENCES service_report (id,service_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_draft ADD CONSTRAINT fk_service_draft_1 FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_draft ADD CONSTRAINT fk_service_draft_2 FOREIGN KEY (context_id) REFERENCES offline_context (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_draft ADD CONSTRAINT fk_service_draft_3 FOREIGN KEY (correction_of_report_id) REFERENCES service_report (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_draft ADD CONSTRAINT fk_service_draft_4 FOREIGN KEY (saved_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE ledger_event ADD CONSTRAINT fk_ledger_event_1 FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE ledger_event ADD CONSTRAINT fk_ledger_event_2 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE ledger_event ADD CONSTRAINT fk_ledger_event_3 FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE ledger_event ADD CONSTRAINT fk_ledger_event_4 FOREIGN KEY (supersedes_event_id) REFERENCES ledger_event (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE risk_followup ADD CONSTRAINT fk_risk_followup_1 FOREIGN KEY (risk_id) REFERENCES risk (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE risk_followup ADD CONSTRAINT fk_risk_followup_2 FOREIGN KEY (ledger_event_id) REFERENCES ledger_event (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE risk_followup ADD CONSTRAINT fk_risk_followup_3 FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE field_commit ADD CONSTRAINT fk_field_commit_1 FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE attachment ADD CONSTRAINT fk_attachment_1 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE attachment ADD CONSTRAINT fk_attachment_2 FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE attachment ADD CONSTRAINT fk_attachment_3 FOREIGN KEY (context_id) REFERENCES offline_context (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE upload_session ADD CONSTRAINT fk_upload_session_1 FOREIGN KEY (attachment_id) REFERENCES attachment (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE upload_session ADD CONSTRAINT fk_upload_session_2 FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_snapshot ADD CONSTRAINT fk_service_snapshot_1 FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_snapshot ADD CONSTRAINT fk_service_snapshot_2 FOREIGN KEY (context_id) REFERENCES offline_context (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_snapshot ADD CONSTRAINT fk_service_snapshot_3 FOREIGN KEY (template_release_id) REFERENCES config_release (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE snapshot_attachment ADD CONSTRAINT fk_snapshot_attachment_1 FOREIGN KEY (snapshot_id) REFERENCES service_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE snapshot_attachment ADD CONSTRAINT fk_snapshot_attachment_2 FOREIGN KEY (attachment_id) REFERENCES attachment (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE preview_session ADD CONSTRAINT fk_preview_session_1 FOREIGN KEY (snapshot_id) REFERENCES service_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE preview_session ADD CONSTRAINT fk_preview_session_2 FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_signature ADD CONSTRAINT fk_service_signature_1 FOREIGN KEY (snapshot_id) REFERENCES service_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_signature ADD CONSTRAINT fk_service_signature_2 FOREIGN KEY (staff_user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_signature ADD CONSTRAINT fk_service_signature_3 FOREIGN KEY (staff_signature_id) REFERENCES service_signature (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_signature ADD CONSTRAINT fk_service_signature_4 FOREIGN KEY (preview_session_id) REFERENCES preview_session (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_signature ADD CONSTRAINT fk_service_signature_5 FOREIGN KEY (attachment_id) REFERENCES attachment (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_signature ADD CONSTRAINT fk_service_signature_6 FOREIGN KEY (supersedes_id) REFERENCES service_signature (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE confirmation_exception ADD CONSTRAINT fk_confirmation_exception_1 FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE confirmation_exception ADD CONSTRAINT fk_confirmation_exception_2 FOREIGN KEY (snapshot_id) REFERENCES service_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE confirmation_exception ADD CONSTRAINT fk_confirmation_exception_3 FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE offline_submission ADD CONSTRAINT fk_offline_submission_1 FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE offline_submission ADD CONSTRAINT fk_offline_submission_2 FOREIGN KEY (context_id) REFERENCES offline_context (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE sync_exception ADD CONSTRAINT fk_sync_exception_1 FOREIGN KEY (submission_id) REFERENCES offline_submission (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE sync_exception ADD CONSTRAINT fk_sync_exception_2 FOREIGN KEY (resolved_by) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE publication_job ADD CONSTRAINT fk_publication_job_1 FOREIGN KEY (snapshot_id,service_id) REFERENCES service_snapshot (id,service_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE publication_job ADD CONSTRAINT fk_publication_job_2 FOREIGN KEY (staff_signature_id) REFERENCES service_signature (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE publication_job ADD CONSTRAINT fk_publication_job_3 FOREIGN KEY (client_signature_id) REFERENCES service_signature (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE publication_job ADD CONSTRAINT fk_publication_job_4 FOREIGN KEY (report_id) REFERENCES service_report (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_report ADD CONSTRAINT fk_service_report_1 FOREIGN KEY (snapshot_id,service_id) REFERENCES service_snapshot (id,service_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_report ADD CONSTRAINT fk_service_report_2 FOREIGN KEY (staff_signature_id) REFERENCES service_signature (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_report ADD CONSTRAINT fk_service_report_3 FOREIGN KEY (client_signature_id) REFERENCES service_signature (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE service_report ADD CONSTRAINT fk_service_report_4 FOREIGN KEY (supersedes_report_id) REFERENCES service_report (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE snapshot_finding ADD CONSTRAINT fk_snapshot_finding_1 FOREIGN KEY (snapshot_id) REFERENCES service_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE snapshot_finding ADD CONSTRAINT fk_snapshot_finding_2 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE snapshot_finding ADD CONSTRAINT fk_snapshot_finding_3 FOREIGN KEY (facility_id,store_id) REFERENCES facility (id,store_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE snapshot_inspection ADD CONSTRAINT fk_snapshot_inspection_1 FOREIGN KEY (snapshot_id) REFERENCES service_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE snapshot_inspection ADD CONSTRAINT fk_snapshot_inspection_2 FOREIGN KEY (facility_id,store_id) REFERENCES facility (id,store_id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE snapshot_treatment ADD CONSTRAINT fk_snapshot_treatment_1 FOREIGN KEY (snapshot_id) REFERENCES service_snapshot (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE idempotency_record ADD CONSTRAINT fk_idempotency_record_1 FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_1 FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE audit_log ADD CONSTRAINT fk_audit_log_2 FOREIGN KEY (store_id) REFERENCES store (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE import_export_job ADD CONSTRAINT fk_import_export_job_1 FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
