package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 报告更正输入（对应 openapi CorrectionInput）。
 */
public record CorrectionInput(
        @NotBlank String id,
        @NotBlank String reason) {
}
