package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.AckResponse;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.RefusalInput;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.confirmation.RefusalService;
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
 * 客户缺席/拒签登记接口（对应 openapi recordRefusal）。
 * POST /services/{serviceId}/confirmation-exceptions —— 登记缺席或拒签，任务置待确认。
 */
@RestController
@RequestMapping("/services/{serviceId}/confirmation-exceptions")
public class RefusalController {

    private final RefusalService refusalService;

    public RefusalController(RefusalService refusalService) {
        this.refusalService = refusalService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AckResponse>> recordRefusal(
            @PathVariable UUID serviceId,
            @RequestBody RefusalInput input,
            HttpServletRequest request) {
        UUID actorId = requireLogin(request);

        UUID id = input.id() != null ? UUID.fromString(input.id()) : UUID.randomUUID();
        UUID snapshotId = input.snapshotId() != null ? UUID.fromString(input.snapshotId()) : null;

        refusalService.recordRefusal(
                id,
                serviceId,
                snapshotId,
                input.reasonType(),
                input.reason(),
                actorId,
                parseInstant(input.recordedAt()));

        AckResponse ack = new AckResponse(true, Instant.now().toString());
        return ResponseEntity.status(201).body(ApiResponse.of(ack));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("recordedAt 时间格式错误");
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
