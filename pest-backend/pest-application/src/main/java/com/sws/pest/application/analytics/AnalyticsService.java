package com.sws.pest.application.analytics;

import com.sws.pest.domain.analytics.AnalyticsRepository;
import com.sws.pest.domain.analytics.Dashboard;
import com.sws.pest.domain.analytics.ServiceStatistics;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 工作台与统计查询用例。
 */
@Service
public class AnalyticsService {

    private final AnalyticsRepository repository;

    public AnalyticsService(AnalyticsRepository repository) {
        this.repository = repository;
    }

    /** 员工工作台汇总。 */
    public Dashboard getDashboard(UUID userId, LocalDate date) {
        return repository.loadDashboard(userId, date);
    }

    /** 管理侧服务与虫情统计。 */
    public ServiceStatistics getStatistics(LocalDate startDate, LocalDate endDate,
                                           UUID storeId, UUID employeeId) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        return repository.loadStatistics(startDate, endDate, storeId, employeeId);
    }
}
