package com.sws.pest.api.dto;

import java.util.List;

/**
 * 风险跟进列表分页结果。
 */
public record FollowupListResponse(List<RiskFollowupRecordResponse> items,
                                   String nextCursor, boolean hasMore) {}
