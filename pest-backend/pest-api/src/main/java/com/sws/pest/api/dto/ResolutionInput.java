package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 处理同步异常（对应 openapi ResolutionInput）。
 */
public record ResolutionInput(
        @NotBlank String decision,
        @NotBlank String reason,
        @NotNull Long expectedTaskRevision) {
}
