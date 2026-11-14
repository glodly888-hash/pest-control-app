package com.sws.pest.application.identity;

import com.sws.pest.domain.identity.AppUser;
import com.sws.pest.domain.identity.AppUserRepository;
import com.sws.pest.domain.identity.AuthSessionRepository;
import com.sws.pest.domain.identity.PasswordVerifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 登录用例服务（身份域）。
 * 真实实现：定位主体 → 校验密码 → 签发会话（JWT access + 可轮换 refresh）。
 */
@Service
public class AuthenticateService {

    private final AppUserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final PasswordVerifier passwordVerifier;
    private final SecureRandom secureRandom = new SecureRandom();

    /** access token 有效期（15 分钟），refresh 有效期（30 天）。 */
    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);

    public AuthenticateService(AppUserRepository userRepository,
                               AuthSessionRepository sessionRepository,
                               PasswordVerifier passwordVerifier) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordVerifier = passwordVerifier;
    }

    /**
     * 登录。错误统一抛出 AuthenticationException，不暴露账号是否存在。
     */
    @Transactional
    public LoginResult login(String companyCode, String account, String password, String deviceId) {
        AppUserRepository.Credentials cred = userRepository.findCredentials(companyCode, account)
                .orElseThrow(() -> new AuthenticationException("invalid credentials"));

        AppUser user = cred.user();
        if (!user.active() || !passwordVerifier.matches(password, cred.passwordHash())) {
            throw new AuthenticationException("invalid credentials");
        }

        UUID sessionId = UUID.randomUUID();
        String refreshToken = newRefreshToken();
        byte[] refreshHash = sha256(refreshToken);

        Instant now = Instant.now();
        sessionRepository.create(sessionId, user.id(), deviceId, refreshHash,
                user.permissionVersion(), now.plus(REFRESH_TTL));

        return new LoginResult(user, sessionId, refreshToken,
                now.plus(ACCESS_TTL), now.plus(REFRESH_TTL));
    }

    /**
     * 刷新令牌。校验 refresh 摘要 → 未撤销未过期 → 轮换 → 签发新 access。
     * 返回新会话令牌与用户，供 API 层签发 JWT。
     */
    @Transactional
    public RefreshResult refresh(String refreshToken, String deviceId) {
        byte[] refreshHash = sha256(refreshToken);
        AuthSessionRepository.AuthSession session = sessionRepository.findByRefreshHash(refreshHash)
                .orElseThrow(() -> new AuthenticationException("invalid refresh token"));

        if (session.revoked()) {
            throw new AuthenticationException("invalid refresh token");
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            throw new AuthenticationException("refresh token expired");
        }
        if (deviceId != null && !deviceId.equals(session.deviceId())) {
            throw new AuthenticationException("device mismatch");
        }

        AppUser user = userRepository.findById(session.userId())
                .orElseThrow(() -> new AuthenticationException("user not found"));

        String newRefreshToken = newRefreshToken();
        byte[] newRefreshHash = sha256(newRefreshToken);
        Instant refreshExpiresAt = Instant.now().plus(REFRESH_TTL);

        boolean rotated = sessionRepository.rotate(session.id(), newRefreshHash, refreshExpiresAt);
        if (!rotated) {
            throw new AuthenticationException("invalid refresh token");
        }

        Instant accessExpiresAt = Instant.now().plus(ACCESS_TTL);
        return new RefreshResult(user, session.id(), newRefreshToken,
                accessExpiresAt, refreshExpiresAt);
    }

    /**
     * 注销当前会话。
     */
    @Transactional
    public void logout(UUID sessionId) {
        sessionRepository.revoke(sessionId);
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 登录结果（不含 access token，由 API 层用 JWT 签发）。
     */
    public record LoginResult(AppUser user, UUID sessionId, String refreshToken,
                              Instant accessExpiresAt, Instant refreshExpiresAt) {
    }

    /**
     * 刷新结果（不含 access token，由 API 层用 JWT 签发）。
     */
    public record RefreshResult(AppUser user, UUID sessionId, String refreshToken,
                                Instant accessExpiresAt, Instant refreshExpiresAt) {
    }

    /** 认证失败异常，API 层统一映射为 401。 */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
