package com.sws.pest.api.dto;

import java.util.List;

/**
 * 会话响应（对应 openapi Session）。
 */
public record SessionResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        String accessExpiresAt,
        String refreshExpiresAt,
        String sessionId,
        UserResponse user) {
}
