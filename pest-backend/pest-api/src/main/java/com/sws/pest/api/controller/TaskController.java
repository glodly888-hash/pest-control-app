package com.sws.pest.api.controller;

import com.sws.pest.api.NotFoundException;
import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.EmergencyTaskInput;
import com.sws.pest.api.dto.TaskChangeInput;
import com.sws.pest.api.dto.TaskListResponse;
import com.sws.pest.api.dto.TaskResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.workorder.TaskChangeService;
import com.sws.pest.application.workorder.TaskQueryService;
import com.sws.pest.domain.workorder.Task;
import com.sws.pest.domain.workorder.TaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 任务接口。对应 openapi GET /tasks、GET /tasks/{taskId}、POST /tasks/{taskId}/changes。
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskQueryService taskQueryService;
    private final TaskChangeService taskChangeService;

    public TaskController(TaskQueryService taskQueryService, TaskChangeService taskChangeService) {
        this.taskQueryService = taskQueryService;
        this.taskChangeService = taskChangeService;
    }

    @GetMapping
    public ApiResponse<TaskListResponse> listTasks(
            HttpServletRequest request,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "storeId", required = false) UUID storeId) {

        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }

        TaskRepository.Page page = taskQueryService.listByAssignee(
                userId, startDate, endDate, status, keyword, storeId, cursor, limit);

        List<TaskResponse> items = page.items().stream()
                .map(TaskController::toResponse)
                .toList();

        TaskListResponse data = new TaskListResponse(items, page.nextCursor(), page.hasMore());
        return ApiResponse.of(data);
    }

    private static TaskResponse toResponse(Task t) {
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

    /**
     * GET /tasks/{taskId} —— 任务详情，返回 ETag（"task-{revision}"）。
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskResponse>> getTask(
            HttpServletRequest request,
            @PathVariable UUID taskId) {
        requireLogin(request);
        Task task = taskQueryService.getById(taskId);
        if (task == null) {
            throw new NotFoundException("任务不存在");
        }
        return ResponseEntity.ok()
                .eTag("\"task-" + task.revision() + "\"")
                .body(ApiResponse.of(toResponse(task)));
    }

    /**
     * POST /tasks/{taskId}/changes —— 派工/改派/改期/取消。
     * If-Match 携带任务 ETag（"task-{revision}"），不匹配返回 412。
     */
    @PostMapping("/{taskId}/changes")
    public ResponseEntity<ApiResponse<TaskResponse>> changeTask(
            HttpServletRequest request,
            @PathVariable UUID taskId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody TaskChangeInput input) {
        UUID actorId = requireLogin(request);

        long expectedRevision = parseRevision(ifMatch);

        UUID assigneeId = null;
        if (input.assigneeId() != null && !input.assigneeId().isBlank()) {
            assigneeId = UUID.fromString(input.assigneeId());
        }
        LocalDate plannedDate = null;
        if (input.plannedDate() != null && !input.plannedDate().isBlank()) {
            plannedDate = LocalDate.parse(input.plannedDate());
        }

        Task updated = taskChangeService.change(
                taskId, actorId, input.action(), input.reason(),
                assigneeId, plannedDate, expectedRevision);

        return ResponseEntity.ok()
                .eTag("\"task-" + updated.revision() + "\"")
                .body(ApiResponse.of(toResponse(updated)));
    }

    private static UUID requireLogin(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        return userId;
    }

    /**
     * POST /tasks —— 员工创建应急任务（201）。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> createEmergencyTask(
            HttpServletRequest request,
            @RequestBody EmergencyTaskInput input) {
        UUID actorId = requireLogin(request);

        UUID taskId = UUID.fromString(input.id());
        UUID storeId = UUID.fromString(input.storeId());
        LocalDate plannedDate = LocalDate.parse(input.plannedDate());
        UUID offlineContextId = input.offlineContextId() != null
                ? UUID.fromString(input.offlineContextId()) : null;

        Task task = taskChangeService.createEmergency(
                taskId, storeId, actorId, plannedDate, input.reason(),
                java.time.Instant.now(), offlineContextId);

        return ResponseEntity.status(201)
                .eTag("\"task-" + task.revision() + "\"")
                .body(ApiResponse.of(toResponse(task)));
    }

    private static long parseRevision(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new IllegalArgumentException("缺少 If-Match 版本");
        }
        // 形如 "task-3" 或带引号 "\"task-3\""
        String trimmed = ifMatch.replace("\"", "").trim();
        if (!trimmed.startsWith("task-")) {
            throw new IllegalArgumentException("If-Match 格式错误");
        }
        try {
            return Long.parseLong(trimmed.substring("task-".length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match 版本号非法");
        }
    }
}
