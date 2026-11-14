package com.sws.pest.api.dto;

import java.util.List;

/**
 * 设施台账列表响应。
 */
public record FacilityListResponse(
        List<FacilityResponse> items,
        String nextCursor,
        boolean hasMore) {
}
