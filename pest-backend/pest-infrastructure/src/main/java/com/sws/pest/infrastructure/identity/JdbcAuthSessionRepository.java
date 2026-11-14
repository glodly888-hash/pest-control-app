package com.sws.pest.infrastructure.identity;

import com.sws.pest.domain.identity.AuthSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * auth_session 的 JDBC 实现。refresh_hash 存 BINARY(32)（SHA-256 摘要）。
 */
@Repository
public class JdbcAuthSessionRepository implements AuthSessionRepository {

    private final JdbcTemplate jdbc;

    public JdbcAuthSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID sessionId, UUID userId, String deviceId, byte[] refreshHash,
                       long permissionVersion, Instant expiresAt) {
        String sql = """
                INSERT INTO auth_session (id, user_id, device_id, refresh_hash,
                                          permission_version, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        jdbc.update(sql, uuidBytes(sessionId), uuidBytes(userId), deviceId, refreshHash,
                permissionVersion, Timestamp.from(expiresAt), Timestamp.from(Instant.now()));
    }

    @Override
    public Optional<AuthSession> findByRefreshHash(byte[] refreshHash) {
        String sql = """
                SELECT id, user_id, device_id, permission_version, expires_at,
                       (revoked_at IS NOT NULL) AS revoked
                FROM auth_session WHERE refresh_hash = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<AuthSession>empty();
            }
            return Optional.of(mapRow(rs));
        }, refreshHash);
    }

    private static AuthSession mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AuthSession(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("user_id")),
                rs.getString("device_id"),
                rs.getLong("permission_version"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getBoolean("revoked"));
    }

    @Override
    public Optional<AuthSession> findById(UUID sessionId) {
        String sql = """
                SELECT id, user_id, device_id, permission_version, expires_at,
                       (revoked_at IS NOT NULL) AS revoked
                FROM auth_session WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<AuthSession>empty();
            }
            return Optional.of(mapRow(rs));
        }, uuidBytes(sessionId));
    }

    @Override
    public void revoke(UUID sessionId) {
        jdbc.update("UPDATE auth_session SET revoked_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), uuidBytes(sessionId));
    }

    @Override
    public boolean rotate(UUID sessionId, byte[] newRefreshHash, Instant newExpiresAt) {
        String sql = """
                UPDATE auth_session
                SET refresh_hash = ?, expires_at = ?, revoked_at = NULL, last_seen_at = ?
                WHERE id = ? AND revoked_at IS NULL
                """;
        return jdbc.update(sql, newRefreshHash, Timestamp.from(newExpiresAt),
                Timestamp.from(Instant.now()), uuidBytes(sessionId)) == 1;
    }

    @Override
    public void touch(UUID sessionId, Instant lastSeenAt) {
        jdbc.update("UPDATE auth_session SET last_seen_at = ? WHERE id = ?",
                Timestamp.from(lastSeenAt), uuidBytes(sessionId));
    }

    static byte[] uuidBytes(UUID uuid) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    static UUID bytesToUuid(byte[] bytes) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
