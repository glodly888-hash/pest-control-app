package com.sws.pest.domain.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * 员工仓储端口。由 pest-infrastructure 用 JDBC 实现，
 * pest-application 只依赖此接口，不直接访问数据库。
 */
public interface AppUserRepository {

    Optional<AppUser> findById(UUID id);

    Optional<AppUser> findByLoginName(String loginName);

    /**
     * 按登录主体定位员工并返回其密码哈希。
     * companyCode 用于定位客户主体（customer.code），account 对应 app_user.login_name。
     * 返回 record 避免把密码哈希暴露进 AppUser 领域对象。
     */
    Optional<Credentials> findCredentials(String companyCode, String account);

    void save(AppUser user);

    /**
     * 登录凭证查询结果：用户领域对象 + 加密后的密码哈希（BCrypt）。
     */
    record Credentials(AppUser user, String passwordHash) {
    }
}
