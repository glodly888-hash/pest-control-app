package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.RiskListResponse;
import com.sws.pest.api.dto.RiskResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.ledger.RiskQueryService;
import com.sws.pest.domain.ledger.Risk;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 风险台账查询接口（对应 openapi listRisks / getRisk）。
 */
@RestController
public class RiskController {

    private final RiskQueryService riskQueryService;

    public RiskController(RiskQueryService riskQueryService) {
        this.riskQueryService = riskQueryService;
    }

    /** listRisks：门店风险台账。 */
    @GetMapping("/stores/{storeId}/risks")
    public ApiResponse<RiskListResponse> listRisks(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        requireLogin(request);
        Risk.Status statusFilter = parseStatus(status);

        RiskQueryService.RiskPage page =
                riskQueryService.listRisks(storeId, statusFilter, cursor, limit);

        List<RiskResponse> items = page.items().stream()
                .map(RiskController::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.of(new RiskListResponse(items, page.nextCursor(), page.hasMore()));
    }

    /** getRisk：风险详情。 */
    @GetMapping("/risks/{riskId}")
    public ApiResponse<RiskResponse> getRisk(@PathVariable UUID riskId, HttpServletRequest request) {
        requireLogin(request);
        Risk risk = riskQueryService.getRisk(riskId);
        return ApiResponse.of(toResponse(risk));
    }

    private static Risk.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Risk.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status 枚举值非法");
        }
    }

    private static RiskResponse toResponse(Risk r) {
        List<RiskResponse.AttachmentRefResponse> photos = r.photos().stream()
                .map(RiskResponse.AttachmentRefResponse::new)
                .collect(Collectors.toList());
        return new RiskResponse(
                r.id().toString(),
                r.storeId().toString(),
                r.typeId().toString(),
                r.pestTypeIds(),
                r.areaId() != null ? r.areaId().toString() : null,
                r.floor(),
                r.position(),
                r.description(),
                r.suggestion(),
                r.responsibleParty(),
                r.contactName(),
                r.ownerId().toString(),
                r.dueDate(),
                r.status().name(),
                r.revision(),
                r.discoveredAt().toString(),
                r.closedAt() != null ? r.closedAt().toString() : null,
                photos);
    }

    private static void requireLogin(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
    }
}
