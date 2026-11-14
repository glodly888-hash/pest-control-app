package com.sws.pest.domain.analytics;

import com.sws.pest.domain.workorder.Task;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 工作台与统计读取端口。
 */
public interface AnalyticsRepository {

    /**
     * 员工工作台：本人任务的各状态计数 + 当日任务列表。
     *
     * @param userId 当前员工（任务主执行人）
     * @param date   工作台日期（门店业务时区）
     */
    Dashboard loadDashboard(UUID userId, LocalDate date);

    /**
     * 本人作为跟进人（followup_user_id）的未关闭风险数。
     */
    int countOpenRisks(UUID userId);

    /**
     * 服务统计（管理侧）：按日期区间 + 可选 store/employee 过滤。
     */
    ServiceStatistics loadStatistics(LocalDate startDate, LocalDate endDate,
                                     UUID storeId, UUID employeeId);
}
