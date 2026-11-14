package com.sws.pest.api.dto;

/**
 * 冻结快照请求（对应 FreezeInput）。
 */
public record FreezeInput(
        String snapshotId,
        Long draftRevision,
        String contentHash) {
}
