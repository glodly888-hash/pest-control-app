package com.sws.pest.infrastructure.reporting;

import com.sws.pest.domain.reporting.PublicationJob;
import com.sws.pest.domain.reporting.PublicationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * publication_job 的 JDBC 实现。
 *
 * create 在事务内插入 QUEUED 任务，并将 service.phase 置 PUBLISHING。
 * 实际 PDF 渲染与 service_report 生成由 worker 异步消费 job_queue 完成。
 */
@Repository
public class JdbcPublicationRepository implements PublicationRepository {

    private final JdbcTemplate jdbc;

    public JdbcPublicationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PublicationJob> findBySnapshot(UUID serviceId, UUID snapshotId) {
        String sql = """
                SELECT id, service_id, snapshot_id, staff_signature_id, client_signature_id,
                       status, error_code, error_message, report_id, created_at, updated_at
                FROM publication_job
                WHERE service_id = ? AND snapshot_id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<PublicationJob>empty();
            }
            return Optional.of(mapJob(rs));
        }, uuidBytes(serviceId), uuidBytes(snapshotId));
    }

    @Override
    public Optional<PublicationJob> findById(UUID jobId) {
        String sql = """
                SELECT id, service_id, snapshot_id, staff_signature_id, client_signature_id,
                       status, error_code, error_message, report_id, created_at, updated_at
                FROM publication_job
                WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<PublicationJob>empty();
            }
            return Optional.of(mapJob(rs));
        }, uuidBytes(jobId));
    }

    @Override
    @Transactional
    public PublicationJob create(PublicationJob job) {
        jdbc.update("""
                INSERT INTO publication_job (id, service_id, snapshot_id, head_revision,
                                            staff_signature_id, client_signature_id, status, attempt_no)
                VALUES (?, ?, ?, 1, ?, ?, 'QUEUED', 0)
                """,
                uuidBytes(job.id()),
                uuidBytes(job.serviceId()),
                uuidBytes(job.snapshotId()),
                uuidBytes(job.staffSignatureId()),
                uuidBytes(job.clientSignatureId()));

        // service.phase → PUBLISHING（仅当仍为 FROZEN/AWAITING_CONFIRMATION）
        jdbc.update("""
                UPDATE service SET phase = 'PUBLISHING'
                WHERE id = ? AND phase IN ('FROZEN', 'AWAITING_CONFIRMATION')
                """, uuidBytes(job.serviceId()));

        // 事务发件箱：入队 REPORT_RENDER，业务 ID 复用 publication_job.id（唯一约束 uk_job_business 兜底幂等）
        enqueue(job.id(), job.serviceId());

        return findById(job.id()).orElse(job);
    }

    @Override
    @Transactional
    public PublicationJob retry(UUID jobId) {
        // 仅 FAILED/BLOCKED 且 canRetry 可重试；复用原快照与版本
        int updated = jdbc.update("""
                UPDATE publication_job
                SET status = 'QUEUED', attempt_no = attempt_no + 1,
                    error_code = NULL, error_message = NULL, updated_at = NOW(6)
                WHERE id = ? AND status IN ('FAILED', 'BLOCKED')
                """, uuidBytes(jobId));

        if (updated == 0) {
            throw new IllegalStateException("任务不可重试：仅失败任务可重试");
        }

        PublicationJob job = findById(jobId)
                .orElseThrow(() -> new IllegalStateException("发布任务不存在"));

        // 重新入队（同 business_id 的 job_queue 若已 DEAD 则复位为 READY，否则幂等）
        enqueue(jobId, job.serviceId());

        return job;
    }

    /**
     * 幂等入队 REPORT_RENDER：business_id = publication_job.id。
     * 已存在（含 DEAD）则复位为 READY，避免重试后无任务可领取。
     */
    private void enqueue(UUID publicationJobId, UUID serviceId) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM job_queue WHERE kind = 'REPORT_RENDER' AND business_id = ?",
                Integer.class, uuidBytes(publicationJobId));
        if (exists != null && exists > 0) {
            jdbc.update("""
                    UPDATE job_queue
                    SET status = 'READY', next_run_at = NOW(6), lease_owner = NULL,
                        lease_until = NULL, last_error = NULL, updated_at = NOW(6)
                    WHERE kind = 'REPORT_RENDER' AND business_id = ?
                    """, uuidBytes(publicationJobId));
            return;
        }
        // payload_json 存最小引用（serviceId + jobId），禁止放二进制或完整正文
        String payload = "{\"publicationJobId\":\"" + publicationJobId
                + "\",\"serviceId\":\"" + serviceId + "\"}";
        jdbc.update("""
                INSERT INTO job_queue (id, kind, business_id, status, payload_json,
                                       attempts, max_attempts, next_run_at)
                VALUES (?, 'REPORT_RENDER', ?, 'READY', ?, 0, 5, NOW(6))
                """,
                uuidBytes(UUID.randomUUID()),
                uuidBytes(publicationJobId),
                payload);
    }

    private static PublicationJob mapJob(ResultSet rs) throws SQLException {
        return new PublicationJob(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("service_id")),
                bytesToUuid(rs.getBytes("snapshot_id")),
                bytesToUuid(rs.getBytes("staff_signature_id")),
                bytesToUuid(rs.getBytes("client_signature_id")),
                PublicationJob.Status.valueOf(rs.getString("status")),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getBytes("report_id") != null ? bytesToUuid(rs.getBytes("report_id")) : null,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

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
