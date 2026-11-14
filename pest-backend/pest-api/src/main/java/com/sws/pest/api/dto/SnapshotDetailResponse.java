package com.sws.pest.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * 快照详情响应（对应 openapi Snapshot 完整契约，getSnapshot 用）。
 */
public record SnapshotDetailResponse(
        String id,
        JsonNode payload,
        String contentHash,
        long contentVersion,
        String templateVersion,
        String createdAt,
        boolean isValid) {
}
