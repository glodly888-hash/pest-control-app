-- 设计示例：供 Java NamedParameterJdbcTemplate / JdbcClient 绑定参数使用。
-- 不是 Flyway 迁移，不能直接整份运行。每段先执行 ScopePolicy 数据范围检查。
-- 二进制UUID参数由Java转换为16字节；不把客户端传入的scope列表视为授权。

-- 1. 草稿乐观锁：应用须先锁task/service并确认EDITABLE和当前派工。
UPDATE service_draft
SET content_json = CAST(:contentJson AS JSON),
    revision = revision + 1, saved_by = :actorId
WHERE service_id = :serviceId AND revision = :expectedRevision;
-- 必须检查 affectedRows=1；否则返回412并保留客户端草稿。

-- 2. 队列领取，独立短事务。处理过期租约由回收器按相同锁协议更新。
START TRANSACTION;
SELECT id, kind, business_id, fencing_token
FROM job_queue
WHERE status = 'READY' AND next_run_at <= UTC_TIMESTAMP(6)
ORDER BY next_run_at, id
LIMIT 1 FOR UPDATE SKIP LOCKED;
-- 把刚读出的id绑定到jobId，服务端生成owner和leaseSeconds。
UPDATE job_queue
SET status='RUNNING', lease_owner=:owner,
    lease_until=TIMESTAMPADD(SECOND,:leaseSeconds,UTC_TIMESTAMP(6)),
    fencing_token=fencing_token+1, attempts=attempts+1
WHERE id=:jobId AND status='READY';
SELECT fencing_token FROM job_queue WHERE id=:jobId;
COMMIT;
-- 渲染在事务之外；后续写入必须携带本次token，不读取新token冒用。

-- 3. 租约心跳，失去租约后主动放弃发布。
UPDATE job_queue
SET lease_until=TIMESTAMPADD(SECOND,:leaseSeconds,UTC_TIMESTAMP(6))
WHERE id=:jobId AND status='RUNNING' AND lease_owner=:owner
  AND fencing_token=:token AND lease_until>UTC_TIMESTAMP(6);

-- 4. 过期租约回收：先锁具体行，再READY或DEAD；旧worker因此失去提交资格。
START TRANSACTION;
SELECT id, attempts, max_attempts FROM job_queue
WHERE status='RUNNING' AND lease_until<=UTC_TIMESTAMP(6)
ORDER BY lease_until,id LIMIT 1 FOR UPDATE SKIP LOCKED;
UPDATE job_queue
SET status=IF(attempts>=max_attempts,'DEAD','READY'),
    next_run_at=TIMESTAMPADD(SECOND,:backoffSeconds,UTC_TIMESTAMP(6)),
    lease_owner=NULL,lease_until=NULL,fencing_token=fencing_token+1
WHERE id=:jobId AND status='RUNNING' AND lease_until<=UTC_TIMESTAMP(6);
COMMIT;
-- DEAD后同步更新对应业务任务失败状态及审计；用可重复执行的协调任务补偿。

-- 5. 发布最终事务锁顺序示意；只展示锁，不省略应用层检查。
START TRANSACTION;
SELECT id,status,assignee_id,assignment_version FROM service_task
WHERE id=:taskId FOR UPDATE;
SELECT id,head_revision,active_snapshot_id,current_report_id FROM service
WHERE id=:serviceId FOR UPDATE;
SELECT id,invalidated_at,content_hash FROM service_snapshot
WHERE id=:snapshotId AND service_id=:serviceId FOR UPDATE;
SELECT id,role,invalidated_at,content_hash,staff_signature_id FROM service_signature
WHERE id IN (:staffSignatureId,:clientSignatureId) ORDER BY id FOR UPDATE;
SELECT id,status,head_revision FROM publication_job WHERE id=:publicationJobId FOR UPDATE;
SELECT id,status,lease_owner,lease_until,fencing_token FROM job_queue WHERE id=:jobId FOR UPDATE;
-- 应用校验所有预期值和附件，确认对象已成功写入且哈希匹配。
-- 仅校验通过后：旧版SUPERSEDED -> 插新report -> 切service.current_report_id
-- -> task.COMPLETED -> job和publication_job成功 -> audit/notification，随后COMMIT。
-- 任一校验失败ROLLBACK；本段为设计示意，不包含可单独执行的发布动作。
ROLLBACK;

-- 6. 当前正式报告的来源统计；避免更正前后重复计算。
-- permittedStoreIds必须来自服务端权限策略；时间为门店业务日对应UTC区间。
SELECT f.source,f.pest_type_id,
       SUM(CASE WHEN f.quantity_state='EXACT' THEN f.quantity ELSE NULL END) AS exact_quantity,
       SUM(CASE WHEN f.quantity_state='UNKNOWN' THEN 1 ELSE 0 END) AS unknown_records
FROM service s
JOIN service_report r ON r.id=s.current_report_id AND r.service_id=s.id AND r.status='PUBLISHED'
JOIN snapshot_finding f ON f.snapshot_id=r.snapshot_id AND f.store_id=s.store_id
WHERE s.store_id IN (:permittedStoreIds)
  AND r.service_started_at>=:startUtc AND r.service_started_at<:endUtc
GROUP BY f.source,f.pest_type_id;
-- exact_quantity=NULL表示无明确数量；不应显示为捕获0。
-- 不生成跨source汇总；同Inspection.id的不同虫种用不同统计行。

-- 7. 设施检查状态分列；分母规则还需结合快照的应检查设施清单。
SELECT i.check_result,COUNT(*) AS record_count
FROM service s
JOIN service_report r ON r.id=s.current_report_id AND r.service_id=s.id AND r.status='PUBLISHED'
JOIN snapshot_inspection i ON i.snapshot_id=r.snapshot_id AND i.store_id=s.store_id
WHERE s.store_id IN (:permittedStoreIds)
  AND r.service_started_at>=:startUtc AND r.service_started_at<:endUtc
GROUP BY i.check_result;
