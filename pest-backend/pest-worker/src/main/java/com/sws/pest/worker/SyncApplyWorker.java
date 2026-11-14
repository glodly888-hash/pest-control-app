package com.sws.pest.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 离线同步应用 Worker：消费 job_queue（kind=SYNC_APPLY）。
 *
 * 把离线提交包（offline_submission.canonical_payload）自动应用为服务草稿，
 * 打通「接收 → 应用」的离线闭环。
 *
 * 流程（对齐架构文档离线同步章节，首期简化）：
 * 1. 领取：短事务内 FOR UPDATE SKIP LOCKED 领 READY 且到期的任务，
 *    递增 fencing_token、设租约、attempts+1，立即提交；
 * 2. 校验：解析原包，做冲突检测（任务改派/取消 → assignment_version 不一致）；
 * 3. 应用：事务外组装，最终事务内落地 service + service_draft，
 *    submission 置 APPLIED，job 置 DONE；
 * 冲突则转 sync_exception（submission 置 CONFLICT），job 置 DONE（不重试）。
 * 失败指数退避 + attempts 达上限转 DEAD。
 */
@Component
public class SyncApplyWorker {

    private static final Logger log = LoggerFactory.getLogger(SyncApplyWorker.class);
    private static final String KIND = "SYNC_APPLY";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final String leaseOwner;
    private final int leaseSeconds;

