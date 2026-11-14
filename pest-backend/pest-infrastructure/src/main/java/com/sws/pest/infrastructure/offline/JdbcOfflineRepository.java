package com.sws.pest.infrastructure.offline;

import com.sws.pest.domain.ledger.Risk;
import com.sws.pest.domain.masterdata.Facility;
import com.sws.pest.domain.masterdata.Store;
import com.sws.pest.domain.offline.OfflineContextBody;
import com.sws.pest.domain.offline.OfflineRepository;
import com.sws.pest.domain.platform.DictionaryItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 离线同步的 JDBC 实现。
 *
 * 首期简化：
 * - contextBody 以 JSON 字符串透传，contextDigest 用 SHA-256（非严格 JCS）；
 * - offline_asset 二进制存本地文件系统（object_key 指向路径）；
 * - 提交两段式：receive 持久化原包 + 入队 SYNC_APPLY，应用由 worker 异步执行。
 */
@Repository
public class JdbcOfflineRepository implements OfflineRepository {

    private final JdbcTemplate jdbc;
    private final Path assetRoot;

    public JdbcOfflineRepository(JdbcTemplate jdbc,
                                 @Value("${pest.media.upload-root:/tmp/pest-media}") String uploadRoot) {
        this.jdbc = jdbc;
        this.assetRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    // ---- 上下文 ----

    @Override
    @Transactional
    public OfflineContextView createContext(UUID id, UUID storeId, UUID taskId, UUID userId,
                                            String deviceId, long permissionVersion,
                                            String contextDigest, String contextBody,
                                            Instant expiresAt, Instant evidenceUntil) {
        // 复用未过期的同上下文
        Optional<OfflineContextView> existing = findContext(id);
        if (existing.isPresent() && existing.get().expiresAt().isAfter(Instant.now())) {
            return existing.get();
        }

        jdbc.update("""
                INSERT INTO offline_context (id, store_id, task_id, user_id, device_id,
                                            assignment_version, permission_version, context_digest,
                                            canonical_body, context_json, expires_at, evidence_until)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE context_json = VALUES(context_json),
                                        canonical_body = VALUES(canonical_body),
                                        context_digest = VALUES(context_digest),
                                        expires_at = VALUES(expires_at),
                                        evidence_until = VALUES(evidence_until),
                                        downloaded_at = NULL,
                                        revoked_at = NULL
                """,
                uuidBytes(id),
                uuidBytes(storeId),
                taskId != null ? uuidBytes(taskId) : null,
                uuidBytes(userId),
                deviceId,
                permissionVersion,
                hexToBytes(contextDigest),
                contextBody.getBytes(StandardCharsets.UTF_8),
                contextBody,
                Timestamp.from(expiresAt),
                Timestamp.from(evidenceUntil));

        return findContext(id).orElseThrow(() -> new IllegalStateException("上下文创建失败"));
    }

    @Override
    public Optional<OfflineContextView> findContext(UUID contextId) {
        String sql = """
                SELECT id, store_id, task_id, user_id, device_id, permission_version,
                       context_digest, context_json, expires_at, evidence_until,
                       downloaded_at, revoked_at, created_at
                FROM offline_context WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<OfflineContextView>empty();
            }
            return Optional.of(mapContext(rs));
        }, uuidBytes(contextId));
    }

    @Override
    @Transactional
    public void markDownloaded(UUID contextId, Instant downloadedAt) {
        jdbc.update("UPDATE offline_context SET downloaded_at = ? WHERE id = ?",
                Timestamp.from(downloadedAt), uuidBytes(contextId));
    }

    @Override
    public String serializeContextBody(OfflineContextBody body) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.0");

        // store
        ObjectNode store = mapper.createObjectNode();
        if (body.store() != null) {
            store.put("id", body.store().id());
            store.put("code", body.store().code());
            store.put("name", body.store().name());
        }
        root.set("store", store);

        // facilities
        ArrayNode facilities = mapper.createArrayNode();
        for (Facility f : body.facilities()) {
            ObjectNode o = facilities.addObject();
            o.put("id", f.id() == null ? null : f.id().toString());
            o.put("code", f.code());
            o.put("typeId", f.typeId() == null ? null : f.typeId().toString());
            o.put("typeName", f.typeName());
            o.put("areaId", f.areaId() == null ? null : f.areaId().toString());
            o.put("floor", f.floor());
            o.put("position", f.position());
            o.put("revision", f.revision());
        }
        root.set("facilities", facilities);

        // openRisks（仅未关闭风险）
        ArrayNode risks = mapper.createArrayNode();
        for (Risk r : body.openRisks()) {
            ObjectNode o = risks.addObject();
            o.put("id", r.id() == null ? null : r.id().toString());
            o.put("position", r.position());
            o.put("description", r.description());
            o.put("revision", r.revision());
        }
        root.set("openRisks", risks);

        // areas
        ArrayNode areas = mapper.createArrayNode();
        for (Store.Area a : body.areas()) {
            ObjectNode o = areas.addObject();
            o.put("id", a.id() == null ? null : a.id().toString());
            o.put("name", a.name());
            o.put("floor", a.floor());
        }
        root.set("areas", areas);

        // dictionaries（按 kind 分组：PEST / CHEMICAL / FACILITY_TYPE）
        ObjectNode dictionaries = mapper.createObjectNode();
        ArrayNode pestTypes = mapper.createArrayNode();
        ArrayNode chemicals = mapper.createArrayNode();
        ArrayNode facilityTypes = mapper.createArrayNode();
        for (DictionaryItem d : body.dictionaries()) {
            ObjectNode o = mapper.createObjectNode();
            o.put("id", d.id() == null ? null : d.id().toString());
            o.put("name", d.name());
            o.put("code", d.code());
            o.put("revision", d.revision());
            if (d.formulation() != null) {
                o.put("formulation", d.formulation());
            }
            switch (d.kind()) {
                case "PEST" -> pestTypes.add(o);
                case "CHEMICAL" -> chemicals.add(o);
                case "FACILITY_TYPE" -> facilityTypes.add(o);
                default -> { /* 其余 kind（MEASURE/UNIT/RISK_TYPE/AREA）暂不进上下文 */ }
            }
        }
        dictionaries.set("pestTypes", pestTypes);
        dictionaries.set("chemicals", chemicals);
        dictionaries.set("facilityTypes", facilityTypes);
        root.set("dictionaries", dictionaries);

        // 保留占位字段，兼容前端 OfflineContextBody 解析
        root.put("templateVersion", "1");
        root.put("templateSha256", "0000000000000000000000000000000000000000000000000000000000000000");
        root.set("brand", mapper.createObjectNode());
        root.set("notices", mapper.createArrayNode());
        root.set("requiredAssets", mapper.createArrayNode());

        return root.toString();
    }

    // ---- 提交 ----

    @Override
    @Transactional
    public SubmissionView receive(UUID submissionId, UUID userId, UUID contextId, UUID serviceId,
                                  UUID taskId, long assignmentVersion, String requestHash,
                                  String canonicalPayload, Instant receivedAt) {
        // 幂等：同 id 已存在
        Optional<SubmissionView> existing = findSubmission(submissionId);
        if (existing.isPresent()) {
            byte[] existingHash = jdbc.queryForObject(
                    "SELECT request_hash FROM offline_submission WHERE id = ?",
                    byte[].class, uuidBytes(submissionId));
            String existingHex = hex(existingHash);
            if (!existingHex.equals(requestHash)) {
                throw new Conflict("同 ID 不同内容的提交包，拒绝重复提交");
            }
            return existing.get();
        }

        jdbc.update("""
                INSERT INTO offline_submission (id, user_id, context_id, service_id, task_id,
                                               assignment_version, request_hash, canonical_payload,
                                               status, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', ?)
                """,
                uuidBytes(submissionId),
                uuidBytes(userId),
                uuidBytes(contextId),
                uuidBytes(serviceId),
                uuidBytes(taskId),
                assignmentVersion,
                hexToBytes(requestHash),
                canonicalPayload.getBytes(StandardCharsets.UTF_8),
                Timestamp.from(receivedAt));

        // 入队 SYNC_APPLY
        UUID jobId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO job_queue (id, kind, business_id, status, payload_json, attempts,
                                       max_attempts, next_run_at)
                VALUES (?, 'SYNC_APPLY', ?, 'READY', '{}', 0, 5, ?)
                """,
                uuidBytes(jobId),
                uuidBytes(submissionId),
                Timestamp.from(Instant.now()));

        return findSubmission(submissionId).orElseThrow(() -> new IllegalStateException("提交接收失败"));
    }

    @Override
    public Optional<SubmissionView> findSubmission(UUID submissionId) {
        String sql = """
                SELECT id, user_id, context_id, service_id, task_id, assignment_version,
                       status, result_json, received_at, applied_at
                FROM offline_submission WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<SubmissionView>empty();
            }
            return Optional.of(mapSubmission(rs));
        }, uuidBytes(submissionId));
    }

    // ---- 异常 ----

    @Override
    public List<SyncExceptionView> listExceptions(String status, UUID actorId) {
        String sql = """
                SELECT e.id, e.submission_id, e.kind, e.status, e.resolution_json,
                       e.resolved_at, e.created_at, s.service_id, s.task_id, s.assignment_version
                FROM sync_exception e
                JOIN offline_submission s ON s.id = e.submission_id
                """;
        StringBuilder sb = new StringBuilder(sql);
        List<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sb.append(" WHERE e.status = ?");
            args.add(status);
        }
        sb.append(" ORDER BY e.created_at DESC LIMIT 100");
        return jdbc.query(sb.toString(), (rs, i) -> mapException(rs), args.toArray());
    }

    @Override
    public Optional<SyncExceptionView> findException(UUID exceptionId) {
        String sql = """
                SELECT e.id, e.submission_id, e.kind, e.status, e.resolution_json,
                       e.resolved_at, e.created_at, s.service_id, s.task_id, s.assignment_version
                FROM sync_exception e
                JOIN offline_submission s ON s.id = e.submission_id
                WHERE e.id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<SyncExceptionView>empty();
            }
            return Optional.of(mapException(rs));
        }, uuidBytes(exceptionId));
    }

    @Override
    @Transactional
    public SyncExceptionView resolve(UUID exceptionId, UUID actorId, String decision,
                                     String reason, long expectedTaskRevision) {
        // 校验存在且 OPEN
        String current = jdbc.queryForObject(
                "SELECT status FROM sync_exception WHERE id = ?",
                String.class, uuidBytes(exceptionId));
        if (current == null) {
            throw new NotFound("同步异常不存在");
        }
        if (!"OPEN".equals(current)) {
            throw new Conflict("同步异常已处理");
        }

        String resolutionJson = "{\"decision\":\"" + decision + "\",\"reason\":\""
                + reason.replace("\"", "\\\"") + "\"}";
        jdbc.update("""
                UPDATE sync_exception
                SET status = 'RESOLVED', resolution_json = ?, resolved_by = ?, resolved_at = ?
                WHERE id = ?
                """, resolutionJson, uuidBytes(actorId), Timestamp.from(Instant.now()),
                uuidBytes(exceptionId));

        return findException(exceptionId).orElseThrow(() -> new IllegalStateException("异常处理失败"));
    }

    // ---- 离线资源 ----

    @Override
    public Optional<OfflineAssetView> findAsset(UUID assetId) {
        String sql = """
                SELECT id, object_key, sha256, byte_size, mime_type
                FROM offline_asset WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<OfflineAssetView>empty();
            }
            String role = jdbc.queryForObject(
                    "SELECT kind FROM config_release WHERE id = (SELECT release_id FROM offline_asset WHERE id = ?)",
                    String.class, uuidBytes(assetId));
            return Optional.of(new OfflineAssetView(
                    bytesToUuid(rs.getBytes("id")),
                    role != null ? role : "TEMPLATE",
                    hex(rs.getBytes("sha256")),
                    rs.getString("object_key"),
                    rs.getLong("byte_size"),
                    rs.getString("mime_type")));
        }, uuidBytes(assetId));
    }

    @Override
    public byte[] readAssetContent(UUID assetId) {
        Optional<OfflineAssetView> asset = findAsset(assetId);
        if (asset.isEmpty()) {
            throw new NotFound("离线资源不存在");
        }
        try {
            Path path = assetRoot.resolve(asset.get().objectKey()).normalize();
            return Files.readAllBytes(path);
        } catch (Exception e) {
            throw new NotFound("离线资源文件缺失");
        }
    }

    // ---- 映射 ----

    private static OfflineContextView mapContext(ResultSet rs) throws SQLException {
        return new OfflineContextView(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("store_id")),
                rs.getBytes("task_id") != null ? bytesToUuid(rs.getBytes("task_id")) : null,
                bytesToUuid(rs.getBytes("user_id")),
                rs.getString("device_id"),
                rs.getLong("permission_version"),
                hex(rs.getBytes("context_digest")),
                rs.getString("context_json"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("evidence_until").toInstant(),
                rs.getTimestamp("downloaded_at") != null ? rs.getTimestamp("downloaded_at").toInstant() : null,
                rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant() : null,
                rs.getTimestamp("created_at").toInstant(),
                0L);
    }

    private static SubmissionView mapSubmission(ResultSet rs) throws SQLException {
        return new SubmissionView(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("user_id")),
                bytesToUuid(rs.getBytes("context_id")),
                bytesToUuid(rs.getBytes("service_id")),
                bytesToUuid(rs.getBytes("task_id")),
                rs.getLong("assignment_version"),
                rs.getString("status"),
                rs.getString("result_json"),
                rs.getTimestamp("received_at").toInstant(),
                rs.getTimestamp("applied_at") != null ? rs.getTimestamp("applied_at").toInstant() : null);
    }

    private static SyncExceptionView mapException(ResultSet rs) throws SQLException {
        String resolutionJson = rs.getString("resolution_json");
        return new SyncExceptionView(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("submission_id")),
                rs.getString("kind"),
                rs.getString("status"),
                "同步冲突，需人工处理",
                bytesToUuid(rs.getBytes("service_id")),
                rs.getTimestamp("created_at").toInstant(),
                resolutionJson,
                rs.getTimestamp("resolved_at") != null ? rs.getTimestamp("resolved_at").toInstant() : null,
                rs.getBytes("task_id") != null ? bytesToUuid(rs.getBytes("task_id")) : null,
                rs.getLong("assignment_version"));
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
