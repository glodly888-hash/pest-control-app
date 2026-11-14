package com.sws.pest.infrastructure.ledger;

import com.sws.pest.domain.ledger.Risk;
import com.sws.pest.domain.ledger.RiskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * risk 表的 JDBC 实现。
 *
 * content_json 内保存 description / suggestion / photos 等完整当前状态，
 * 这里用 Jackson 读回 JSON 字段（首期不做 JCS 规范化，直接透传）。
 * 排序按 (due_date, id)，游标为 base64(dueDate|id)。
 */
@Repository
public class JdbcRiskRepository implements RiskRepository {

    private final JdbcTemplate jdbc;

    public JdbcRiskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String RISK_SELECT = """
            SELECT r.id, r.store_id, r.risk_type_id, r.pest_type_ids_json, r.area_id,
                   r.position, r.status, r.revision, r.responsible_party,
                   r.followup_user_id, r.due_date, r.found_at, r.closed_at, r.content_json
            FROM risk r
            """;

    @Override
    public List<Risk> listByStore(UUID storeId, Risk.Status status, String cursor, int limit) {
        StringBuilder sql = new StringBuilder(RISK_SELECT);
        sql.append(" WHERE r.store_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(storeId));

        if (status != null) {
            sql.append(" AND r.status = ?");
            args.add(status.name());
        }
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\|");
            sql.append(" AND (r.due_date, r.id) > (?, ?)");
            // due_date 可空，游标里用空串表示 NULL
            args.add(parts[0].isEmpty() ? null : java.sql.Date.valueOf(parts[0]));
            args.add(uuidBytes(UUID.fromString(parts[1])));
        }
        sql.append(" ORDER BY r.due_date IS NULL, r.due_date, r.id LIMIT ?");
        args.add(limit + 1);

        return jdbc.query(sql.toString(), (rs, i) -> mapRisk(rs), args.toArray());
    }

    @Override
    public boolean hasMore(UUID storeId, Risk.Status status, String cursor, int limit) {
        // 复用 listByStore 的 limit+1 语义，这里直接再查一次判断溢出。
        // 为避免重复映射开销，首期直接由 listByStore 在 application 层截断。
        // 本方法仅作占位，实际 hasMore 由 controller/service 依据列表长度推导。
        return false;
    }

    @Override
    public Optional<Risk> findById(UUID riskId) {
        List<Risk> list = jdbc.query(RISK_SELECT + " WHERE r.id = ?",
                (rs, i) -> mapRisk(rs), uuidBytes(riskId));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public boolean exists(UUID riskId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk WHERE id = ?", Integer.class, uuidBytes(riskId));
        return c != null && c > 0;
    }

    @Override
    public long currentRevision(UUID riskId) {
        Long rev = jdbc.queryForObject(
                "SELECT revision FROM risk WHERE id = ?", Long.class, uuidBytes(riskId));
        return rev == null ? -1 : rev;
    }

    @Override
    public boolean storeExists(UUID storeId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM store WHERE id = ?", Integer.class, uuidBytes(storeId));
        return c != null && c > 0;
    }

    private static Risk mapRisk(ResultSet rs) throws SQLException {
        // 首期简化：content_json 用简单字符串解析，不引入 Jackson 依赖到 infra。
        // 直接从 content_json 提取 description/suggestion/photos（用正则/简单 JSON 工具）。
        String contentJson = rs.getString("content_json");
        JsonContent c = JsonContent.parse(contentJson);

        String dueDate = rs.getDate("due_date") != null
                ? rs.getDate("due_date").toLocalDate().toString() : null;

        return new Risk(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("store_id")),
                bytesToUuid(rs.getBytes("risk_type_id")),
                c.pestTypeIds(),
                rs.getBytes("area_id") != null ? bytesToUuid(rs.getBytes("area_id")) : null,
                c.floor(),
                rs.getString("position"),
                c.description(),
                c.suggestion(),
                rs.getString("responsible_party"),
                null, // contact_cipher 加密，首期不解析
                bytesToUuid(rs.getBytes("followup_user_id")),
                dueDate,
                Risk.Status.valueOf(rs.getString("status")),
                rs.getLong("revision"),
                rs.getTimestamp("found_at").toInstant(),
                rs.getTimestamp("closed_at") != null ? rs.getTimestamp("closed_at").toInstant() : null,
                c.photos());
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
