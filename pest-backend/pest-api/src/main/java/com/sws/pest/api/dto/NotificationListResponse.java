package com.sws.pest.api.dto;

import java.util.List;

/**
 * 员工通知列表响应（对应 openapi listNotifications 的 data）。
 */
public record NotificationListResponse(
        List<NotificationResponse> items,
        String nextCursor,
        boolean hasMore) {
}
