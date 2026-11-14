package com.sws.pest.api.dto;

import java.util.List;

/**
 * 离线提交回执（对应 openapi SubmissionReceipt）。
 */
public record SubmissionReceiptResponse(
        String id,
        String state,
        String serviceId,
        long draftRevision,
        String exceptionId,
        String publicationJobId,
        List<String> issues,
        String receivedAt) {
}
