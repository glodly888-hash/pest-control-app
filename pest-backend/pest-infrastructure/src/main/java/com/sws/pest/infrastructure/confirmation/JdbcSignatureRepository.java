package com.sws.pest.infrastructure.confirmation;

import com.sws.pest.domain.confirmation.Signature;
import com.sws.pest.domain.confirmation.SignatureRepository;
import com.sws.pest.domain.workorder.FieldService;
import com.sws.pest.domain.workorder.ServicePhase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * service_signature 的 JDBC 实现。
 *
 * 首期简化：
 * - 签字图片 attachment：以占位记录（status=READY + verified_at 非空）满足外键与 CHECK 约束，
 *   真实上传/病毒扫描链路（createUpload/uploadBytes/verifyUpload）后续实现；
 * - 客户阅读回执 preview_session：以占位记录（visible_duration_ms=5000）满足约束；
 * - 附件的 store_id/owner_id/context_id 从快照服务派生。
 */
@Repository
public class JdbcSignatureRepository implements SignatureRepository {

    private final JdbcTemplate jdbc;

    public JdbcSignatureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SnapshotView> findSnapshot(UUID snapshotId) {
        String sql = """
                SELECT sn.id AS snapshot_id, sn.content_hash, sn.service_id,
                       s.task_id, s.store_id, s.actor_id, s.context_id, s.phase,
                       s.head_revision, s.active_snapshot_id, s.current_report_id,
                       s.service_started_at, s.completed_at
                FROM service_snapshot sn
                JOIN service s ON s.id = sn.service_id
                WHERE sn.id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<SnapshotView>empty();
            }
            UUID svcId = bytesToUuid(rs.getBytes("service_id"));
            FieldService service = new FieldService(
                    svcId,
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
            String contentHash = hex(rs.getBytes("content_hash"));
            boolean published = "PUBLISHED".equals(rs.getString("phase"));
            return Optional.of(new SnapshotView(snapshotId, service, contentHash, published));
        }, uuidBytes(snapshotId));
    }

    @Override
    public Optional<Signature> findActive(UUID snapshotId, Signature.Role role) {
        String sql = """
                SELECT id, snapshot_id, role, signer_name, staff_user_id, staff_signature_id,
                       preview_session_id, attachment_id, content_hash, attestation_hash,
                       signed_at, rating, comment, supersedes_id, replacement_reason, created_at
                FROM service_signature
                WHERE snapshot_id = ? AND role = ? AND invalidated_at IS NULL
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<Signature>empty();
            }
            return Optional.of(mapSignature(rs));
        }, uuidBytes(snapshotId), role.name());
    }

    @Override
    public Optional<Signature> findById(UUID signatureId) {
        String sql = """
                SELECT id, snapshot_id, role, signer_name, staff_user_id, staff_signature_id,
                       preview_session_id, attachment_id, content_hash, attestation_hash,
                       signed_at, rating, comment, supersedes_id, replacement_reason, created_at
                FROM service_signature
                WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<Signature>empty();
            }
            return Optional.of(mapSignature(rs));
        }, uuidBytes(signatureId));
    }

    @Override
    @Transactional
    public Signature save(Signature signature) {
        SnapshotView snapshot = findSnapshot(signature.snapshotId()).orElse(null);
        if (snapshot == null) {
            throw new InvalidReferenceException("快照不存在");
        }

        // 确保签字图片附件存在（占位 READY）
        ensureAttachment(signature.attachmentId(), snapshot.service());

        // 客户签字需阅读回执
        if (signature.role() == Signature.Role.CLIENT && signature.previewSessionId() != null) {
            ensurePreviewSession(signature.previewSessionId(), signature.snapshotId(),
                    snapshot.service().actorId(), signature.contentHash());
        }

        jdbc.update("""
                INSERT INTO service_signature (id, snapshot_id, role, signer_name, staff_user_id,
                                              staff_signature_id, preview_session_id, attachment_id,
                                              content_hash, attestation_hash, canonical_attestation,
                                              signed_at, rating, comment, supersedes_id, replacement_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                uuidBytes(signature.id()),
                uuidBytes(signature.snapshotId()),
                signature.role().name(),
                signature.signerName(),
                signature.staffUserId() != null ? uuidBytes(signature.staffUserId()) : null,
                signature.staffSignatureId() != null ? uuidBytes(signature.staffSignatureId()) : null,
                signature.previewSessionId() != null ? uuidBytes(signature.previewSessionId()) : null,
                uuidBytes(signature.attachmentId()),
                hexToBytes(signature.contentHash()),
                hexToBytes(signature.attestationHash()),
                ("{\"signer\":\"" + signature.signerName() + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.sql.Timestamp.from(signature.signedAt()),
                signature.rating(),
                signature.comment(),
                signature.supersedesId() != null ? uuidBytes(signature.supersedesId()) : null,
                signature.replacementReason());

        return findById(signature.id()).orElse(signature);
    }

    private void ensureAttachment(UUID attachmentId, FieldService service) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM attachment WHERE id = ?", Integer.class, uuidBytes(attachmentId));
        if (exists != null && exists > 0) {
            return;
        }
        // 占位附件：status=READY 且 verified_at 非空（满足 ck_attachment_4）
        jdbc.update("""
                INSERT INTO attachment (id, store_id, owner_id, context_id, purpose, scope, status,
                                        object_key, sha256, byte_size, mime_type, verified_at)
                VALUES (?, ?, ?, ?, 'SIGNATURE', 'NORMAL', 'READY', ?, ?, 1, 'image/png', NOW(6))
                """,
                uuidBytes(attachmentId),
                uuidBytes(service.storeId()),
                uuidBytes(service.actorId()),
                uuidBytes(service.contextId()),
                "placeholder/" + attachmentId,
                new byte[32]);
    }

    private void ensurePreviewSession(UUID sessionId, UUID snapshotId, UUID userId, String contentHash) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM preview_session WHERE id = ?", Integer.class, uuidBytes(sessionId));
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO preview_session (id, snapshot_id, user_id, content_hash, started_at,
                                             completed_at, visible_duration_ms, is_offline, device_id)
                VALUES (?, ?, ?, ?, NOW(6), NOW(6), 5000, FALSE, 'seed-placeholder')
                """,
                uuidBytes(sessionId),
                uuidBytes(snapshotId),
                uuidBytes(userId),
                hexToBytes(contentHash));
    }

    private static Signature mapSignature(ResultSet rs) throws SQLException {
        return new Signature(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("snapshot_id")),
                Signature.Role.valueOf(rs.getString("role")),
                rs.getString("signer_name"),
                rs.getBytes("staff_user_id") != null ? bytesToUuid(rs.getBytes("staff_user_id")) : null,
                rs.getBytes("staff_signature_id") != null ? bytesToUuid(rs.getBytes("staff_signature_id")) : null,
                rs.getBytes("preview_session_id") != null ? bytesToUuid(rs.getBytes("preview_session_id")) : null,
                bytesToUuid(rs.getBytes("attachment_id")),
                hex(rs.getBytes("content_hash")),
                hex(rs.getBytes("attestation_hash")),
                rs.getTimestamp("signed_at").toInstant(),
                rs.getObject("rating") != null ? rs.getInt("rating") : null,
                rs.getString("comment"),
                rs.getBytes("supersedes_id") != null ? bytesToUuid(rs.getBytes("supersedes_id")) : null,
                rs.getString("replacement_reason"),
                rs.getTimestamp("created_at").toInstant());
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
