package com.sws.pest.api.controller;

import com.sws.pest.api.NotFoundException;
import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.CreateServiceInput;
import com.sws.pest.api.dto.DraftWriteInput;
import com.sws.pest.api.dto.FreezeInput;
import com.sws.pest.api.dto.RevisionInput;
import com.sws.pest.api.dto.ServiceDraftResponse;
import com.sws.pest.api.dto.SnapshotResponse;
import com.sws.pest.api.dto.ValidationResultResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.workorder.ServiceDraftService;
import com.sws.pest.domain.workorder.FieldService;
import com.sws.pest.domain.workorder.ServiceRepository;
import com.sws.pest.domain.workorder.TaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 现场作业接口。对应 openapi 的 createService / getDraft / saveDraft /
 * validateService / freezeSnapshot。
 *
 * 首期闭环：创建服务草稿 → 读/写草稿 → 校验 → 冻结快照。
 * 签字、发布留待下一轮。
 */
@RestController
@RequestMapping("/services")
public class ServiceController {

    private final ServiceDraftService serviceDraftService;

    public ServiceController(ServiceDraftService serviceDraftService) {
        this.serviceDraftService = serviceDraftService;
    }

    /**
     * POST /services/{taskId} —— 创建服务草稿（幂等）。
     */
    @PostMapping("/{taskId}")
    public ResponseEntity<ApiResponse<ServiceDraftResponse>> createService(
            HttpServletRequest request,
            @PathVariable UUID taskId,
            @RequestBody CreateServiceInput input) {
        UUID actorId = requireLogin(request);

        UUID serviceId = input.id() != null ? UUID.fromString(input.id()) : UUID.randomUUID();
        UUID contextId = input.contextId() != null ? UUID.fromString(input.contextId()) : null;

        FieldService service = serviceDraftService.createService(taskId, actorId, serviceId, contextId);

        ServiceRepository.DraftView view = serviceDraftService.getDraft(service.id());
        return ResponseEntity.status(201)
                .eTag("\"draft-" + view.draftRevision() + "\"")
                .body(ApiResponse.of(toDraftResponse(view)));
    }

    /**
     * GET /services/{serviceId}/draft —— 恢复草稿。
     */
    @GetMapping("/{serviceId}/draft")
    public ResponseEntity<ApiResponse<ServiceDraftResponse>> getDraft(
            HttpServletRequest request,
            @PathVariable UUID serviceId) {
        requireLogin(request);
        ServiceRepository.DraftView view = serviceDraftService.getDraft(serviceId);
        if (view == null) {
            throw new NotFoundException("服务不存在");
        }
        return ResponseEntity.ok()
                .eTag("\"draft-" + view.draftRevision() + "\"")
                .body(ApiResponse.of(toDraftResponse(view)));
    }

    /**
     * PUT /services/{serviceId}/draft —— 保存完整草稿。
     */
    @PutMapping("/{serviceId}/draft")
    public ResponseEntity<ApiResponse<ServiceDraftResponse>> saveDraft(
            HttpServletRequest request,
            @PathVariable UUID serviceId,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody DraftWriteInput input) {
        UUID actorId = requireLogin(request);
        long expectedRevision = parseDraftRevision(ifMatch);

        String contentJson = input.content() != null ? input.content().toString() : "{}";
        long newRevision = serviceDraftService.saveDraft(serviceId, actorId, expectedRevision, contentJson);

        ServiceRepository.DraftView view = serviceDraftService.getDraft(serviceId);
        return ResponseEntity.ok()
                .eTag("\"draft-" + newRevision + "\"")
                .body(ApiResponse.of(toDraftResponse(view)));
    }

    /**
     * POST /services/{serviceId}/validations —— 签字前校验。
     */
    @PostMapping("/{serviceId}/validations")
    public ApiResponse<ValidationResultResponse> validate(
            HttpServletRequest request,
            @PathVariable UUID serviceId) {
        requireLogin(request);
        ServiceDraftService.ValidationResult result = serviceDraftService.validate(serviceId);
        List<String> issues = result.canSign() ? List.of() : List.of("缺少签到信息 checkin");
        return ApiResponse.of(new ValidationResultResponse(
                result.canSign(), result.draftRevision(), issues, result.contentHash()));
    }

    /**
     * POST /services/{serviceId}/snapshots —— 冻结快照。
     */
    @PostMapping("/{serviceId}/snapshots")
    public ResponseEntity<ApiResponse<SnapshotResponse>> freeze(
            HttpServletRequest request,
            @PathVariable UUID serviceId,
            @RequestBody FreezeInput input) {
        requireLogin(request);
        long draftRevision = input.draftRevision() != null ? input.draftRevision() : 0L;
        UUID snapshotId = serviceDraftService.freeze(serviceId, draftRevision, input.contentHash());
        SnapshotResponse snapshot = new SnapshotResponse(
                snapshotId.toString(), 1L, input.contentHash(), Instant.now().toString());
        return ResponseEntity.status(201).body(ApiResponse.of(snapshot));
    }

    /**
     * POST /services/{serviceId}/draft-revisions —— 签后未发布内容修订。
     */
    @PostMapping("/{serviceId}/draft-revisions")
    public ResponseEntity<ApiResponse<ServiceDraftResponse>> reopenDraft(
            HttpServletRequest request,
            @PathVariable UUID serviceId,
            @RequestBody RevisionInput input) {
        UUID actorId = requireLogin(request);

        UUID fromSnapshotId = UUID.fromString(input.fromSnapshotId());
        long newHeadRevision = serviceDraftService.reopenDraft(serviceId, actorId, fromSnapshotId);

        ServiceRepository.DraftView view = serviceDraftService.getDraft(serviceId);
        return ResponseEntity.status(201)
                .eTag("\"draft-" + view.draftRevision() + "\"")
                .body(ApiResponse.of(toDraftResponse(view)));
    }

    private static ServiceDraftResponse toDraftResponse(ServiceRepository.DraftView view) {
        FieldService s = view.service();
        return new ServiceDraftResponse(
                s.id().toString(),
                s.taskId().toString(),
                s.contextId().toString(),
                view.draftRevision(),
                view.fieldCommitRevision(),
                s.phase().name(),
                view.contentJson(),
                view.updatedAt().toString());
    }

    private static UUID requireLogin(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        return userId;
    }

    private static long parseDraftRevision(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new IllegalArgumentException("缺少 If-Match 版本");
        }
        String trimmed = ifMatch.replace("\"", "").trim();
        if (!trimmed.startsWith("draft-")) {
            throw new IllegalArgumentException("If-Match 格式错误");
        }
        try {
            return Long.parseLong(trimmed.substring("draft-".length()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("If-Match 版本号非法");
        }
    }
}
