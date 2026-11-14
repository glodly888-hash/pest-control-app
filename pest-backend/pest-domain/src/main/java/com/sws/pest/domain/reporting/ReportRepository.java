package com.sws.pest.domain.reporting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 报告查询持久化端口（service_report）。
 */
public interface ReportRepository {

    /**
     * 分页游标视图：报告列表 + 是否还有更多。
     */
    record ReportPage(List<Report> items, boolean hasMore, String nextCursor) {
    }

    /**
     * 按员工 + 可选过滤条件分页查询历史报告。
     * cursor 为 base64(publishedAt|id)，按 publishedAt 倒序。
     */
    ReportPage list(UUID actorId, String cursor, int limit, UUID storeId, UUID serviceId, String status);

    /**
     * 按 ID 查询报告详情（含 signatures）。
     */
    Optional<Report> findById(UUID reportId);

    /**
     * 读取正式报告 PDF 字节（按 object_key）。
     * 报告非 PUBLISHED/SUPERSEDED 或文件缺失时抛 IllegalStateException。
     */
    byte[] readPdf(UUID reportId);
}
