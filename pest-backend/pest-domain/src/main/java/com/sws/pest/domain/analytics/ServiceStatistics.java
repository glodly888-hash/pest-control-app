package com.sws.pest.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 基础服务与虫情统计（getStatistics）。
 *
 * 按门店业务时区日期区间统计；报告更正按每服务最新有效版本（current_report_id）统计，
 * 不把被替代（SUPERSEDED）版本重复计算。
 */
public final class ServiceStatistics {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final int planned;
    private final int completed;
    private final int cancelled;
    private final int overdue;
    private final int riskOpen;
    private final int riskOverdue;
    private final int riskClosed;
    private final int checkedFacilities;
    private final int eligibleFacilities;
    private final List<SourceBucket> sourceStatistics;

    public ServiceStatistics(LocalDate startDate, LocalDate endDate, int planned, int completed,
                             int cancelled, int overdue, int riskOpen, int riskOverdue, int riskClosed,
                             int checkedFacilities, int eligibleFacilities,
                             List<SourceBucket> sourceStatistics) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.planned = planned;
        this.completed = completed;
        this.cancelled = cancelled;
        this.overdue = overdue;
        this.riskOpen = riskOpen;
        this.riskOverdue = riskOverdue;
        this.riskClosed = riskClosed;
        this.checkedFacilities = checkedFacilities;
        this.eligibleFacilities = eligibleFacilities;
        this.sourceStatistics = sourceStatistics;
    }

    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public int planned() { return planned; }
    public int completed() { return completed; }
    public int cancelled() { return cancelled; }
    public int overdue() { return overdue; }
    public int riskOpen() { return riskOpen; }
    public int riskOverdue() { return riskOverdue; }
    public int riskClosed() { return riskClosed; }
    public int checkedFacilities() { return checkedFacilities; }
    public int eligibleFacilities() { return eligibleFacilities; }
    public List<SourceBucket> sourceStatistics() { return sourceStatistics; }

    /**
     * 来源统计行：按 (source, pestTypeId) 分组。
     * knownCount 为 EXACT 数量之和；unknownRecordCount 为 UNKNOWN 记录数；
     * 设施维度 notChecked/unconfirmable 计数。
     */
    public record SourceBucket(
            String source,
            UUID pestTypeId,
            int knownCount,
            int unknownRecordCount,
            int notCheckedFacilityCount,
            int unconfirmableFacilityCount) {
    }
}
