package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 签后未发布修订输入（对应 openapi RevisionInput）。
 */
public record RevisionInput(
        @NotBlank String reason,
        @NotBlank String fromSnapshotId) {
}
