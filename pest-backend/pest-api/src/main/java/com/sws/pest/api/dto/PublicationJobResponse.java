package com.sws.pest.api.dto;

/**
 * 报告发布任务响应（对应 openapi PublicationJob）。
 */
public record PublicationJobResponse(
        String id,
        String serviceId,
        String snapshotId,
        String status,
        String reportId,
        String errorCode,
        String errorMessage,
        boolean canRetry,
        String createdAt,
        String updatedAt) {
}
