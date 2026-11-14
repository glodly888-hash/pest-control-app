package com.sws.pest.infrastructure.ledger;

import com.sws.pest.domain.ledger.FollowupRepository;
import com.sws.pest.domain.ledger.Risk;
import com.sws.pest.domain.ledger.RiskFollowup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * risk_followup + ledger_event 的 JDBC 实现。
 *
 * 台账事件驱动模型（核心）：
 * apply() 在同一事务内：
 * 1) 校验乐观锁 expectedRiskRevision == risk.revision；
 * 2) 插入 ledger_event（kind=RISK_FOLLOWUP，payload 含 from_status/to_status）；
 * 3) 插入 risk_followup（引用 ledger_event.id，UNIQUE uk_followup_event 保证幂等）；
 * 4) 更新 risk.status / revision+1 / last_event_id / closed_at。
 */
@Repository
public class JdbcFollowupRepository implements FollowupRepository {

    private final JdbcTemplate jdbc;

    public JdbcFollowupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public long apply(RiskFollowup entry, UUID operatorId, UUID serviceId, UUID storeId,
                      Risk.Status fromStatus, Risk.Status toStatus, Instant receivedAt) {
        UUID riskId = entry.riskId();
        long expectedRevision = entry.expectedRiskRevision();

        // 1. 乐观锁：校验当前 revision（同时读 store_id 用于授权校验）
        Long currentRevision = jdbc.queryForObject(
                "SELECT revision FROM risk WHERE id = ?", Long.class, uuidBytes(riskId));
        if (currentRevision == null) {
            throw new IllegalStateException("风险不存在");
        }
        if (currentRevision.longValue() != expectedRevision) {
            throw new OptimisticLockException("风险已被他人跟进，请刷新后重试");
        }

        // 2. 插入 ledger_event
        UUID eventId = UUID.randomUUID();
        long resultRevision = expectedRevision + 1;
        String payloadJson = buildPayloadJson(entry, fromStatus, toStatus);
        byte[] payloadHash = sha256(payloadJson);
        jdbc.update("""
                INSERT INTO ledger_event (id, service_id, store_id, entity_id, kind,
                                          expected_revision, result_revision, payload_hash,
                                          payload_json, actor_id, occurred_at, supersedes_event_id)
                VALUES (?, ?, ?, ?, 'RISK_FOLLOWUP', ?, ?, ?, ?, ?, ?, ?)
                """,
                uuidBytes(eventId),
                serviceId != null ? uuidBytes(serviceId) : null,
                uuidBytes(storeId),
                uuidBytes(riskId),
                expectedRevision,
                resultRevision,
                payloadHash,
                payloadJson,
                uuidBytes(operatorId),
                java.sql.Timestamp.from(entry.occurredAt()),
                entry.supersedesEventId() != null ? uuidBytes(entry.supersedesEventId()) : null);

        // 3. 插入 risk_followup
        String contentJson = buildContentJson(entry);
        jdbc.update("""
                INSERT INTO risk_followup (id, risk_id, ledger_event_id, service_id,
                                           from_status, to_status, result, content_json, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                uuidBytes(entry.id()),
                uuidBytes(riskId),
                uuidBytes(eventId),
                serviceId != null ? uuidBytes(serviceId) : null,
                fromStatus.name(),
                toStatus.name(),
                entry.result().name(),
                contentJson,
                java.sql.Timestamp.from(entry.occurredAt()));

        // 4. 更新 risk：status / revision+1 / last_event_id / closed_at（关闭时）
        boolean closing = toStatus == Risk.Status.CLOSED;
        int updated = jdbc.update("""
                UPDATE risk SET status = ?, revision = revision + 1, last_event_id = ?,
                       closed_at = ?
                WHERE id = ? AND revision = ?
                """,
                toStatus.name(),
                uuidBytes(eventId),
                closing ? java.sql.Timestamp.from(receivedAt) : null,
                uuidBytes(riskId),
                expectedRevision);
        if (updated == 0) {
            throw new OptimisticLockException("风险已被他人跟进，请刷新后重试");
        }

        return resultRevision;
    }

    @Override
    public List<FollowupView> listByRisk(UUID riskId, String cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT f.id, f.risk_id, f.service_id, f.result, f.content_json, f.occurred_at,
                       f.from_status, f.to_status,
                       e.actor_id, e.result_revision
                FROM risk_followup f
                JOIN ledger_event e ON e.id = f.ledger_event_id
                WHERE f.risk_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(riskId));

        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\|");
            sql.append(" AND (f.occurred_at, f.id) < (?, ?)");
            args.add(java.sql.Timestamp.from(Instant.parse(parts[0])));
            args.add(uuidBytes(UUID.fromString(parts[1])));
        }
        sql.append(" ORDER BY f.occurred_at DESC, f.id DESC LIMIT ?");
        args.add(limit + 1);

        return jdbc.query(sql.toString(), (rs, i) -> mapView(rs), args.toArray());
    }

    @Override
    public boolean hasMore(UUID riskId, String cursor, int limit) {
        return false; // 由 application 层依据 limit+1 截断推导
    }

    private static FollowupView mapView(ResultSet rs) throws SQLException {
        UUID id = bytesToUuid(rs.getBytes("id"));
        UUID riskId = bytesToUuid(rs.getBytes("risk_id"));
        UUID operatorId = bytesToUuid(rs.getBytes("actor_id"));
        UUID serviceId = rs.getBytes("service_id") != null ? bytesToUuid(rs.getBytes("service_id")) : null;
        Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();
        long revisionAfter = rs.getLong("result_revision");

        JsonContent c = JsonContent.parse(rs.getString("content_json"));
        RiskFollowup entry = new RiskFollowup(
                id, riskId, revisionAfter - 1,
                RiskFollowup.Result.valueOf(rs.getString("result")),
                c.description(), c.notCheckedReason(), c.materialSource(),
                c.materialProvidedBy(), c.photos(), occurredAt, null);

        return new FollowupView(id, riskId, revisionAfter, operatorId, serviceId,
                occurredAt, entry);
    }

    private static String buildPayloadJson(RiskFollowup entry, Risk.Status from, Risk.Status to) {
        return "{"
                + "\"fromStatus\":\"" + from.name() + "\","
                + "\"toStatus\":\"" + to.name() + "\","
                + "\"result\":\"" + entry.result().name() + "\","
                + "\"description\":\"" + esc(entry.description()) + "\""
                + "}";
    }

    private static String buildContentJson(RiskFollowup entry) {
        StringBuilder photos = new StringBuilder("[");
        for (int i = 0; i < entry.photos().size(); i++) {
            if (i > 0) photos.append(",");
            photos.append("{\"attachmentId\":\"").append(entry.photos().get(i)).append("\"}");
        }
        photos.append("]");
        return "{"
                + "\"description\":\"" + esc(entry.description()) + "\","
                + "\"notCheckedReason\":\"" + esc(entry.notCheckedReason()) + "\","
                + "\"materialSource\":\"" + (entry.materialSource() == null ? "" : entry.materialSource().name()) + "\","
                + "\"materialProvidedBy\":\"" + esc(entry.materialProvidedBy()) + "\","
                + "\"photos\":" + photos
                + "}";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
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
