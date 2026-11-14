package com.sws.pest.infrastructure.confirmation;

import com.sws.pest.domain.confirmation.PreviewSession;
import com.sws.pest.domain.confirmation.PreviewSessionRepository;
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
 * preview_session 的 JDBC 实现。
 */
@Repository
public class JdbcPreviewSessionRepository implements PreviewSessionRepository {

    private final JdbcTemplate jdbc;

    public JdbcPreviewSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean snapshotExists(UUID snapshotId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_snapshot WHERE id = ?", Integer.class, uuidBytes(snapshotId));
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public PreviewSession save(PreviewSession session) {
        jdbc.update("""
                INSERT INTO preview_session (id, snapshot_id, user_id, content_hash, started_at,
                                             completed_at, visible_duration_ms, is_offline, device_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                uuidBytes(session.id()),
                uuidBytes(session.snapshotId()),
                uuidBytes(session.userId()),
                hexToBytes(session.contentHash()),
                java.sql.Timestamp.from(session.startedAt()),
                java.sql.Timestamp.from(session.completedAt()),
                session.visibleDurationMs(),
                session.isOffline(),
                session.deviceId());

        return findById(session.id()).orElse(session);
    }

    @Override
    public Optional<PreviewSession> findById(UUID sessionId) {
        String sql = """
                SELECT id, snapshot_id, user_id, content_hash, started_at, completed_at,
                       visible_duration_ms, is_offline, device_id, created_at
                FROM preview_session WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<PreviewSession>empty();
            }
            return Optional.of(mapSession(rs));
        }, uuidBytes(sessionId));
    }

    private static PreviewSession mapSession(ResultSet rs) throws SQLException {
        return new PreviewSession(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("snapshot_id")),
                bytesToUuid(rs.getBytes("user_id")),
                hex(rs.getBytes("content_hash")),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("completed_at").toInstant(),
                rs.getLong("visible_duration_ms"),
                rs.getBoolean("is_offline"),
                rs.getString("device_id"),
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
