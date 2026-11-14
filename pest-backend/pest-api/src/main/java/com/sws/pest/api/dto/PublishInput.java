package com.sws.pest.api.dto;

/**
 * 报告发布请求（对应 openapi PublishInput）。
 */
public record PublishInput(
        String id,
        String snapshotId,
        String staffSignatureId,
        String customerSignatureId) {
}
