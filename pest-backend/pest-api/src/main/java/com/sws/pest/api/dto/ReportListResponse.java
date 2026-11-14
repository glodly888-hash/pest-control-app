package com.sws.pest.api.dto;

import java.util.List;

/**
 * 报告列表响应（对应 openapi listReports 的 data）。
 */
public record ReportListResponse(
        List<ReportResponse> items,
        String nextCursor,
        boolean hasMore) {
}
