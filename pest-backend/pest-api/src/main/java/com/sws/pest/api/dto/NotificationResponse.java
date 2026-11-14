package com.sws.pest.api.dto;

/**
 * 员工通知响应（对应 openapi Notification）。
 */
public record NotificationResponse(
        String id,
        String type,
        String title,
        String body,
        String resourceType,
        String resourceId,
        boolean isRead,
        String createdAt) {
}
