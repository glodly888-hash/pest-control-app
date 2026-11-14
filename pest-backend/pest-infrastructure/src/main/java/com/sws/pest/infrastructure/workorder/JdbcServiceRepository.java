package com.sws.pest.infrastructure.workorder;

import com.sws.pest.domain.workorder.FieldService;
import com.sws.pest.domain.workorder.ServicePhase;
import com.sws.pest.domain.workorder.ServiceRepository;
import com.sws.pest.domain.workorder.TaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * service / service_draft / service_snapshot 的 JDBC 实现。
 *
 * 首期简化：
 * - content_json 原样透传，不做 V01-V16 逐字段校验；
 * - canonical_payload 与 payload_json 首期同源（冻结时按 content 生成）；
 * - 签到推进 IN_PROGRESS、published 等状态流转留待后续模块。
 */
@Repository
public class JdbcServiceRepository implements ServiceRepository {

    private final JdbcTemplate jdbc;

    public JdbcServiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<FieldService> findByTaskId(UUID taskId) {
        String sql = """
                SELECT id, task_id, store_id, actor_id, context_id, phase,
                       head_revision, active_snapshot_id, current_report_id,
                       service_started_at, completed_at
                FROM service WHERE task_id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<FieldService>empty();
            }
            return Optional.of(mapService(rs));
        }, uuidBytes(taskId));
    }

    @Override
    @Transactional
    public FieldService createService(FieldService service) {
        // 幂等：同任务已存在则直接返回（并发下靠唯一索引兜底）
        Optional<FieldService> existing = findByTaskId(service.taskId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // 首期简化：确保 offline_context 存在（完整离线上下文由 getTaskContext/getStoreContext 生成，
        // 此处以占位上下文满足外键约束）。context_digest 用零字节占位。
        ensureContext(service.contextId(), service.storeId(), service.taskId(), service.actorId());

        jdbc.update("""
                INSERT INTO service (id, task_id, store_id, actor_id, context_id, phase, head_revision)
                VALUES (?, ?, ?, ?, ?, 'EDITABLE', 1)
                """,
                uuidBytes(service.id()),
                uuidBytes(service.taskId()),
                uuidBytes(service.storeId()),
                uuidBytes(service.actorId()),
                uuidBytes(service.contextId()));

        // 空草稿
        UUID draftId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO service_draft (id, service_id, context_id, revision,
                                           field_commit_revision, schema_version, content_json, saved_by)
                VALUES (?, ?, ?, 1, 0, '1.0', '{}', ?)
                """,
                uuidBytes(draftId),
                uuidBytes(service.id()),
                uuidBytes(service.contextId()),
                uuidBytes(service.actorId()));

