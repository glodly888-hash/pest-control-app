package com.sws.pest.infrastructure.identity;

import com.sws.pest.domain.identity.AppUser;
import com.sws.pest.domain.identity.AppUserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

/**
 * app_user 的 JDBC 实现。BINARY(16) 主键用 UUID 的 RFC 字节序绑定（UUID_TO_BIN 语义）。
 * 只读端口，写操作（新建员工）属管理后台范畴，不在首期范围。
 */
@Repository
public class JdbcAppUserRepository implements AppUserRepository {

    private final JdbcTemplate jdbc;

    public JdbcAppUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AppUser> findById(UUID id) {
        String sql = "SELECT id, org_id, login_name, name, status, permission_version " +
                "FROM app_user WHERE id = ?";
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<AppUser>empty();
            }
            return Optional.of(mapUser(rs.getBytes("id"), rs.getBytes("org_id"),
                    rs.getString("login_name"), rs.getString("name"),
                    rs.getString("status"), rs.getLong("permission_version")));
        }, uuidBytes(id));
    }

    @Override
    public Optional<AppUser> findByLoginName(String loginName) {
        String sql = "SELECT id, org_id, login_name, name, status, permission_version " +
                "FROM app_user WHERE login_name = ?";
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<AppUser>empty();
            }
            return Optional.of(mapUser(rs.getBytes("id"), rs.getBytes("org_id"),
                    rs.getString("login_name"), rs.getString("name"),
                    rs.getString("status"), rs.getLong("permission_version")));
        }, loginName);
    }

    @Override
    public Optional<Credentials> findCredentials(String companyCode, String account) {
        // companyCode 定位 customer 主体（单公司部署，仅校验存在），account 定位员工登录名。
        // 首期不提供多公司 SaaS 入驻，故 companyCode 与 app_user 不做跨表路由，
        // 只校验 customer.code 存在。查不到账号统一返回 empty，由调用方抛通用 401。
        String sql = """
                SELECT u.id, u.org_id, u.login_name, u.name, u.status,
                       u.permission_version, u.password_hash
                FROM app_user u
                JOIN org_unit o ON o.id = u.org_id
                WHERE u.login_name = ? AND u.status <> 'ARCHIVED'
                  AND EXISTS (SELECT 1 FROM customer c WHERE c.code = ? AND c.status = 'ACTIVE')
                """;
        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.<Credentials>empty();
            }
            AppUser user = mapUser(rs.getBytes("id"), rs.getBytes("org_id"),
                    rs.getString("login_name"), rs.getString("name"),
                    rs.getString("status"), rs.getLong("permission_version"));
            return Optional.of(new Credentials(user, rs.getString("password_hash")));
        }, account, companyCode);
    }

    @Override
    public void save(AppUser user) {
        throw new UnsupportedOperationException("首期不提供员工写入（属管理后台范围）");
    }

    private static AppUser mapUser(byte[] id, byte[] orgId, String loginName,
                                   String name, String status, long permissionVersion) {
        // 公司 ID 首期简化：以 org_id 作为 companyId 占位（真实应追溯 customer）。
        // 注意：org_id 是区域/团队，不是公司；companyId 的正确来源需在 scope 查询时 join。
        return new AppUser(bytesToUuid(id), bytesToUuid(orgId), loginName, name,
                loginName, "ACTIVE".equals(status), permissionVersion);
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
