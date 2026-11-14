package com.sws.pest.infrastructure.media;

import com.sws.pest.domain.media.Attachment;
import com.sws.pest.domain.media.AttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * attachment / upload_session 的 JDBC 实现，附件二进制存本地文件系统。
 *
 * 首期简化：
 * - object_key 指向本地文件路径（相对 uploadRoot），非真实 OSS；
 * - context_id 沿用 offline_context 占位（复用 Service 模块的 ensureContext 语义）；
 * - MIME 嗅探首期信任客户端 mediaType，仅做枚举白名单校验；
 * - 完整文件重传，不做断点续传。
 */
@Repository
public class JdbcAttachmentRepository implements AttachmentRepository {

    private final JdbcTemplate jdbc;
    private final Path uploadRoot;

    public JdbcAttachmentRepository(JdbcTemplate jdbc,
                                    @Value("${pest.media.upload-root:/tmp/pest-media}") String uploadRoot) {
        this.jdbc = jdbc;
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    @Override
    public Optional<Attachment> findById(UUID attachmentId) {
        String sql = """
                SELECT id, store_id, owner_id, context_id, purpose, scope, status,
                       object_key, sha256, byte_size, mime_type, verified_at, failure_code,
                       created_at, updated_at
                FROM attachment WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<Attachment>empty();
            }
            return Optional.of(mapAttachment(rs));
        }, uuidBytes(attachmentId));
    }

    @Override
    @Transactional
    public UploadSessionView createOrResume(UUID attachmentId, UUID contextId, UUID storeId,
                                            UUID ownerId, String purpose, String fileName,
                                            String mediaType, long sizeBytes, String sha256) {
        // store_id 缺失时从 offline_context 反查
        if (storeId == null) {
            storeId = resolveStoreId(contextId);
        }
        if (storeId == null) {
            throw new IllegalArgumentException("无法解析门店归属：contextId 不存在");
        }
        // 幂等：附件已 READY，直接返回 isAlreadyVerified 语义（无新会话）
        Optional<Attachment> existing = findById(attachmentId);
        if (existing.isPresent() && existing.get().isReady()) {
            return new UploadSessionView(attachmentId, attachmentId, "VERIFIED",
                    Instant.now(), sizeBytes, existing.get().createdAt());
        }

        // 复用已存在的 OPEN 会话
        Optional<UploadSessionView> session = findSessionByAttachment(attachmentId);
        if (session.isPresent()) {
            return session.get();
        }

        // 新建附件（若不存在）
        if (existing.isEmpty()) {
            ensureContext(contextId, storeId, ownerId);
            String objectKey = "attachments/" + attachmentId + "/" + fileName;
            jdbc.update("""
                    INSERT INTO attachment (id, store_id, owner_id, context_id, purpose, scope,
                                           status, object_key, sha256, byte_size, mime_type)
                    VALUES (?, ?, ?, ?, ?, 'NORMAL', 'PENDING', ?, ?, ?, ?)
                    """,
                    uuidBytes(attachmentId),
                    uuidBytes(storeId),
                    uuidBytes(ownerId),
                    uuidBytes(contextId),
                    purpose,
                    objectKey,
                    hexToBytes(sha256),
                    sizeBytes,
                    mediaType);
        }

        // 新建上传会话
        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO upload_session (id, attachment_id, user_id, temporary_key, status,
                                           expires_at, received_size)
                VALUES (?, ?, ?, ?, 'OPEN', DATE_ADD(NOW(6), INTERVAL 24 HOUR), 0)
                """,
                uuidBytes(sessionId),
                uuidBytes(attachmentId),
                uuidBytes(ownerId),
                "tmp/" + sessionId);

        return new UploadSessionView(sessionId, attachmentId, "OPEN",
                Instant.now().plusSeconds(86400), 0, Instant.now());
    }

