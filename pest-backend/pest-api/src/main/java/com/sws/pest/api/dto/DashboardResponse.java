package com.sws.pest.api.dto;

import java.util.List;

/**
 * 工作台汇总响应（对应 openapi Dashboard）。
 */
public record DashboardResponse(
        String date,
        int planned,
        int inProgress,
        int completed,
        int overdue,
        int openRisks,
        List<TaskResponse> todayTasks) {
}
