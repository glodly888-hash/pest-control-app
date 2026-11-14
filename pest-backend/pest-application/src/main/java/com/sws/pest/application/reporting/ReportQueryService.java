package com.sws.pest.application.reporting;

import com.sws.pest.domain.reporting.Report;
import com.sws.pest.domain.reporting.ReportRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 报告查询用例服务（listReports / getReport）。
 */
@Service
public class ReportQueryService {

    private final ReportRepository reportRepository;

    public ReportQueryService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public ReportRepository.ReportPage list(UUID actorId, String cursor, int limit,
                                            UUID storeId, UUID serviceId, String status) {
        return reportRepository.list(actorId, cursor, limit, storeId, serviceId, status);
    }

    public Report getById(UUID reportId) {
        return reportRepository.findById(reportId).orElse(null);
    }

    /**
     * 读取正式报告 PDF 字节（downloadReport）。
     * 报告非 PUBLISHED/SUPERSEDED 或文件缺失时抛 IllegalStateException。
     */
    public byte[] downloadPdf(UUID reportId) {
        return reportRepository.readPdf(reportId);
    }
}
