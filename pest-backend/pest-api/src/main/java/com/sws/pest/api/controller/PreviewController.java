package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.PreviewReceiptResponse;
import com.sws.pest.api.dto.PreviewSessionInput;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.confirmation.PreviewSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * 客户阅读回执接口（对应 openapi recordPreview）。
 * POST /snapshots/{snapshotId}/preview-sessions —— 记录客户阅读过程。
 */
@RestController
@RequestMapping("/snapshots/{snapshotId}/preview-sessions")
public class PreviewController {

    private final PreviewSessionService previewSessionService;

    public PreviewController(PreviewSessionService previewSessionService) {
        this.previewSessionService = previewSessionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PreviewReceiptResponse>> recordPreview(
            @PathVariable UUID snapshotId,
            @RequestBody PreviewSessionInput input,
            HttpServletRequest request) {
        UUID userId = requireLogin(request);

        UUID id = input.id() != null ? UUID.fromString(input.id()) : UUID.randomUUID();

        PreviewSessionService.PreviewReceiptResult result = previewSessionService.recordPreview(
                id,
                snapshotId,
                userId,
                input.contentHash(),
                parseInstant(input.startedAt()),
                parseInstant(input.endedAt()),
                input.visibleDurationMs() != null ? input.visibleDurationMs() : 0L,
                input.isOffline() != null && input.isOffline(),
                request.getHeader("X-Device-Id") != null ? request.getHeader("X-Device-Id") : "unknown");

        PreviewReceiptResponse data = new PreviewReceiptResponse(
                result.id().toString(),
                result.snapshotId().toString(),
                result.canAcknowledge(),
                result.minimumDurationMs(),
                result.receivedAt().toString());

        return ResponseEntity.status(201).body(ApiResponse.of(data));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式错误");
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
