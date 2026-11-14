package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求体（对应 openapi LoginInput）。
 */
public record LoginRequest(
        @NotBlank @Size(max = 40) String companyCode,
        @NotBlank @Size(max = 100) String account,
        @NotBlank @Size(max = 256) String password,
        @NotBlank @Size(max = 36) String deviceId,
        @Size(max = 40) String appVersion) {
}
