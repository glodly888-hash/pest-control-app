package com.sws.pest.api.dto;

/**
 * 任务变更请求（对应 openapi TaskChangeInput）。
 */
public record TaskChangeInput(
        String action,
        String reason,
        String assigneeId,
        String plannedDate) {
}
