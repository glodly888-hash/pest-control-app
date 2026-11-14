package com.sws.pest.api.controller;

import com.sws.pest.api.NotFoundException;
import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.FacilityListResponse;
import com.sws.pest.api.dto.FacilityResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.masterdata.FacilityQueryService;
import com.sws.pest.application.masterdata.StoreQueryService;
import com.sws.pest.domain.masterdata.Facility;
import com.sws.pest.domain.masterdata.FacilityRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 设施台账接口。对应 openapi GET /stores/{storeId}/facilities（listFacilities）。
 */
@RestController
@RequestMapping("/stores/{storeId}/facilities")
public class FacilityController {

    private final FacilityQueryService facilityQueryService;
    private final StoreQueryService storeQueryService;

    public FacilityController(FacilityQueryService facilityQueryService,
                              StoreQueryService storeQueryService) {
        this.facilityQueryService = facilityQueryService;
        this.storeQueryService = storeQueryService;
    }

    @GetMapping
    public ApiResponse<FacilityListResponse> listFacilities(
            HttpServletRequest request,
            @PathVariable UUID storeId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
            @RequestParam(name = "typeId", required = false) UUID typeId,
            @RequestParam(name = "status", required = false) String status) {

        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        // 门店存在性校验（不存在返回 404）
        if (storeQueryService.getDetail(storeId) == null) {
            throw new NotFoundException("门店不存在");
        }

        FacilityRepository.Page page = facilityQueryService.listByStore(
                storeId, typeId, status, cursor, limit);

        List<FacilityResponse> items = page.items().stream()
                .map(FacilityController::toResponse)
                .toList();

        FacilityListResponse data = new FacilityListResponse(items, page.nextCursor(), page.hasMore());
        return ApiResponse.of(data);
    }

    private static FacilityResponse toResponse(Facility f) {
        return new FacilityResponse(
                f.id().toString(),
                f.storeId().toString(),
                f.code(),
                f.typeId().toString(),
                f.typeName(),
                f.areaId() != null ? f.areaId().toString() : null,
                f.floor(),
                f.position(),
                f.status(),
                f.revision(),
                f.updatedAt() != null ? f.updatedAt().toString() : null);
    }
}
