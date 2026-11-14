package com.sws.pest.infrastructure.confirmation;

import com.sws.pest.domain.confirmation.ConfirmationException;
import com.sws.pest.domain.confirmation.ConfirmationExceptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * confirmation_exception 的 JDBC 实现。
 *
 * 登记缺席/拒签时，把 service.phase 从 FROZEN 置 AWAITING_CONFIRMATION。
 */
@Repository
public class JdbcConfirmationExceptionRepository implements ConfirmationExceptionRepository {

    private final JdbcTemplate jdbc;

    public JdbcConfirmationExceptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean serviceFrozen(UUID serviceId) {
        String phase = jdbc.query(
                "SELECT phase FROM service WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return rs.getString("phase");
                }, uuidBytes(serviceId));
        return "FROZEN".equals(phase);
    }

    @Override
    @Transactional
    public ConfirmationException save(ConfirmationException exception) {
        jdbc.update("""
                INSERT INTO confirmation_exception (id, service_id, snapshot_id, reason_code,
                                                    reason, actor_id, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                uuidBytes(exception.id()),
                uuidBytes(exception.serviceId()),
                exception.snapshotId() != null ? uuidBytes(exception.snapshotId()) : null,
                exception.reasonCode(),
                exception.reason(),
                uuidBytes(exception.actorId()),
                java.sql.Timestamp.from(exception.occurredAt()));

        // 任务置待确认
        jdbc.update("""
                UPDATE service SET phase = 'AWAITING_CONFIRMATION'
                WHERE id = ? AND phase = 'FROZEN'
                """, uuidBytes(exception.serviceId()));

        return findById(exception.id());
    }

    private ConfirmationException findById(UUID id) {
        String sql = """
                SELECT id, service_id, snapshot_id, reason_code, reason, actor_id,
                       occurred_at, created_at
                FROM confirmation_exception WHERE id = ?
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return null;
            }
            return map(rs);
        }, uuidBytes(id));
    }

    private static ConfirmationException map(ResultSet rs) throws SQLException {
        return new ConfirmationException(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("service_id")),
                rs.getBytes("snapshot_id") != null ? bytesToUuid(rs.getBytes("snapshot_id")) : null,
                rs.getString("reason_code"),
                rs.getString("reason"),
                bytesToUuid(rs.getBytes("actor_id")),
                rs.getTimestamp("occurred_at").toInstant(),
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
}
