package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.FollowupInput;
import com.sws.pest.api.dto.FollowupListResponse;
import com.sws.pest.api.dto.RiskFollowupEntryResponse;
import com.sws.pest.api.dto.RiskFollowupRecordResponse;
import com.sws.pest.api.dto.RiskResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.ledger.FollowupService;
import com.sws.pest.domain.ledger.FollowupRepository;
import com.sws.pest.domain.ledger.RiskFollowup;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 风险跟进接口（对应 openapi createFollowup / listFollowups）。
 */
@RestController
@RequestMapping("/risks/{riskId}/followups")
public class FollowupController {

    private final FollowupService followupService;

    public FollowupController(FollowupService followupService) {
        this.followupService = followupService;
    }

    /** createFollowup：服务之外的独立整改跟进。 */
    @PostMapping
    public ResponseEntity<ApiResponse<RiskFollowupRecordResponse>> createFollowup(
            @PathVariable UUID riskId,
            @RequestBody FollowupInput input,
            HttpServletRequest request) {
        UUID operatorId = requireLogin(request);

        FollowupInput.FollowupEntryInput e = input.entry();
        if (e == null) {
            throw new IllegalArgumentException("entry 不能为空");
        }

        UUID id = e.id() != null ? UUID.fromString(e.id()) : UUID.randomUUID();
        UUID entryRiskId = e.riskId() != null ? UUID.fromString(e.riskId()) : riskId;
        UUID supersedes = e.supersedesEventId() != null
                ? UUID.fromString(e.supersedesEventId()) : null;

        List<String> photos = e.photos() == null ? List.of()
                : e.photos().stream().map(FollowupInput.PhotoInput::attachmentId)
                        .collect(Collectors.toList());

        RiskFollowup entry = new RiskFollowup(
                id, entryRiskId, e.expectedRiskRevision(),
                parseResult(e.result()), e.description(), e.notCheckedReason(),
                parseMaterialSource(e.materialSource()), e.materialProvidedBy(),
                photos, parseInstant(e.occurredAt()), supersedes);

        // serviceId / contextId：独立跟进场景 serviceId 可空；contextId 首期透传不落库。
        FollowupRepository.FollowupView view =
                followupService.createFollowup(entry, operatorId, null, input.contextId());

        return ResponseEntity.status(201)
                .body(ApiResponse.of(toRecordResponse(view)));
    }

    /** listFollowups：风险跟进历史。 */
    @GetMapping
    public ApiResponse<FollowupListResponse> listFollowups(
            @PathVariable UUID riskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        requireLogin(request);

        FollowupService.FollowupPage page =
                followupService.listFollowups(riskId, cursor, limit);

        List<RiskFollowupRecordResponse> items = page.items().stream()
                .map(FollowupController::toRecordResponse)
                .collect(Collectors.toList());
        return ApiResponse.of(new FollowupListResponse(items, page.nextCursor(), page.hasMore()));
    }

    private static RiskFollowupRecordResponse toRecordResponse(FollowupRepository.FollowupView v) {
        RiskFollowup e = v.entry();
        List<RiskResponse.AttachmentRefResponse> photos = e.photos().stream()
                .map(RiskResponse.AttachmentRefResponse::new)
                .collect(Collectors.toList());
        RiskFollowupEntryResponse entryResp = new RiskFollowupEntryResponse(
                e.id().toString(),
                e.riskId().toString(),
                e.expectedRiskRevision(),
                e.result().name(),
                e.description(),
                e.notCheckedReason(),
                e.materialSource() != null ? e.materialSource().name() : null,
                e.materialProvidedBy(),
                photos,
                e.occurredAt().toString(),
                e.supersedesEventId() != null ? e.supersedesEventId().toString() : null);
        return new RiskFollowupRecordResponse(
                entryResp,
                v.riskRevisionAfter(),
                v.operatorId() != null ? v.operatorId().toString() : null,
                v.serviceId() != null ? v.serviceId().toString() : null,
                v.receivedAt().toString());
    }

    private static RiskFollowup.Result parseResult(String result) {
        if (result == null) {
            throw new IllegalArgumentException("result 不能为空");
        }
        try {
            return RiskFollowup.Result.valueOf(result);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("result 枚举值非法");
        }
    }

    private static RiskFollowup.MaterialSource parseMaterialSource(String ms) {
        if (ms == null || ms.isBlank()) {
            return null;
        }
        try {
            return RiskFollowup.MaterialSource.valueOf(ms);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("materialSource 枚举值非法");
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("occurredAt 时间格式错误");
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