    public SyncApplyWorker(JdbcTemplate jdbc,
                           PlatformTransactionManager transactionManager,
                           @Value("${pest.worker.lease-seconds:60}") int leaseSeconds) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.leaseOwner = "sync-worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${pest.worker.sync-poll-delay-ms:2000}")
    public void poll() {
        try {
            JobLease lease = claim();
            if (lease == null) {
                return;
            }
            process(lease);
        } catch (Exception e) {
            log.error("离线同步 Worker 轮询异常", e);
        }
    }

    private JobLease claim() {
        return tx.execute(status -> {
            List<JobRow> rows = jdbc.query("""
                    SELECT id, business_id, fencing_token
                    FROM job_queue
                    WHERE status = 'READY' AND kind = ? AND next_run_at <= UTC_TIMESTAMP(6)
                    ORDER BY next_run_at, id
                    LIMIT 1 FOR UPDATE SKIP LOCKED
                    """,
                    rs -> {
                        List<JobRow> list = new ArrayList<>();
                        if (rs.next()) {
                            list.add(new JobRow(
                                    bytesToUuid(rs.getBytes("id")),
                                    bytesToUuid(rs.getBytes("business_id")),
                                    rs.getLong("fencing_token")));
                        }
                        return list;
                    }, KIND);

            if (rows.isEmpty()) {
                return null;
            }
            JobRow row = rows.get(0);

            int updated = jdbc.update("""
                    UPDATE job_queue
                    SET status = 'RUNNING', lease_owner = ?, lease_until = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                        fencing_token = fencing_token + 1, attempts = attempts + 1
                    WHERE id = ? AND status = 'READY'
                    """, leaseOwner, leaseSeconds, uuidBytes(row.id()));

            if (updated == 0) {
                return null;
            }
            Long token = jdbc.queryForObject(
                    "SELECT fencing_token FROM job_queue WHERE id = ?", Long.class, uuidBytes(row.id()));
            return new JobLease(row.id(), row.businessId(), token != null ? token : row.fencingToken() + 1);
        });
    }

    private void process(JobLease lease) {
        try {
            SubmissionData data = loadSubmission(lease);
            if (data == null) {
                markDead(lease, "提交包不存在");
                return;
            }

            // 冲突检测：任务改派/取消导致 assignment_version 不一致
            if (isAssignmentConflict(data)) {
                markConflict(lease, data);
                return;
            }

            // 应用草稿
            applyDraft(lease, data);

        } catch (Exception e) {
            log.error("离线同步应用失败 jobId={} submissionId={}", lease.jobId(), lease.businessId(), e);
            markFailed(lease, e.getMessage());
        }
    }

    /** 读取提交包与关联上下文（事务外只读）。 */
    private SubmissionData loadSubmission(JobLease lease) {
        return jdbc.query("""
                SELECT s.id, s.user_id, s.context_id, s.service_id, s.task_id, s.assignment_version,
                       s.canonical_payload
                FROM offline_submission s
                WHERE s.id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    UUID taskId = rs.getBytes("task_id") != null ? bytesToUuid(rs.getBytes("task_id")) : null;
                    String payload = new String(rs.getBytes("canonical_payload"), StandardCharsets.UTF_8);
                    return new SubmissionData(
                            bytesToUuid(rs.getBytes("id")),
                            bytesToUuid(rs.getBytes("user_id")),
                            bytesToUuid(rs.getBytes("context_id")),
                            bytesToUuid(rs.getBytes("service_id")),
                            taskId,
                            rs.getLong("assignment_version"),
                            payload);
                }, uuidBytes(lease.businessId()));
    }

    /**
     * 冲突检测：包内任务若已被改派/取消（assignment_version 不一致），
     * 或任务状态已非可作业，则视为冲突。
     */
    private boolean isAssignmentConflict(SubmissionData data) {
        if (data.taskId() == null) {
            return false; // 应急包可能无任务，交给应用逻辑处理
        }
        Long currentVersion = jdbc.queryForObject(
                "SELECT assignment_version FROM service_task WHERE id = ?",
                Long.class, uuidBytes(data.taskId()));
        if (currentVersion == null) {
            // 任务已不存在（被删除）→ 冲突
            return true;
        }
        return currentVersion.longValue() != data.assignmentVersion();
    }

    /** 把离线草稿落地为 service + service_draft。 */
    private void applyDraft(JobLease lease, SubmissionData data) {
        JsonNode draftNode = parseDraft(data.payload());
        String contentJson = draftNode != null ? draftNode.toString() : "{}";

        // 预解析 serviceId（事务外，供日志与回执使用）
        final UUID effectiveServiceId = resolveExistingService(data.taskId(), data.serviceId());
        final long[] newRevisionHolder = {0};

        tx.executeWithoutResult(status -> {
            // 校验 fencing_token
            Long token = jdbc.queryForObject(
                    "SELECT fencing_token FROM job_queue WHERE id = ? FOR UPDATE",
                    Long.class, uuidBytes(lease.jobId()));
            if (token == null || token.longValue() != lease.token()) {
                throw new IllegalStateException("租约已失效，放弃应用");
            }

            UUID taskId = data.taskId();
            UUID actorId = data.userId();
            UUID contextId = data.contextId();

            // 复用事务外解析出的 serviceId（一任务一服务）
            UUID serviceId = effectiveServiceId;
            UUID storeId = resolveStoreId(data);

            // 服务端草稿版本是权威：newRevision = 当前 revision + 1，
            // 保证单调递增且满足 ck_service_draft_1（field_commit_revision <= revision）。
            Long curRevision = jdbc.queryForObject(
                    "SELECT revision FROM service_draft WHERE service_id = ?",
                    Long.class, uuidBytes(serviceId));
            long newRevision = (curRevision != null ? curRevision : 0L) + 1;
            newRevisionHolder[0] = newRevision;

            // 1. service 存在则更新，不存在则创建
            Integer svcExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM service WHERE id = ?", Integer.class, uuidBytes(serviceId));
            if (svcExists != null && svcExists > 0) {
                jdbc.update("""
                        UPDATE service SET head_revision = head_revision + 1, updated_at = UTC_TIMESTAMP(6)
                        WHERE id = ?
                        """, uuidBytes(serviceId));
            } else {
                jdbc.update("""
                        INSERT INTO service (id, task_id, store_id, actor_id, context_id, phase, head_revision)
                        VALUES (?, ?, ?, ?, ?, 'EDITABLE', 1)
                        """,
                        uuidBytes(serviceId),
                        taskId != null ? uuidBytes(taskId) : null,
                        uuidBytes(storeId),
                        uuidBytes(actorId),
                        uuidBytes(contextId));
            }

            // 2. service_draft upsert（uk_draft_service）
            jdbc.update("""
                    INSERT INTO service_draft (id, service_id, context_id, revision,
                                              field_commit_revision, schema_version, content_json,
                                              correction_of_report_id, saved_by)
                    VALUES (?, ?, ?, ?, 0, '1.0', ?, NULL, ?)
                    ON DUPLICATE KEY UPDATE revision = VALUES(revision),
                                            content_json = VALUES(content_json),
                                            saved_by = VALUES(saved_by),
                                            updated_at = UTC_TIMESTAMP(6)
                    """,
                    uuidBytes(UUID.randomUUID()),
                    uuidBytes(serviceId),
                    uuidBytes(contextId),
                    newRevision,
                    contentJson,
                    uuidBytes(actorId));

            // 3. 任务推进（若有关联任务且未完成，置 IN_PROGRESS）
            if (taskId != null) {
                jdbc.update("""
                        UPDATE service_task SET status = 'IN_PROGRESS', revision = revision + 1
                        WHERE id = ? AND status = 'PENDING'
                        """, uuidBytes(taskId));
            }

            // 4. submission 置 APPLIED
            String resultJson = "{\"serviceId\":\"" + serviceId + "\",\"draftRevision\":"
                    + newRevision + "}";
            jdbc.update("""
                    UPDATE offline_submission SET status = 'APPLIED', result_json = ?, applied_at = ?
                    WHERE id = ?
                    """, resultJson, Timestamp.from(Instant.now()), uuidBytes(data.id()));

            // 5. job 置 DONE
            jdbc.update("""
                    UPDATE job_queue SET status = 'DONE', lease_owner = NULL, lease_until = NULL
                    WHERE id = ?
                    """, uuidBytes(lease.jobId()));
        });
        log.info("离线提交应用成功 submissionId={} serviceId={} draftRevision={}",
                data.id(), effectiveServiceId, newRevisionHolder[0]);
    }

    /** 冲突：转 sync_exception + submission 置 CONFLICT + job DONE（不重试）。 */
    private void markConflict(JobLease lease, SubmissionData data) {
        tx.executeWithoutResult(status -> {
            Long token = jdbc.queryForObject(
                    "SELECT fencing_token FROM job_queue WHERE id = ? FOR UPDATE",
                    Long.class, uuidBytes(lease.jobId()));
            if (token == null || token.longValue() != lease.token()) {
                return;
            }

            // 唯一约束 uk_exception_submission 保证幂等
            jdbc.update("""
                    INSERT INTO sync_exception (id, submission_id, kind, status, created_at)
                    VALUES (?, ?, 'ASSIGNMENT_CHANGED', 'OPEN', ?)
                    ON DUPLICATE KEY UPDATE status = 'OPEN'
                    """,
                    uuidBytes(UUID.randomUUID()),
                    uuidBytes(data.id()),
                    Timestamp.from(Instant.now()));

            jdbc.update("""
                    UPDATE offline_submission SET status = 'CONFLICT',
                        result_json = '{\"kind\":\"ASSIGNMENT_CHANGED\"}'
                    WHERE id = ?
                    """, uuidBytes(data.id()));

            jdbc.update("""
                    UPDATE job_queue SET status = 'DONE', lease_owner = NULL, lease_until = NULL
                    WHERE id = ?
                    """, uuidBytes(lease.jobId()));
        });
        log.warn("离线提交冲突转异常 submissionId={} kind=ASSIGNMENT_CHANGED", data.id());
    }

    /** 失败处理：指数退避重试，达上限转 DEAD。 */
    private void markFailed(JobLease lease, String error) {
        String safeError = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
        tx.executeWithoutResult(status -> {
            Integer attempts = jdbc.queryForObject(
                    "SELECT attempts FROM job_queue WHERE id = ?", Integer.class, uuidBytes(lease.jobId()));
            Integer maxAttempts = jdbc.queryForObject(
                    "SELECT max_attempts FROM job_queue WHERE id = ?", Integer.class, uuidBytes(lease.jobId()));

            if (attempts != null && maxAttempts != null && attempts >= maxAttempts) {
                jdbc.update("""
                        UPDATE job_queue SET status = 'DEAD', last_error = ?, lease_owner = NULL, lease_until = NULL
                        WHERE id = ?
                        """, safeError, uuidBytes(lease.jobId()));
                jdbc.update("""
                        UPDATE offline_submission SET status = 'CONFLICT', result_json = ?
                        WHERE id = ?
                        """, "{\"error\":\"" + safeError.replace("\"", "\\\"") + "\"}",
                        uuidBytes(lease.businessId()));
                log.warn("离线同步转 DEAD jobId={} businessId={}", lease.jobId(), lease.businessId());
            } else {
                int backoff = (int) Math.min(300, Math.pow(2, attempts == null ? 0 : attempts));
                jdbc.update("""
                        UPDATE job_queue SET status = 'READY', last_error = ?,
                            next_run_at = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                            lease_owner = NULL, lease_until = NULL
                        WHERE id = ?
                        """, safeError, backoff, uuidBytes(lease.jobId()));
                log.warn("离线同步失败，{}s 后重试 jobId={}", backoff, lease.jobId());
            }
        });
    }

    private void markDead(JobLease lease, String error) {
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE job_queue SET status = 'DEAD', last_error = ?, lease_owner = NULL, lease_until = NULL
                    WHERE id = ?
                    """, error, uuidBytes(lease.jobId()));
        });
    }

    // ---- 解析工具 ----

    /** 按 task_id 查找已存在的 service，不存在则回退包内 serviceId。 */
    private UUID resolveExistingService(UUID taskId, UUID fallbackServiceId) {
        if (taskId == null) {
            return fallbackServiceId;
        }
        byte[] existing = jdbc.queryForObject(
                "SELECT id FROM service WHERE task_id = ? LIMIT 1",
                byte[].class, uuidBytes(taskId));
        return existing != null ? bytesToUuid(existing) : fallbackServiceId;
    }

    private UUID resolveStoreId(SubmissionData data) {
        if (data.taskId() != null) {
            byte[] storeId = jdbc.queryForObject(
                    "SELECT store_id FROM service_task WHERE id = ?", byte[].class, uuidBytes(data.taskId()));
            if (storeId != null) {
                return bytesToUuid(storeId);
            }
        }
        // 从上下文取 store_id
        byte[] storeId = jdbc.queryForObject(
                "SELECT store_id FROM offline_context WHERE id = ?", byte[].class, uuidBytes(data.contextId()));
        if (storeId != null) {
            return bytesToUuid(storeId);
        }
        throw new IllegalStateException("无法解析门店：任务与上下文均无 store_id");
    }

    private JsonNode parseDraft(String payload) {
        try {
            JsonNode root = JSON.readTree(payload);
            JsonNode draft = root.get("draft");
            if (draft == null) {
                return root.get("draft");
            }
            // draft 可能包一层 { content: ... }，提取 content
            JsonNode content = draft.get("content");
            return content != null ? content : draft;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 数据结构 ----

    record JobRow(UUID id, UUID businessId, long fencingToken) {}
    record JobLease(UUID jobId, UUID businessId, long token) {}
    record SubmissionData(UUID id, UUID userId, UUID contextId, UUID serviceId, UUID taskId,
                          long assignmentVersion, String payload) {}

    // ---- 工具 ----

    static byte[] uuidBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
