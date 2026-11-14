package com.sws.pest.api.dto;

import java.util.List;

/**
 * 任务列表响应（对应 openapi listTasks 的 data）。
 */
public record TaskListResponse(
        List<TaskResponse> items,
        String nextCursor,
        boolean hasMore) {
}
