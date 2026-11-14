package com.sws.pest.api.controller;

import com.sws.pest.api.NotFoundException;
import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.StoreListResponse;
import com.sws.pest.api.dto.StoreResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.masterdata.StoreQueryService;
import com.sws.pest.domain.masterdata.Store;
import com.sws.pest.domain.masterdata.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 门店接口。对应 openapi GET /stores（listStores）、GET /stores/{storeId}（getStore）。
 */
@RestController
@RequestMapping("/stores")
public class StoreController {

    private final StoreQueryService storeQueryService;

    public StoreController(StoreQueryService storeQueryService) {
        this.storeQueryService = storeQueryService;
    }

    @GetMapping
    public ApiResponse<StoreListResponse> listStores(
            HttpServletRequest request,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
            @RequestParam(name = "keyword", required = false) String keyword) {

        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }

        StoreRepository.Page page = storeQueryService.listByScope(userId, keyword, cursor, limit);

        List<StoreResponse> items = page.items().stream()
                .map(StoreController::toResponse)
                .toList();

        StoreListResponse data = new StoreListResponse(items, page.nextCursor(), page.hasMore());
        return ApiResponse.of(data);
    }

    @GetMapping("/{storeId}")
    public ApiResponse<StoreResponse> getStore(
            HttpServletRequest request,
            @PathVariable UUID storeId) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        Store store = storeQueryService.getDetail(storeId);
        if (store == null) {
            throw new NotFoundException("门店不存在");
        }
        return ApiResponse.of(toResponse(store));
    }

    private static StoreResponse toResponse(Store s) {
        StoreResponse.LocationResponse location =
                (s.latitude() != null && s.longitude() != null)
                        ? new StoreResponse.LocationResponse(
                                s.latitude().doubleValue(), s.longitude().doubleValue())
                        : null;
        List<StoreResponse.AreaResponse> areas = s.areas() == null
                ? List.of()
                : s.areas().stream()
                        .map(a -> new StoreResponse.AreaResponse(a.id().toString(), a.name(), a.floor()))
                        .toList();
        return new StoreResponse(
                s.id().toString(),
                s.customerId() != null ? s.customerId().toString() : null,
                s.brandId() != null ? s.brandId().toString() : null,
                s.code(), s.name(), s.address(), s.timezone(), s.brandName(),
                s.regionId() != null ? s.regionId().toString() : null,
                location,
                s.revision(),
                s.contactName(),
                s.contactPhone(),
                areas);
    }
}
