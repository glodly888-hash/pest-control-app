package com.sws.pest.domain.identity;

/**
 * 密码校验端口。由 infrastructure 用 BCrypt 实现。
 * application 层不直接依赖 spring-security，保持领域纯净。
 */
public interface PasswordVerifier {

    /**
     * 校验明文密码与哈希是否匹配。
     */
    boolean matches(String rawPassword, String encodedHash);
}
