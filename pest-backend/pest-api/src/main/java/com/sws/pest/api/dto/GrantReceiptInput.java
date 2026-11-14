package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 确认离线资料完整下载（对应 openapi GrantReceipt）。
 */
public record GrantReceiptInput(
        @NotBlank String contextId,
        @NotNull List<String> assetHashes,
        @NotBlank String downloadedAt) {
}
