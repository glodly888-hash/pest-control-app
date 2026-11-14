package com.sws.pest.api.controller;

import com.sws.pest.api.NotFoundException;
import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.GrantReceiptInput;
import com.sws.pest.api.dto.OfflineContextResponse;
import com.sws.pest.api.dto.OfflineSubmissionInput;
import com.sws.pest.api.dto.ResolutionInput;
import com.sws.pest.api.dto.SubmissionReceiptResponse;
import com.sws.pest.api.dto.SyncExceptionResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.offline.OfflineService;
import com.sws.pest.domain.offline.OfflineRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 离线同步接口（对应 openapi getTaskContext / getStoreContext / confirmDownload /
 * downloadOfflineAsset / submitOfflineWork / getSubmission / listSyncExceptions /
 * getSyncException / resolveSyncException）。
 */
@RestController
public class OfflineController {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final OfflineService offlineService;

    public OfflineController(OfflineService offlineService) {
        this.offlineService = offlineService;
    }

    // ---- 上下文下载 ----

    @GetMapping("/tasks/{taskId}/offline-context")
    public ApiResponse<OfflineContextResponse> getTaskContext(
            HttpServletRequest request,
            @PathVariable UUID taskId,
            @RequestParam(required = false) UUID contextId,
            @RequestParam(required = false) String deviceId) {
        UUID actorId = requireLogin(request);
        UUID effectiveContextId = contextId != null ? contextId : UUID.randomUUID();
        String effectiveDeviceId = deviceId != null ? deviceId : "unknown-device";

        OfflineRepository.OfflineContextView ctx = offlineService.getTaskContext(
                taskId, actorId, effectiveContextId, effectiveDeviceId);
        return ApiResponse.of(toContextResponse(ctx));
    }

    @GetMapping("/stores/{storeId}/offline-context")
    public ApiResponse<OfflineContextResponse> getStoreContext(
            HttpServletRequest request,
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID contextId,
            @RequestParam(required = false) String deviceId) {
        UUID actorId = requireLogin(request);
        UUID effectiveContextId = contextId != null ? contextId : UUID.randomUUID();
        String effectiveDeviceId = deviceId != null ? deviceId : "unknown-device";

        OfflineRepository.OfflineContextView ctx = offlineService.getStoreContext(
                storeId, actorId, effectiveContextId, effectiveDeviceId);
        return ApiResponse.of(toContextResponse(ctx));
    }

    @PostMapping("/offline-contexts/{contextId}/download-receipts")
    public ApiResponse<Object> confirmDownload(
            HttpServletRequest request,
            @PathVariable UUID contextId,
            @RequestBody GrantReceiptInput input) {
        UUID actorId = requireLogin(request);
        Instant downloadedAt = input.downloadedAt() != null
                ? OffsetDateTime.parse(input.downloadedAt()).toInstant() : Instant.now();
        offlineService.confirmDownload(contextId, actorId, downloadedAt);
        return ApiResponse.of(new java.util.HashMap<String, Object>());
    }

    // ---- 离线资源 ----

    @GetMapping("/offline-assets/{assetId}/content")
    public ResponseEntity<byte[]> downloadOfflineAsset(
            HttpServletRequest request,
            @PathVariable UUID assetId) {
        requireLogin(request);
        byte[] content = offlineService.downloadAsset(assetId);
        return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .header("Content-Length", String.valueOf(content.length))
                .body(content);
    }

    // ---- 离线提交 ----

