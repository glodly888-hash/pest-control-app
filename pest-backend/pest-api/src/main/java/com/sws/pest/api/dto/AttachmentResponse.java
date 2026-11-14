package com.sws.pest.api.dto;

/**
 * 附件响应（对应 openapi Attachment）。
 *
 * status 映射：DB PENDING/VERIFYING → WAITING；READY → VERIFIED；FAILED → REJECTED。
 */
public record AttachmentResponse(
        String id,
        String sha256,
        String mediaType,
        long sizeBytes,
        String status,
        String purpose,
        String receivedAt) {
}
