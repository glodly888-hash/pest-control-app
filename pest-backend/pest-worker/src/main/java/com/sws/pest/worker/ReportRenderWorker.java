package com.sws.pest.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 报告渲染 Worker：消费 job_queue（kind=REPORT_RENDER）。
 *
 * 流程（对齐架构文档 7.1 / 关键事务与查询.sql）：
 * 1. 领取：短事务内 FOR UPDATE SKIP LOCKED 领 READY 且到期的任务，
 *    递增 fencing_token、设租约 owner/lease_until、attempts+1，立即提交；
 * 2. 渲染：事务之外读取快照正文 + 签署凭证，生成 PDF 并写本地文件系统，
 *    重算 sha256（先确认对象可读、大小与摘要一致）；
 * 3. 提交：最终事务内校验 fencing_token 后，插入 service_report、
 *    切换 service.current_report_id、task 置 COMPLETED、publication_job SUCCEEDED、
 *    job_queue DONE。
 * 失败指数退避 + attempts 达上限转 DEAD，publication_job 记 FAILED。
 */
@Component
public class ReportRenderWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportRenderWorker.class);

    private static final String KIND = "REPORT_RENDER";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ReportRenderer renderer;
    private final Path reportRoot;
    private final String leaseOwner;
    private final int leaseSeconds;

    public ReportRenderWorker(JdbcTemplate jdbc,
                              PlatformTransactionManager transactionManager,
                              ReportRenderer renderer,
                              @Value("${pest.media.report-root:/tmp/pest-media}") String reportRoot,
                              @Value("${pest.worker.lease-seconds:60}") int leaseSeconds) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.renderer = renderer;
        this.reportRoot = Paths.get(reportRoot).toAbsolutePath().normalize();
        this.leaseOwner = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        this.leaseSeconds = leaseSeconds;
    }

    /**
     * 轮询领取并处理一个 REPORT_RENDER 任务。
     * 首期用固定频率轮询（单进程），后续可改多线程池并发。
     */
    @Scheduled(fixedDelayString = "${pest.worker.poll-delay-ms:2000}")
    public void poll() {
        try {
            JobLease lease = claim();
            if (lease == null) {
                return;
            }
            process(lease);
        } catch (Exception e) {
            // 单任务异常不终止轮询；领取失败下轮重试
            log.error("Worker 轮询异常", e);
        }
    }

    /** 领取一个到期任务（短事务 + SKIP LOCKED）。 */
    private JobLease claim() {
        return tx.execute(status -> {
            List<JobRow> rows = jdbc.query("""
                    SELECT id, kind, business_id, fencing_token
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
                                    rs.getString("kind"),
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
                return null; // 被并发抢走
            }
            Long token = jdbc.queryForObject(
                    "SELECT fencing_token FROM job_queue WHERE id = ?", Long.class, uuidBytes(row.id()));
            return new JobLease(row.id(), row.businessId(), token != null ? token : row.fencingToken() + 1);
        });
    }

    /** 处理任务：渲染 + 最终事务。 */
    private void process(JobLease lease) {
        try {
            // 1. 读取发布任务与快照数据（事务外，只读）
            PublicationData data = loadData(lease);

            // 2. 渲染 PDF
            ReportRenderer.RenderedPdf pdf = renderPdf(data);

            // 3. 写文件（先落盘，确认可读、大小与摘要一致）
            writePdf(lease, pdf);

            // 4. 最终事务：校验 fencing_token + 发布
            publish(lease, data, pdf);

        } catch (Exception e) {
            log.error("报告渲染失败 jobId={}", lease.jobId(), e);
            markFailed(lease, e.getMessage());
        }
    }

    private PublicationData loadData(JobLease lease) {
        // 发布任务 → 快照 → 服务 → 门店 → 签署
        PublicationRow pub = jdbc.query("""
                SELECT p.id, p.service_id, p.snapshot_id, p.staff_signature_id, p.client_signature_id
                FROM publication_job p
                WHERE p.id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return new PublicationRow(
                            bytesToUuid(rs.getBytes("id")),
                            bytesToUuid(rs.getBytes("service_id")),
                            bytesToUuid(rs.getBytes("snapshot_id")),
                            bytesToUuid(rs.getBytes("staff_signature_id")),
                            bytesToUuid(rs.getBytes("client_signature_id")));
                }, uuidBytes(lease.businessId()));

        if (pub == null) {
            throw new IllegalStateException("发布任务不存在");
        }

        // 服务 + 门店
        ServiceRow svc = jdbc.query("""
                SELECT s.id, s.task_id, s.store_id, s.head_revision, s.service_started_at,
                       s.service_ended_at, st.name AS store_name
                FROM service s
                JOIN store st ON st.id = s.store_id
                WHERE s.id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return new ServiceRow(
                            bytesToUuid(rs.getBytes("id")),
                            bytesToUuid(rs.getBytes("task_id")),
                            bytesToUuid(rs.getBytes("store_id")),
                            rs.getLong("head_revision"),
                            rs.getTimestamp("service_started_at") != null ? rs.getTimestamp("service_started_at").toInstant() : null,
                            rs.getTimestamp("service_ended_at") != null ? rs.getTimestamp("service_ended_at").toInstant() : null,
                            rs.getString("store_name"));
                }, uuidBytes(pub.serviceId()));

        if (svc == null) {
            throw new IllegalStateException("服务不存在");
        }

        // 快照正文
        String contentJson = jdbc.queryForObject(
                "SELECT payload_json FROM service_snapshot WHERE id = ?",
                String.class, uuidBytes(pub.snapshotId()));

        // 签署凭证
        List<ReportRenderer.Signature> signatures = jdbc.query("""
                SELECT role, signer_name, signed_at, rating, comment
                FROM service_signature
                WHERE snapshot_id = ? AND invalidated_at IS NULL
                ORDER BY (role = 'STAFF') DESC, signed_at
                """,
                rs -> {
                    List<ReportRenderer.Signature> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new ReportRenderer.Signature(
                                rs.getString("role"),
                                rs.getString("signer_name"),
                                rs.getTimestamp("signed_at").toInstant().toString(),
                                rs.getObject("rating") != null ? String.valueOf(rs.getInt("rating")) : null,
                                rs.getString("comment")));
                    }
                    return list;
                }, uuidBytes(pub.snapshotId()));

        // 任务服务类型
        String serviceType = jdbc.queryForObject(
                "SELECT service_type FROM service_task WHERE id = ?", String.class, uuidBytes(svc.taskId()));

        // 报告编号：全局唯一，规则 PEST-yyyyMMdd-{任务ID前8位}（公司配置评审前占位）
        String reportNumber = "PEST-" + java.time.LocalDate.now().toString().replace("-", "")
                + "-" + svc.taskId().toString().replace("-", "").substring(0, 8).toUpperCase();

        return new PublicationData(pub, svc, contentJson, signatures, serviceType, reportNumber);
    }

    private ReportRenderer.RenderedPdf renderPdf(PublicationData data) {
        ReportRenderer.RenderInput input = new ReportRenderer.RenderInput(
                data.reportNumber(),
                data.service().storeName(),
                data.serviceType(),
                data.service().startedAt() != null ? data.service().startedAt().toString() : "",
                data.service().endedAt() != null ? data.service().endedAt().toString() : "",
                data.contentJson(),
                data.signatures());
        return renderer.render(input);
    }

    private void writePdf(JobLease lease, ReportRenderer.RenderedPdf pdf) {
        String objectKey = "reports/" + lease.businessId() + "/" + lease.token() + ".pdf";
        Path file = reportRoot.resolve(objectKey);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, pdf.bytes());
            // 校验可读、大小与摘要一致
            byte[] readBack = Files.readAllBytes(file);
            if (readBack.length != pdf.bytes().length
                    || !ReportRenderer.sha256(readBack).equals(pdf.sha256())) {
                throw new IllegalStateException("PDF 落盘校验失败：大小或摘要不一致");
            }
        } catch (IOException e) {
            throw new IllegalStateException("PDF 写入失败: " + e.getMessage(), e);
        }
    }

    /** 最终事务：校验 fencing_token，发布报告。 */
    private void publish(JobLease lease, PublicationData data, ReportRenderer.RenderedPdf pdf) {
        tx.executeWithoutResult(status -> {
            // 校验 fencing_token 未被回收（租约仍属本 worker）
            Integer token = jdbc.queryForObject(
                    "SELECT fencing_token FROM job_queue WHERE id = ? FOR UPDATE",
                    Integer.class, uuidBytes(lease.jobId()));
            if (token == null || token.longValue() != lease.token()) {
                throw new IllegalStateException("租约已失效，放弃发布");
            }

            // 报告 ID 复用 publication_job.id（业务唯一），version_no = 1
            UUID reportId = data.pub().id();
            String objectKey = "reports/" + lease.businessId() + "/" + lease.token() + ".pdf";
            Instant publishedAt = Instant.now();
            Instant startedAt = data.service().startedAt() != null ? data.service().startedAt() : publishedAt;
            Instant endedAt = data.service().endedAt() != null ? data.service().endedAt() : publishedAt;

            byte[] contentHashBytes = ReportRenderer.sha256Bytes(data.contentJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] pdfHashBytes = hexToBytes(pdf.sha256());

            // 插入 service_report
            jdbc.update("""
                    INSERT INTO service_report (id, service_id, snapshot_id, version_no, report_number,
                                               status, staff_signature_id, client_signature_id,
                                               content_hash, pdf_sha256, object_key, byte_size,
                                               published_at, service_started_at, service_ended_at)
                    VALUES (?, ?, ?, 1, ?, 'PUBLISHED', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    uuidBytes(reportId),
                    uuidBytes(data.pub().serviceId()),
                    uuidBytes(data.pub().snapshotId()),
                    data.reportNumber(),
                    uuidBytes(data.pub().staffSignatureId()),
                    uuidBytes(data.pub().clientSignatureId()),
                    contentHashBytes,
                    pdfHashBytes,
                    objectKey,
                    pdf.bytes().length,
                    java.sql.Timestamp.from(publishedAt),
                    java.sql.Timestamp.from(startedAt),
                    java.sql.Timestamp.from(endedAt));

            // 切换 service.current_report_id + phase PUBLISHED + completed_at
            jdbc.update("""
                    UPDATE service SET phase = 'PUBLISHED', current_report_id = ?, completed_at = ?
                    WHERE id = ?
                    """, uuidBytes(reportId), java.sql.Timestamp.from(publishedAt),
                    uuidBytes(data.pub().serviceId()));

            // task 置 COMPLETED
            jdbc.update("""
                    UPDATE service_task SET status = 'COMPLETED', revision = revision + 1
                    WHERE id = ?
                    """, uuidBytes(data.service().taskId()));

            // publication_job 置 SUCCEEDED
            jdbc.update("""
                    UPDATE publication_job SET status = 'SUCCEEDED', report_id = ?, error_code = NULL, error_message = NULL
                    WHERE id = ?
                    """, uuidBytes(reportId), uuidBytes(data.pub().id()));

            // job_queue 置 DONE
            jdbc.update("""
                    UPDATE job_queue SET status = 'DONE', lease_owner = NULL, lease_until = NULL
                    WHERE id = ?
                    """, uuidBytes(lease.jobId()));
        });
        log.info("报告发布成功 jobId={} reportId={} reportNumber={}",
                lease.jobId(), data.pub().id(), data.reportNumber());
    }

    /** 失败处理：指数退避重试，达上限转 DEAD + publication_job FAILED。 */
    private void markFailed(JobLease lease, String error) {
        String safeError = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
        tx.executeWithoutResult(status -> {
            // 达上限 → DEAD + publication_job FAILED
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
                        UPDATE publication_job SET status = 'FAILED', error_message = ?
                        WHERE id = ?
                        """, safeError, uuidBytes(lease.businessId()));
                log.warn("报告渲染转 DEAD jobId={} businessId={}", lease.jobId(), lease.businessId());
            } else {
                // 指数退避：2^attempts 秒
                int backoff = (int) Math.min(300, Math.pow(2, attempts == null ? 0 : attempts));
                jdbc.update("""
                        UPDATE job_queue SET status = 'READY', last_error = ?,
                            next_run_at = TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(6)),
                            lease_owner = NULL, lease_until = NULL
                        WHERE id = ?
                        """, safeError, backoff, uuidBytes(lease.jobId()));
                log.warn("报告渲染失败，{}s 后重试 jobId={}", backoff, lease.jobId());
            }
        });
    }

    // ---- 数据结构 ----

    record JobRow(UUID id, String kind, UUID businessId, long fencingToken) {}
    record JobLease(UUID jobId, UUID businessId, long token) {}
    record PublicationRow(UUID id, UUID serviceId, UUID snapshotId, UUID staffSignatureId, UUID clientSignatureId) {}
    record ServiceRow(UUID id, UUID taskId, UUID storeId, long headRevision, Instant startedAt, Instant endedAt, String storeName) {}
    record PublicationData(PublicationRow pub, ServiceRow service, String contentJson,
                           List<ReportRenderer.Signature> signatures, String serviceType, String reportNumber) {}

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

    static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
