package com.sws.pest.api.dto;

/**
 * 同步异常响应（对应 openapi SyncException）。
 */
public record SyncExceptionResponse(
        String id,
        String submissionId,
        String type,
        String status,
        String message,
        String submittedServiceId,
        String createdAt,
        String resolution,
        String resolvedAt,
        String taskId,
        Long currentTaskRevision) {
}
