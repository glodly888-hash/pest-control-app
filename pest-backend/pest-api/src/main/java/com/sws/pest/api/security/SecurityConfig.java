package com.sws.pest.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全相关 bean 装配。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public JwtService jwtService(@Value("${pest.jwt.secret}") String secret) {
        return new JwtService(secret);
    }
}
