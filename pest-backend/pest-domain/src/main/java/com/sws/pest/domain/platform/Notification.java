package com.sws.pest.domain.platform;

import java.time.Instant;
import java.util.UUID;

/**
 * 员工通知（对应 openapi Notification）。
 */
public record Notification(
        UUID id,
        String type,
        String title,
        String body,
        String resourceType,
        UUID resourceId,
        boolean isRead,
        Instant createdAt) {
}
