package com.sws.pest.api.dto;

import java.util.List;

/**
 * 风险台账分页结果。
 */
public record RiskListResponse(List<RiskResponse> items, String nextCursor, boolean hasMore) {}