    @Override
    public Optional<UploadSessionView> findSession(UUID uploadId) {
        String sql = """
                SELECT id, attachment_id, status, expires_at, received_size, created_at
                FROM upload_session WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<UploadSessionView>empty();
            }
            return Optional.of(mapSession(rs));
        }, uuidBytes(uploadId));
    }

    @Override
    @Transactional
    public long storeBytes(UUID uploadId, byte[] content) {
        UploadSessionView session = findSession(uploadId)
                .orElseThrow(() -> new UploadSessionNotFound("上传会话不存在或已失效"));
        if ("VERIFIED".equals(session.status())) {
            throw new IllegalStateException("附件已校验，不能重复上传");
        }

        UUID attachmentId = session.attachmentId();
        Attachment att = findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFound("附件不存在"));

        // 写文件
        Path file = uploadRoot.resolve(att.objectKey());
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException e) {
            throw new IllegalStateException("附件写入失败: " + e.getMessage(), e);
        }

        jdbc.update("""
                UPDATE upload_session SET status = 'RECEIVED', received_size = ?
                WHERE id = ?
                """, content.length, uuidBytes(uploadId));

        // 附件进入 VERIFYING
        jdbc.update("UPDATE attachment SET status = 'VERIFYING' WHERE id = ?",
                uuidBytes(attachmentId));

        return content.length;
    }

    @Override
    @Transactional
    public Attachment verify(UUID uploadId, String sha256, long sizeBytes) {
        UploadSessionView session = findSession(uploadId)
                .orElseThrow(() -> new UploadSessionNotFound("上传会话不存在或已失效"));

        UUID attachmentId = session.attachmentId();
        Attachment att = findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFound("附件不存在"));

        // 服务器重算哈希与大小，不信任客户端：先比声明的 sha256/size 与入库预期
        byte[] expectedHash = hexToBytes(sha256);
        byte[] declaredHash = hexToBytes(att.sha256());
        if (!constantTimeEquals(declaredHash, expectedHash) || att.byteSize() != sizeBytes) {
            jdbc.update("UPDATE attachment SET status = 'FAILED', failure_code = 'HASH_MISMATCH' WHERE id = ?",
                    uuidBytes(attachmentId));
            throw new IllegalStateException("附件校验失败：哈希或大小不匹配");
        }

        // 实际校验文件内容
        Path file = uploadRoot.resolve(att.objectKey());
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("附件读取失败: " + e.getMessage(), e);
        }
        byte[] actualHash = sha256(content);
        if (!constantTimeEquals(expectedHash, actualHash) || content.length != sizeBytes) {
            jdbc.update("UPDATE attachment SET status = 'FAILED', failure_code = 'CONTENT_MISMATCH' WHERE id = ?",
                    uuidBytes(attachmentId));
            throw new IllegalStateException("附件校验失败：文件内容哈希不匹配");
        }

        // 标记 READY
        jdbc.update("""
                UPDATE attachment SET status = 'READY', verified_at = NOW(6), failure_code = NULL
                WHERE id = ?
                """, uuidBytes(attachmentId));
        jdbc.update("UPDATE upload_session SET status = 'VERIFIED' WHERE id = ?",
                uuidBytes(uploadId));

        return findById(attachmentId).orElseThrow();
    }

    @Override
    public byte[] readContent(UUID attachmentId) {
        Attachment att = findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFound("附件不存在"));
        if (!att.isReady()) {
            throw new IllegalStateException("附件尚未校验通过，不能下载");
        }
        try {
            return Files.readAllBytes(uploadRoot.resolve(att.objectKey()));
        } catch (IOException e) {
            throw new IllegalStateException("附件读取失败: " + e.getMessage(), e);
        }
    }

    // ---- 占位与工具 ----

    private UUID resolveStoreId(UUID contextId) {
        byte[] storeId = jdbc.query(
                "SELECT store_id FROM offline_context WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return rs.getBytes("store_id");
                }, uuidBytes(contextId));
        return storeId != null ? bytesToUuid(storeId) : null;
    }

    private void ensureContext(UUID contextId, UUID storeId, UUID ownerId) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM offline_context WHERE id = ?", Integer.class, uuidBytes(contextId));
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO offline_context (id, store_id, task_id, user_id, device_id,
                                            assignment_version, permission_version, context_digest,
                                            canonical_body, context_json, expires_at, evidence_until)
                VALUES (?, ?, ?, ?, 'seed-placeholder', 1, 1, ?, '{}', '{}',
                        DATE_ADD(NOW(6), INTERVAL 30 DAY), DATE_ADD(NOW(6), INTERVAL 30 DAY))
                """,
                uuidBytes(contextId),
                uuidBytes(storeId),
                uuidBytes(storeId), // task_id 占位用 storeId（offline_context.task_id 有外键）
                uuidBytes(ownerId),
                new byte[32]);
    }

    private Optional<UploadSessionView> findSessionByAttachment(UUID attachmentId) {
        String sql = """
                SELECT id, attachment_id, status, expires_at, received_size, created_at
                FROM upload_session WHERE attachment_id = ? AND status IN ('OPEN','RECEIVED')
                ORDER BY created_at DESC LIMIT 1
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<UploadSessionView>empty();
            }
            return Optional.of(mapSession(rs));
        }, uuidBytes(attachmentId));
    }

    private static UploadSessionView mapSession(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UploadSessionView(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("attachment_id")),
                rs.getString("status"),
                rs.getTimestamp("expires_at") != null ? rs.getTimestamp("expires_at").toInstant() : null,
                rs.getLong("received_size"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null);
    }

    private static Attachment mapAttachment(java.sql.ResultSet rs) throws java.sql.SQLException {
        byte[] sha = rs.getBytes("sha256");
        return new Attachment(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("store_id")),
                bytesToUuid(rs.getBytes("owner_id")),
                bytesToUuid(rs.getBytes("context_id")),
                rs.getString("purpose"),
                rs.getString("scope"),
                rs.getString("status"),
                rs.getString("object_key"),
                sha != null ? bytesToHex(sha) : null,
                rs.getLong("byte_size"),
                rs.getString("mime_type"),
                rs.getTimestamp("verified_at") != null ? rs.getTimestamp("verified_at").toInstant() : null,
                rs.getString("failure_code"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null);
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

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length; i++) {
            r |= a[i] ^ b[i];
        }
        return r == 0;
    }
}
