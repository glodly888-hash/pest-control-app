package com.sws.pest.infrastructure.masterdata;

import com.sws.pest.domain.masterdata.Store;
import com.sws.pest.domain.masterdata.StoreRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * store 的 JDBC 实现。授权门店按 user_scope 过滤：
 * scope_kind=STORE 直接命中 store_id；scope_kind=ORG 命中 org 下所有门店。
 */
@Repository
public class JdbcStoreRepository implements StoreRepository {

    private final JdbcTemplate jdbc;

    public JdbcStoreRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page listByScope(UUID userId, String keyword, String cursor, int limit) {
        // 数据范围：user_scope 中 STORE 直接授权 + ORG 授权的 org 下门店。
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT s.id, s.customer_id, s.brand_id, s.code, s.name,
                       s.address, s.timezone, b.name AS brand_name
                FROM store s
                JOIN user_scope us ON us.user_id = ?
                    AND (us.scope_kind = 'STORE' AND us.store_id = s.id
                         OR us.scope_kind = 'ORG' AND us.org_id = s.org_id)
                LEFT JOIN brand b ON b.id = s.brand_id
                WHERE s.status = 'ACTIVE'
                """);
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(userId));

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (s.code LIKE ? OR s.name LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }

        // 游标分页：按 (code, id) 排序，cursor 为 base64(code|id)。
        if (cursor != null && !cursor.isBlank()) {
            sql.append(" AND (s.code, s.id) > (?, ?)");
            String[] parts = cursor.split("\\|");
            args.add(parts[0]);
            args.add(uuidBytes(UUID.fromString(parts[1])));
        }

        sql.append(" ORDER BY s.code, s.id LIMIT ?");
        args.add(limit + 1); // 多取一条判断 hasMore

        List<Store> stores = jdbc.query(sql.toString(), rs -> {
            List<Store> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapStore(rs));
            }
            return list;
        }, args.toArray());

        boolean hasMore = stores.size() > limit;
        if (hasMore) {
            stores = stores.subList(0, limit);
        }
        String nextCursor = null;
        if (hasMore && !stores.isEmpty()) {
            Store last = stores.get(stores.size() - 1);
            nextCursor = last.code() + "|" + last.id();
        }
        return new Page(stores, nextCursor, hasMore);
    }

    @Override
    public Optional<Store> findById(UUID storeId) {
        String sql = """
                SELECT s.id, s.customer_id, s.brand_id, s.code, s.name,
                       s.address, s.timezone, b.name AS brand_name
                FROM store s
                LEFT JOIN brand b ON b.id = s.brand_id
                WHERE s.id = ? AND s.status = 'ACTIVE'
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<Store>empty();
            }
            return Optional.of(mapStore(rs));
        }, uuidBytes(storeId));
    }

    @Override
    public Store findDetailById(UUID storeId) {
        String sql = """
                SELECT s.id, s.customer_id, s.brand_id, s.code, s.name,
                       s.address, s.timezone, b.name AS brand_name,
                       s.org_id AS region_id, s.latitude, s.longitude, s.revision
                FROM store s
                LEFT JOIN brand b ON b.id = s.brand_id
                WHERE s.id = ? AND s.status = 'ACTIVE'
                """;
        List<Store> list = jdbc.query(sql, (rs, i) -> mapStoreDetail(rs), uuidBytes(storeId));
        if (list.isEmpty()) {
            return null;
        }
        Store base = list.get(0);
        // 分区列表
        List<Store.Area> areas = jdbc.query(
                "SELECT id, name, floor FROM store_area WHERE store_id = ? AND status = 'ACTIVE' ORDER BY code",
                (rs, i) -> new Store.Area(
                        bytesToUuid(rs.getBytes("id")),
                        rs.getString("name"),
                        rs.getString("floor")),
                uuidBytes(storeId));
        // contact_cipher 首期不解密，contactName/contactPhone 返回空
        return new Store(base.id(), base.customerId(), base.brandId(), base.code(),
                base.name(), base.address(), base.timezone(), base.brandName(),
                base.regionId(), base.latitude(), base.longitude(), base.revision(),
                null, null, areas);
    }

    private static Store mapStore(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Store(
                bytesToUuid(rs.getBytes("id")),
                rs.getBytes("customer_id") != null ? bytesToUuid(rs.getBytes("customer_id")) : null,
                rs.getBytes("brand_id") != null ? bytesToUuid(rs.getBytes("brand_id")) : null,
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("timezone"),
                rs.getString("brand_name"),
                null, null, null, 0L, null, null, List.of());
    }

    private static Store mapStoreDetail(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Store(
                bytesToUuid(rs.getBytes("id")),
                rs.getBytes("customer_id") != null ? bytesToUuid(rs.getBytes("customer_id")) : null,
                rs.getBytes("brand_id") != null ? bytesToUuid(rs.getBytes("brand_id")) : null,
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("timezone"),
                rs.getString("brand_name"),
                rs.getBytes("region_id") != null ? bytesToUuid(rs.getBytes("region_id")) : null,
                rs.getBigDecimal("latitude"),
                rs.getBigDecimal("longitude"),
                rs.getLong("revision"),
                null, null, List.of());
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
