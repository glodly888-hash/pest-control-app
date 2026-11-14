package com.sws.pest.domain.confirmation;

import java.time.Instant;
import java.util.UUID;

/**
 * 客户缺席或拒签记录（confirmation_exception 表实体）。
 */
public record ConfirmationException(
        UUID id,
        UUID serviceId,
        UUID snapshotId,
        String reasonCode,   // ABSENT / REFUSED
        String reason,
        UUID actorId,
        Instant occurredAt,
        Instant createdAt
) {
}
