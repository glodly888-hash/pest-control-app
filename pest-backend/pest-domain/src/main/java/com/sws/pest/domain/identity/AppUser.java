package com.sws.pest.domain.identity;

import java.util.UUID;

/**
 * 员工（app_user）。主键统一 BINARY(16)，对应接口 UUIDv4。
 * 数据范围由 user_scope + ScopePolicy 决定，本对象不存明文密码。
 */
public final class AppUser {

    private final UUID id;
    private final UUID companyId;
    private final String loginName;
    private final String name;
    private final String employeeCode;
    private final boolean active;
    private final long permissionVersion;

    public AppUser(UUID id, UUID companyId, String loginName, String name,
                   String employeeCode, boolean active, long permissionVersion) {
        this.id = id;
        this.companyId = companyId;
        this.loginName = loginName;
        this.name = name;
        this.employeeCode = employeeCode;
        this.active = active;
        this.permissionVersion = permissionVersion;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public String loginName() {
        return loginName;
    }

    public String name() {
        return name;
    }

    public String employeeCode() {
        return employeeCode;
    }

    public boolean active() {
        return active;
    }

    public long permissionVersion() {
        return permissionVersion;
    }
}
