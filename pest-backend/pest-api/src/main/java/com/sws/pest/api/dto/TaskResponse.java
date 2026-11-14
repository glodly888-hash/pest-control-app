package com.sws.pest.api.dto;

import java.util.List;

/**
 * 任务响应（对应 openapi Task）。
 */
public record TaskResponse(
        String id,
        String storeId,
        String storeName,
        String serviceType,
        String status,
        String plannedDate,
        String plannedStartAt,
        String plannedEndAt,
        String assigneeId,
        long assignmentVersion,
        long revision,
        String instructions,
        String source,
        String serviceId,
        boolean isOverdue,
        String latestReportId,
        List<ModuleStateResponse> moduleStates) {

    public record ModuleStateResponse(String module, String state) {
    }
}
