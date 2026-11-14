package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 现场台账提交输入（对应 openapi FieldCommitInput）。
 */
public record FieldCommitInput(
        @NotNull Long draftRevision) {
}
