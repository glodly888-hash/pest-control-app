package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌输入（对应 openapi RefreshInput）。
 */
public record RefreshInput(
        @NotBlank String refreshToken,
        String deviceId) {
}
