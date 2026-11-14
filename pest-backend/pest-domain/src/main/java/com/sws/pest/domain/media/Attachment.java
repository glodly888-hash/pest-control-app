package com.sws.pest.domain.media;

import java.time.Instant;
import java.util.UUID;

/**
 * 受保护原始附件（attachment 表实体）。
 *
 * 状态（DB）：PENDING / VERIFYING / READY / FAILED。
 * 对外映射（API）：WAITING（PENDING/VERIFYING）、VERIFIED（READY）、REJECTED（FAILED）。
 */
public record Attachment(
        UUID id,
        UUID storeId,
        UUID ownerId,
        UUID contextId,
        String purpose,
        String scope,
        String status,
        String objectKey,
        String sha256,
        long byteSize,
        String mimeType,
        Instant verifiedAt,
        String failureCode,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isReady() {
        return "READY".equals(status);
    }
}
