package com.sws.pest.api.dto;

/**
 * 客户阅读回执请求（对应 openapi PreviewSessionInput）。
 */
public record PreviewSessionInput(
        String id,
        String snapshotId,
        String contentHash,
        String startedAt,
        String endedAt,
        Long visibleDurationMs,
        Boolean isOffline) {
}
