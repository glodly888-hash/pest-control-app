-- 开发种子数据（仅本地，不用于生产）
-- 用途：跑通登录 → 门店列表最小链路

-- 1. 客户主体（companyCode = SWSTEST）
INSERT INTO customer (id, code, name, status) VALUES
  (UNHEX(REPLACE('10000000-0000-0000-0000-000000000001','-','')), 'SWSTEST', '测试客户', 'ACTIVE');

-- 2. 组织（区域/团队）
INSERT INTO org_unit (id, code, name, kind, status) VALUES
  (UNHEX(REPLACE('20000000-0000-0000-0000-000000000001','-','')), 'ORG001', '杭州区域', 'REGION', 'ACTIVE');

-- 3. 员工（account = employee01，密码 = password）
-- password_hash 是 BCrypt("password") 的哈希（rounds=10，已验证）
INSERT INTO app_user (id, org_id, login_name, password_hash, name, status, permission_version) VALUES
  (UNHEX(REPLACE('30000000-0000-0000-0000-000000000001','-','')),
   UNHEX(REPLACE('20000000-0000-0000-0000-000000000001','-','')),
   'employee01',
   '$2a$10$ZKGdZRQ7FEBoPgtWkTFlk.WrEvI0Ll.WxBRYlLmVjGyK6qDoJgmA.',
   '测试员工',
   'ACTIVE',
   1);

-- 4. 品牌
INSERT INTO brand (id, customer_id, name, status) VALUES
  (UNHEX(REPLACE('40000000-0000-0000-0000-000000000001','-','')),
   UNHEX(REPLACE('10000000-0000-0000-0000-000000000001','-','')),
   '测试品牌', 'ACTIVE');

-- 5. 门店
INSERT INTO store (id, customer_id, brand_id, org_id, code, name, address, timezone, status) VALUES
  (UNHEX(REPLACE('50000000-0000-0000-0000-000000000001','-','')),
   UNHEX(REPLACE('10000000-0000-0000-0000-000000000001','-','')),
   UNHEX(REPLACE('40000000-0000-0000-0000-000000000001','-','')),
   UNHEX(REPLACE('20000000-0000-0000-0000-000000000001','-','')),
   'STORE001', '测试门店一号', '杭州市滨江区测试路 1 号', 'Asia/Shanghai', 'ACTIVE');

-- 6. 员工数据范围（授权到门店）
INSERT INTO user_scope (id, user_id, scope_kind, store_id) VALUES
  (UNHEX(REPLACE('60000000-0000-0000-0000-000000000001','-','')),
   UNHEX(REPLACE('30000000-0000-0000-0000-000000000001','-','')),
   'STORE',
   UNHEX(REPLACE('50000000-0000-0000-0000-000000000001','-','')));

-- 7. 服务任务（employee01 的三条任务，覆盖不同日期/状态，验证 listTasks 过滤与分页）
INSERT INTO service_task (id, store_id, plan_id, occurrence_key, assignee_id,
                          service_type, planned_date, planned_start_at, planned_end_at,
                          status, assignment_version, revision, creation_source, instructions) VALUES
  (UNHEX(REPLACE('70000000-0000-0000-0000-000000000001','-','')),
   UNHEX(REPLACE('50000000-0000-0000-0000-000000000001','-','')),
   NULL, NULL,
   UNHEX(REPLACE('30000000-0000-0000-0000-000000000001','-','')),
   'ROUTINE', '2026-11-13', '2026-11-13 09:00:00', '2026-11-13 10:00:00',
   'PENDING', 1, 1, 'PLAN', '常规巡检：重点检查后厨与仓库'),
  (UNHEX(REPLACE('70000000-0000-0000-0000-000000000002','-','')),
   UNHEX(REPLACE('50000000-0000-0000-0000-000000000001','-','')),
   NULL, NULL,
   UNHEX(REPLACE('30000000-0000-0000-0000-000000000001','-','')),
   'EMERGENCY', '2026-11-12', '2026-11-12 15:00:00', NULL,
   'IN_PROGRESS', 1, 1, 'EMPLOYEE_EMERGENCY', '应急：发现鼠迹，需立即处理'),
  (UNHEX(REPLACE('70000000-0000-0000-0000-000000000003','-','')),
   UNHEX(REPLACE('50000000-0000-0000-0000-000000000001','-','')),
   NULL, NULL,
   UNHEX(REPLACE('30000000-0000-0000-0000-000000000001','-','')),
   'RECHECK', '2026-11-10', '2026-11-10 09:00:00', '2026-11-10 10:00:00',
   'COMPLETED', 1, 1, 'PLAN', '复查：确认上次整改是否到位');
