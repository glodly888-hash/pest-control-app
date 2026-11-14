package com.sws.pest.api.dto;

/**
 * 快照响应（对应 Snapshot schema，首期精简）。
 */
public record SnapshotResponse(
        String id,
        long contentVersion,
        String contentHash,
        String createdAt) {
}
