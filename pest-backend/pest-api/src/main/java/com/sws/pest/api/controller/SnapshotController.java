package com.sws.pest.api.controller;

import com.sws.pest.api.NotFoundException;
import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.SnapshotDetailResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.workorder.ServiceDraftService;
import com.sws.pest.domain.workorder.ServiceRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * 快照读取接口（对应 openapi getSnapshot）。
 *
 * GET /snapshots/{snapshotId} —— 读取报告预览数据。
 */
@RestController
@RequestMapping("/snapshots")
public class SnapshotController {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ServiceDraftService serviceDraftService;

    public SnapshotController(ServiceDraftService serviceDraftService) {
        this.serviceDraftService = serviceDraftService;
    }

    @GetMapping("/{snapshotId}")
    public ApiResponse<SnapshotDetailResponse> getSnapshot(
            HttpServletRequest request,
            @PathVariable UUID snapshotId) {
        requireLogin(request);

        ServiceRepository.SnapshotDetail detail = serviceDraftService.getSnapshot(snapshotId);
        if (detail == null) {
            throw new NotFoundException("快照不存在");
        }

        JsonNode payload = parsePayload(detail.payloadJson());
        return ApiResponse.of(new SnapshotDetailResponse(
                detail.snapshotId().toString(),
                payload,
                detail.contentHash(),
                detail.contentVersion(),
                detail.templateVersion(),
                detail.createdAt().toString(),
                detail.isValid()));
    }

    private static JsonNode parsePayload(String payloadJson) {
        try {
            return JSON_MAPPER.readTree(payloadJson);
        } catch (Exception e) {
            return JSON_MAPPER.createObjectNode();
        }
    }

    private static void requireLogin(HttpServletRequest request) {
        if (JwtAuthFilter.currentUserId(request) == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
    }
}
