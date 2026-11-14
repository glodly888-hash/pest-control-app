package com.sws.pest.infrastructure.masterdata;

import com.sws.pest.domain.masterdata.Facility;
import com.sws.pest.domain.masterdata.FacilityRepository;
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
 * facility 的 JDBC 实现。
 * 游标按 (code, id) 稳定排序。typeName 由 facility_type_id join 字典获得，
 * 首期字典配置未落地，typeName 返回 null（客户端用 typeId 本地匹配字典）。
 */
@Repository
public class JdbcFacilityRepository implements FacilityRepository {

    private final JdbcTemplate jdbc;

    public JdbcFacilityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page listByStore(UUID storeId, UUID typeId, String status, String cursor, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, store_id, code, facility_type_id AS type_id,
                       area_id, floor, position, status, revision, updated_at
                FROM facility
                WHERE store_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(storeId));

        if (typeId != null) {
            sql.append(" AND facility_type_id = ?");
            args.add(uuidBytes(typeId));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (cursor != null && !cursor.isBlank()) {
            sql.append(" AND (code, id) > (?, ?)");
            String[] parts = cursor.split("\\|");
            args.add(parts[0]);
            args.add(uuidBytes(UUID.fromString(parts[1])));
        }

        sql.append(" ORDER BY code, id LIMIT ?");
        args.add(limit + 1);

        List<Facility> facilities = jdbc.query(sql.toString(), (rs, i) -> mapFacility(rs),
                args.toArray());

        boolean hasMore = facilities.size() > limit;
        if (hasMore) {
            facilities = facilities.subList(0, limit);
        }
        String nextCursor = null;
        if (hasMore && !facilities.isEmpty()) {
            Facility last = facilities.get(facilities.size() - 1);
            nextCursor = last.code() + "|" + last.id();
        }
        return new Page(facilities, nextCursor, hasMore);
    }

    private static Facility mapFacility(ResultSet rs) throws SQLException {
        return new Facility(
                bytesToUuid(rs.getBytes("id")),
                bytesToUuid(rs.getBytes("store_id")),
                rs.getString("code"),
                bytesToUuid(rs.getBytes("type_id")),
                null, // typeName 首期不 join 字典，返回 null
                rs.getBytes("area_id") != null ? bytesToUuid(rs.getBytes("area_id")) : null,
                rs.getString("floor"),
                rs.getString("position"),
                rs.getString("status"),
                rs.getLong("revision"),
                rs.getTimestamp("updated_at") != null
                        ? rs.getTimestamp("updated_at").toInstant() : null);
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
