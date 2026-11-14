package com.sws.pest.infrastructure.identity;

import com.sws.pest.domain.identity.PasswordVerifier;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码校验实现。application 层只依赖 PasswordVerifier 端口。
 */
@Component
public class BCryptPasswordVerifier implements PasswordVerifier {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null) {
            return false;
        }
        // BCrypt 哈希格式校验，避免对非 BCrypt 值抛异常
        if (!encodedHash.startsWith("$2")) {
            return false;
        }
        try {
            return encoder.matches(rawPassword, encodedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
