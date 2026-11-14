package com.sws.pest.api.dto;

/**
 * 阅读回执响应（对应 openapi PreviewReceipt）。
 */
public record PreviewReceiptResponse(
        String id,
        String snapshotId,
        boolean canAcknowledge,
        long minimumDurationMs,
        String receivedAt) {
}
