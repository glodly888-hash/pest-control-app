package com.sws.pest.api.dto;

import java.util.List;

/**
 * 门店列表响应（对应 openapi listStores 的 data）。
 */
public record StoreListResponse(
        List<StoreResponse> items,
        String nextCursor,
        boolean hasMore) {
}
