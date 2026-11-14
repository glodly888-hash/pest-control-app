package com.sws.pest.api.dto;

import java.util.List;

/**
 * 服务统计响应（对应 openapi ServiceStatistics）。
 */
public record ServiceStatisticsResponse(
        String startDate,
        String endDate,
        int planned,
        int completed,
        int cancelled,
        int overdue,
        int riskOpen,
        int riskOverdue,
        int riskClosed,
        int checkedFacilities,
        int eligibleFacilities,
        List<SourceStatisticsResponse> sourceStatistics) {

    public record SourceStatisticsResponse(
            String source,
            String pestTypeId,
            int knownCount,
            int unknownRecordCount,
            int notCheckedFacilityCount,
            int unconfirmableFacilityCount) {
    }
}