    @PostMapping("/sync/submissions")
    public ResponseEntity<ApiResponse<SubmissionReceiptResponse>> submitOfflineWork(
            HttpServletRequest request,
            @RequestBody OfflineSubmissionInput input) {
        UUID actorId = requireLogin(request);

        UUID submissionId = UUID.fromString(input.id());
        UUID contextId = UUID.fromString(input.contextId());
        UUID serviceId = UUID.fromString(input.serviceId());
        UUID taskId = input.taskId() != null ? UUID.fromString(input.taskId()) : null;

        // 规范化 payload（首期：把整个 input 序列化为 canonical 字符串）
        String canonicalPayload = serializeInput(input);

        OfflineRepository.SubmissionView submission = offlineService.submit(
                submissionId, actorId, contextId, serviceId, taskId,
                input.assignmentVersion(), canonicalPayload);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.of(toSubmissionReceipt(submission)));
    }

    @GetMapping("/sync/submissions/{submissionId}")
    public ApiResponse<SubmissionReceiptResponse> getSubmission(
            HttpServletRequest request,
            @PathVariable UUID submissionId) {
        requireLogin(request);
        OfflineRepository.SubmissionView submission = offlineService.getSubmission(submissionId);
        return ApiResponse.of(toSubmissionReceipt(submission));
    }

    // ---- 同步异常 ----

    @GetMapping("/sync/exceptions")
    public ApiResponse<List<SyncExceptionResponse>> listSyncExceptions(
            HttpServletRequest request,
            @RequestParam(required = false) String status) {
        UUID actorId = requireLogin(request);
        List<SyncExceptionResponse> items = offlineService.listExceptions(status, actorId).stream()
                .map(OfflineController::toExceptionResponse)
                .toList();
        return ApiResponse.of(items);
    }

    @GetMapping("/sync/exceptions/{exceptionId}")
    public ApiResponse<SyncExceptionResponse> getSyncException(
            HttpServletRequest request,
            @PathVariable UUID exceptionId) {
        requireLogin(request);
        return ApiResponse.of(toExceptionResponse(offlineService.getException(exceptionId)));
    }

    @PostMapping("/sync/exceptions/{exceptionId}/resolutions")
    public ApiResponse<SyncExceptionResponse> resolveSyncException(
            HttpServletRequest request,
            @PathVariable UUID exceptionId,
            @RequestBody ResolutionInput input) {
        UUID actorId = requireLogin(request);
        OfflineRepository.SyncExceptionView resolved = offlineService.resolve(
                exceptionId, actorId, input.decision(), input.reason(),
                input.expectedTaskRevision());
        return ApiResponse.of(toExceptionResponse(resolved));
    }

    // ---- 映射 ----

    private static OfflineContextResponse toContextResponse(OfflineRepository.OfflineContextView ctx) {
        JsonNode contextBody = parseJson(ctx.contextBody());
        return new OfflineContextResponse(
                ctx.id().toString(),
                "1.0",
                ctx.contextDigest(),
                contextBody,
                ctx.id().toString(),
                ctx.permissionVersion(),
                ctx.issuedAt().toString(),
                ctx.expiresAt().toString(),
                ctx.downloadBytes());
    }

    private static SubmissionReceiptResponse toSubmissionReceipt(OfflineRepository.SubmissionView s) {
        return new SubmissionReceiptResponse(
                s.id().toString(),
                s.status(),
                s.serviceId() != null ? s.serviceId().toString() : null,
                0L,
                null,
                null,
                List.of(),
                s.receivedAt().toString());
    }

    private static SyncExceptionResponse toExceptionResponse(OfflineRepository.SyncExceptionView e) {
        return new SyncExceptionResponse(
                e.id().toString(),
                e.submissionId().toString(),
                e.kind(),
                e.status(),
                e.message(),
                e.submittedServiceId() != null ? e.submittedServiceId().toString() : null,
                e.createdAt().toString(),
                e.resolution(),
                e.resolvedAt() != null ? e.resolvedAt().toString() : null,
                e.taskId() != null ? e.taskId().toString() : null,
                e.currentTaskRevision());
    }

    private static String serializeInput(OfflineSubmissionInput input) {
        try {
            return JSON_MAPPER.writeValueAsString(input);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static JsonNode parseJson(String json) {
        try {
            return JSON_MAPPER.readTree(json);
        } catch (Exception e) {
            return JSON_MAPPER.createObjectNode();
        }
    }

    private static UUID requireLogin(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        return userId;
    }
}
