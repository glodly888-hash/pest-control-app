package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.DashboardResponse;
import com.sws.pest.api.dto.ServiceStatisticsResponse;
import com.sws.pest.api.dto.TaskResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.analytics.AnalyticsService;
import com.sws.pest.domain.analytics.Dashboard;
import com.sws.pest.domain.analytics.ServiceStatistics;
import com.sws.pest.domain.workorder.Task;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 工作台汇总与统计接口。对应 openapi getDashboard / getStatistics。
 */
@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * GET /dashboard?date=YYYY-MM-DD —— 员工工作台汇总。
     */
    @GetMapping("/dashboard")
    public ApiResponse<DashboardResponse> getDashboard(
            HttpServletRequest request,
            @RequestParam(name = "date")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        UUID userId = requireLogin(request);

        Dashboard d = analyticsService.getDashboard(userId, date);

        List<TaskResponse> todayTasks = d.todayTasks().stream()
                .map(AnalyticsController::toTaskResponse)
                .toList();

        DashboardResponse data = new DashboardResponse(
                d.date().toString(),
                d.planned(), d.inProgress(), d.completed(), d.overdue(),
                d.openRisks(), todayTasks);
        return ApiResponse.of(data);
    }

    /**
     * GET /statistics?startDate=&endDate=&storeId=&employeeId= —— 服务与虫情统计。
     */
    @GetMapping("/statistics")
    public ApiResponse<ServiceStatisticsResponse> getStatistics(
            HttpServletRequest request,
            @RequestParam(name = "startDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "storeId", required = false) UUID storeId,
            @RequestParam(name = "employeeId", required = false) UUID employeeId) {
        requireLogin(request);

        ServiceStatistics s = analyticsService.getStatistics(startDate, endDate, storeId, employeeId);

        List<ServiceStatisticsResponse.SourceStatisticsResponse> buckets = s.sourceStatistics().stream()
                .map(b -> new ServiceStatisticsResponse.SourceStatisticsResponse(
                        b.source(),
                        b.pestTypeId() != null ? b.pestTypeId().toString() : null,
                        b.knownCount(),
                        b.unknownRecordCount(),
                        b.notCheckedFacilityCount(),
                        b.unconfirmableFacilityCount()))
                .toList();

        ServiceStatisticsResponse data = new ServiceStatisticsResponse(
                s.startDate().toString(),
                s.endDate().toString(),
                s.planned(), s.completed(), s.cancelled(), s.overdue(),
                s.riskOpen(), s.riskOverdue(), s.riskClosed(),
                s.checkedFacilities(), s.eligibleFacilities(),
                buckets);
        return ApiResponse.of(data);
    }

    private static TaskResponse toTaskResponse(Task t) {
        List<TaskResponse.ModuleStateResponse> moduleStates = t.moduleStates().stream()
                .map(m -> new TaskResponse.ModuleStateResponse(m.module(), m.state()))
                .toList();
        return new TaskResponse(
                t.id().toString(),
                t.storeId().toString(),
                t.storeName(),
                t.serviceType(),
                t.status().name(),
                t.plannedDate().toString(),
                t.plannedStartAt() != null ? t.plannedStartAt().toString() : null,
                t.plannedEndAt() != null ? t.plannedEndAt().toString() : null,
                t.assigneeId().toString(),
                t.assignmentVersion(),
                t.revision(),
                t.instructions(),
                t.source(),
                t.serviceId() != null ? t.serviceId().toString() : null,
                t.overdue(),
                t.latestReportId() != null ? t.latestReportId().toString() : null,
                moduleStates);
    }

    private static UUID requireLogin(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        return userId;
    }
}
