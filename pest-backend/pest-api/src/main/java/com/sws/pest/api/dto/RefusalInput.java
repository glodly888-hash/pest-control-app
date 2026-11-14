package com.sws.pest.api.dto;

/**
 * 客户缺席/拒签登记请求（对应 openapi RefusalInput）。
 */
public record RefusalInput(
        String id,
        String snapshotId,
        String reasonType,
        String reason,
        String recordedAt) {
}