        return service;
    }

    @Override
    public Optional<DraftView> findDraft(UUID serviceId) {
        String sql = """
                SELECT s.id, s.task_id, s.store_id, s.actor_id, s.context_id, s.phase,
                       s.head_revision, s.active_snapshot_id, s.current_report_id,
                       s.service_started_at, s.completed_at,
                       d.content_json, d.revision, d.field_commit_revision, d.updated_at
                FROM service s
                JOIN service_draft d ON d.service_id = s.id
                WHERE s.id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<DraftView>empty();
            }
            FieldService service = mapService(rs);
            String contentJson = rs.getString("content_json");
            long draftRevision = rs.getLong("revision");
            long fieldCommitRevision = rs.getLong("field_commit_revision");
            Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
            return Optional.of(new DraftView(service, contentJson, draftRevision,
                    fieldCommitRevision, updatedAt));
        }, uuidBytes(serviceId));
    }

    @Override
    @Transactional
    public long saveDraft(UUID serviceId, String contentJson, UUID actorId, long expectedRevision) {
        // 先校验 phase（冻结后禁止修改）——调用方已 findDraft 保证存在
        String phase = jdbc.queryForObject(
                "SELECT phase FROM service WHERE id = ?", String.class, uuidBytes(serviceId));
        if (!"EDITABLE".equals(phase)) {
            throw new IllegalStateException("服务已冻结，禁止修改草稿");
        }

        long nextRevision = expectedRevision + 1;
        int updated = jdbc.update("""
                UPDATE service_draft SET content_json = ?, revision = ?, saved_by = ?
                WHERE service_id = ? AND revision = ?
                """, contentJson, nextRevision, uuidBytes(actorId),
                uuidBytes(serviceId), expectedRevision);
        if (updated == 0) {
            throw new TaskRepository.OptimisticLockException("草稿版本冲突，请刷新后重试");
        }
        return nextRevision;
    }

    @Override
    @Transactional
    public UUID freezeSnapshot(UUID serviceId, long expectedDraftRevision, String contentHash,
                               String canonicalPayload, String payloadJson, long headRevision) {
        String phase = jdbc.queryForObject(
                "SELECT phase FROM service WHERE id = ?", String.class, uuidBytes(serviceId));
        if (!"EDITABLE".equals(phase)) {
            throw new IllegalStateException("服务已冻结，不能重复冻结");
        }

        // 乐观锁：草稿版本必须匹配
        long currentRevision = jdbc.queryForObject(
                "SELECT revision FROM service_draft WHERE service_id = ?",
                Long.class, uuidBytes(serviceId));
        if (currentRevision != expectedDraftRevision) {
            throw new TaskRepository.OptimisticLockException("草稿版本冲突，请刷新后重试");
        }

        UUID snapshotId = UUID.randomUUID();
        byte[] contentHashBytes = hexToBytes(contentHash);
        byte[] contextIdBytes = jdbc.queryForObject(
                "SELECT context_id FROM service WHERE id = ?", byte[].class, uuidBytes(serviceId));
        UUID contextId = bytesToUuid(contextIdBytes);

        // 首期简化：确保存在 TEMPLATE 占位配置（完整报告模板由 config_release 版本化下发，此处占位满足外键）
        UUID templateReleaseId = ensureTemplateRelease();

        jdbc.update("""
                INSERT INTO service_snapshot (id, service_id, context_id, content_version,
                                              head_revision, draft_revision, content_hash,
                                              canonical_payload, payload_json, template_release_id,
                                              renderer_version)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, 'v1')
                """,
                uuidBytes(snapshotId),
                uuidBytes(serviceId),
                uuidBytes(contextId),
                headRevision,
                currentRevision,
                contentHashBytes,
                canonicalPayload.getBytes(StandardCharsets.UTF_8),
                payloadJson,
                uuidBytes(templateReleaseId));

        jdbc.update("""
                UPDATE service SET phase = 'FROZEN', active_snapshot_id = ?
                WHERE id = ? AND phase = 'EDITABLE'
                """, uuidBytes(snapshotId), uuidBytes(serviceId));

        return snapshotId;
    }

    @Override
    public Optional<SnapshotDetail> findSnapshot(UUID snapshotId) {
        String sql = """
                SELECT sn.id, sn.service_id, sn.content_version, sn.content_hash,
                       sn.payload_json, sn.template_release_id, sn.invalidated_at, sn.created_at,
                       cr.version_no
                FROM service_snapshot sn
                LEFT JOIN config_release cr ON cr.id = sn.template_release_id
                WHERE sn.id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<SnapshotDetail>empty();
            }
            return Optional.of(new SnapshotDetail(
                    bytesToUuid(rs.getBytes("id")),
                    bytesToUuid(rs.getBytes("service_id")),
                    rs.getLong("content_version"),
                    hex(rs.getBytes("content_hash")),
                    rs.getString("payload_json"),
                    String.valueOf(rs.getLong("version_no")),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("invalidated_at") == null));
        }, uuidBytes(snapshotId));
    }

    @Override
    @Transactional
    public long reopenDraft(UUID serviceId, UUID fromSnapshotId, long expectedHeadRevision) {
        String phase = jdbc.queryForObject(
                "SELECT phase FROM service WHERE id = ?", String.class, uuidBytes(serviceId));
        if ("EDITABLE".equals(phase) || "PUBLISHED".equals(phase) || "PUBLISHING".equals(phase)) {
            throw new IllegalStateException("当前阶段不允许修订草稿");
        }

        // 乐观锁：head_revision 必须匹配
        int updated = jdbc.update("""
                UPDATE service SET head_revision = ?, phase = 'EDITABLE', active_snapshot_id = NULL
                WHERE id = ? AND head_revision = ? AND active_snapshot_id = ?
                """, expectedHeadRevision + 1, uuidBytes(serviceId),
                expectedHeadRevision, uuidBytes(fromSnapshotId));
        if (updated == 0) {
            throw new TaskRepository.OptimisticLockException("服务版本冲突，请刷新后重试");
        }

        // 作废旧快照
        jdbc.update("UPDATE service_snapshot SET invalidated_at = NOW(6) WHERE id = ?",
                uuidBytes(fromSnapshotId));

        // 作废该快照下的有效签字
        jdbc.update("""
                UPDATE service_signature SET invalidated_at = NOW(6)
                WHERE snapshot_id = ? AND invalidated_at IS NULL
                """, uuidBytes(fromSnapshotId));

        // 阻断该快照的发布任务
        jdbc.update("""
                UPDATE publication_job SET status = 'BLOCKED',
                    error_code = 'REOPENED', error_message = '签后修订，发布任务已阻断'
                WHERE snapshot_id = ? AND status IN ('QUEUED','RUNNING')
                """, uuidBytes(fromSnapshotId));

        return expectedHeadRevision + 1;
    }

    @Override
    @Transactional
    public void createCorrection(UUID serviceId, UUID reportId, long expectedHeadRevision) {
        String phase = jdbc.queryForObject(
                "SELECT phase FROM service WHERE id = ?", String.class, uuidBytes(serviceId));
        if (!"PUBLISHED".equals(phase)) {
            throw new IllegalStateException("仅已发布报告可创建更正");
        }

        // 校验报告存在且属于该服务
        Integer reportCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_report WHERE id = ? AND service_id = ?",
                Integer.class, uuidBytes(reportId), uuidBytes(serviceId));
        if (reportCount == null || reportCount == 0) {
            throw new IllegalArgumentException("报告不存在或不属于该服务");
        }

        // head_revision 递增、active_snapshot_id 置空、phase 回 EDITABLE、current_report_id 保持
        int updated = jdbc.update("""
                UPDATE service SET head_revision = ?, phase = 'EDITABLE', active_snapshot_id = NULL
                WHERE id = ? AND head_revision = ?
                """, expectedHeadRevision + 1, uuidBytes(serviceId), expectedHeadRevision);
        if (updated == 0) {
            throw new TaskRepository.OptimisticLockException("服务版本冲突，请刷新后重试");
        }

        // 草稿打上 correction_of_report_id 标记（新内容代次）
        jdbc.update("""
                UPDATE service_draft SET correction_of_report_id = ?, revision = 1,
                    field_commit_revision = 0
                WHERE service_id = ?
                """, uuidBytes(reportId), uuidBytes(serviceId));
    }

    private void ensureContext(UUID contextId, UUID storeId, UUID taskId, UUID actorId) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM offline_context WHERE id = ?", Integer.class, uuidBytes(contextId));
        if (exists != null && exists > 0) {
            return;
        }
        // 占位上下文：context_digest 用 32 字节零值，body 为 "{}"，有效期 30 天。
        jdbc.update("""
                INSERT INTO offline_context (id, store_id, task_id, user_id, device_id,
                                            assignment_version, permission_version, context_digest,
                                            canonical_body, context_json, expires_at, evidence_until)
                VALUES (?, ?, ?, ?, 'seed-placeholder', 1, 1, ?, '{}', '{}',
                        DATE_ADD(NOW(6), INTERVAL 30 DAY), DATE_ADD(NOW(6), INTERVAL 30 DAY))
                """,
                uuidBytes(contextId),
                uuidBytes(storeId),
                uuidBytes(taskId),
                uuidBytes(actorId),
                new byte[32]);
    }

    private UUID ensureTemplateRelease() {
        // 复用已存在的 TEMPLATE 占位；无则创建（published_by 用种子员工 employee01）。
        byte[] existing = jdbc.query(
                "SELECT id FROM config_release WHERE kind = 'TEMPLATE' LIMIT 1",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return rs.getBytes("id");
                });
        if (existing != null) {
            return bytesToUuid(existing);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO config_release (id, kind, version_no, payload_json, payload_hash,
                                            published_by, published_at)
                VALUES (?, 'TEMPLATE', 1, '{}', ?, ?, NOW(6))
                """,
                uuidBytes(id),
                new byte[32],
                uuidBytes(UUID.fromString("30000000-0000-0000-0000-000000000001")));
        return id;
    }

    private static FieldService mapService(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FieldService(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("task_id")),
                bytesToUuid(rs.getBytes("store_id")),
                bytesToUuid(rs.getBytes("actor_id")),
                bytesToUuid(rs.getBytes("context_id")),
                ServicePhase.valueOf(rs.getString("phase")),
                rs.getLong("head_revision"),
                rs.getBytes("active_snapshot_id") != null ? bytesToUuid(rs.getBytes("active_snapshot_id")) : null,
                rs.getBytes("current_report_id") != null ? bytesToUuid(rs.getBytes("current_report_id")) : null,
                rs.getTimestamp("service_started_at") != null ? rs.getTimestamp("service_started_at").toInstant() : null,
                rs.getTimestamp("completed_at") != null ? rs.getTimestamp("completed_at").toInstant() : null);
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

    private static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
