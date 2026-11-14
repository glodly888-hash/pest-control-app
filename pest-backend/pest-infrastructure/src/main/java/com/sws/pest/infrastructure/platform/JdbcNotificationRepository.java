package com.sws.pest.infrastructure.platform;

import com.sws.pest.domain.platform.Notification;
import com.sws.pest.domain.platform.NotificationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * notification 表的 JDBC 实现。
 *
 * payload_json 结构约定：
 * { "type": "TASK_CHANGED", "title": "...", "body": "...",
 *   "resourceType": "TASK", "resourceId": "uuid" }
 */
@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    private final JdbcTemplate jdbc;

    public JdbcNotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT = """
            SELECT id, kind, payload_json, read_at, created_at
            FROM notification
            """;

    @Override
    public Page listByUser(UUID userId, String cursor, int limit) {
        StringBuilder sql = new StringBuilder(SELECT);
        sql.append(" WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(userId));

        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\|");
            // cursor 格式：epochMillis|uuid
            sql.append(" AND (created_at, id) < (?, ?)");
            args.add(java.sql.Timestamp.from(Instant.ofEpochMilli(Long.parseLong(parts[0]))));
            args.add(uuidBytes(UUID.fromString(parts[1])));
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(limit + 1);

        List<Notification> all = jdbc.query(sql.toString(), (rs, i) -> map(rs), args.toArray());
        boolean hasMore = all.size() > limit;
        List<Notification> items = hasMore ? all.subList(0, limit) : all;
        String nextCursor = "";
        if (hasMore && !items.isEmpty()) {
            Notification last = items.get(items.size() - 1);
            nextCursor = last.createdAt().toEpochMilli() + "|" + last.id();
        }
        return new Page(items, nextCursor, hasMore);
    }

    @Override
    public boolean markRead(UUID userId, UUID notificationId) {
        int updated = jdbc.update(
                "UPDATE notification SET read_at = NOW(6) WHERE id = ? AND user_id = ? AND read_at IS NULL",
                uuidBytes(notificationId), uuidBytes(userId));
        // 已读或不存在都视为幂等成功；但需区分「不属于该用户」→ 由上层判断
        if (updated > 0) {
            return true;
        }
        // 已读（read_at 非空且属于该用户）也算成功
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE id = ? AND user_id = ?",
                Integer.class, uuidBytes(notificationId), uuidBytes(userId));
        return exists != null && exists > 0;
    }

    private Notification map(ResultSet rs) throws SQLException {
        UUID id = bytesToUuid(rs.getBytes("id"));
        String kind = rs.getString("kind");
        String payload = rs.getString("payload_json");
        boolean read = rs.getTimestamp("read_at") != null;
        Instant createdAt = rs.getTimestamp("created_at").toInstant();

        String title = "";
        String body = "";
        String resourceType = "";
        UUID resourceId = null;
        try {
            var node = new tools.jackson.databind.ObjectMapper().readTree(payload);
            if (node != null) {
                title = node.has("title") ? node.get("title").asText("") : "";
                body = node.has("body") ? node.get("body").asText("") : "";
                resourceType = node.has("resourceType") ? node.get("resourceType").asText("") : "";
                if (node.has("resourceId") && !node.get("resourceId").isNull()) {
                    resourceId = UUID.fromString(node.get("resourceId").asText());
                }
            }
        } catch (Exception ignored) {
            // 容错
        }
        return new Notification(id, kind, title, body, resourceType, resourceId, read, createdAt);
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
