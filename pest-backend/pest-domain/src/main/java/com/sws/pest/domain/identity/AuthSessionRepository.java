package com.sws.pest.domain.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 认证会话仓储端口。access token 用 JWT 无状态校验，
 * refresh token 摘要落 auth_session 表（refresh_hash BINARY(32) 存 SHA-256）。
 */
public interface AuthSessionRepository {

    /**
     * 创建会话，保存 refresh token 的 SHA-256 摘要。
     */
    void create(UUID sessionId, UUID userId, String deviceId, byte[] refreshHash,
                long permissionVersion, Instant expiresAt);

    /**
     * 按 refresh token 摘要查找会话（用于刷新与撤销）。
     */
    Optional<AuthSession> findByRefreshHash(byte[] refreshHash);

    /**
     * 按会话 ID 查找会话（logout 用）。
     */
    Optional<AuthSession> findById(UUID sessionId);

    void revoke(UUID sessionId);

    /**
     * 轮换 refresh token：更新摘要与有效期（旧摘要立即失效）。
     * 返回是否更新成功（影响行数为 1）。
     */
    boolean rotate(UUID sessionId, byte[] newRefreshHash, Instant newExpiresAt);

    void touch(UUID sessionId, Instant lastSeenAt);

    /**
     * 认证会话（不含明文 refresh token）。
     */
    record AuthSession(UUID id, UUID userId, String deviceId,
                       long permissionVersion, Instant expiresAt, boolean revoked) {
    }
}
