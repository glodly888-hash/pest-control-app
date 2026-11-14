package com.sws.pest.infrastructure.analytics;

import com.sws.pest.domain.analytics.AnalyticsRepository;
import com.sws.pest.domain.analytics.Dashboard;
import com.sws.pest.domain.analytics.ServiceStatistics;
import com.sws.pest.domain.workorder.TaskRepository;
import com.sws.pest.infrastructure.workorder.JdbcTaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 工作台与统计的 JDBC 实现。
 *
 * - Dashboard 基于 service_task（按 assignee 过滤）+ risk（按 followup_user_id）；
 * - Statistics 基于 service_report（PUBLISHED，current_report_id 去重）
 *   + snapshot_finding + snapshot_inspection。
 */
@Repository
public class JdbcAnalyticsRepository implements AnalyticsRepository {

    private final JdbcTemplate jdbc;
    private final TaskRepository taskRepository;

    public JdbcAnalyticsRepository(JdbcTemplate jdbc, JdbcTaskRepository taskRepository) {
        this.jdbc = jdbc;
        this.taskRepository = taskRepository;
    }

    @Override
    public Dashboard loadDashboard(UUID userId, LocalDate date) {
        // 本人任务全周期状态计数
        List<Object[]> rows = jdbc.query(
                "SELECT status, COUNT(*) FROM service_task WHERE assignee_id = ? GROUP BY status",
                (rs, i) -> new Object[]{rs.getString(1), rs.getLong(2)},
                uuidBytes(userId));

        int planned = 0, inProgress = 0, completed = 0;
        for (Object[] r : rows) {
            String st = (String) r[0];
            long c = (Long) r[1];
            switch (st) {
                case "PENDING" -> planned += (int) c;
                case "IN_PROGRESS" -> inProgress += (int) c;
                case "COMPLETED" -> completed += (int) c;
                default -> { /* CANCELLED / AWAITING_CONFIRMATION 不计入四项 */ }
            }
        }

        // overdue：有计划结束时间且已过、且未完成/取消
        Integer overdue = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service_task "
                        + "WHERE assignee_id = ? AND planned_end_at IS NOT NULL "
                        + "AND planned_end_at < NOW(6) AND status NOT IN ('COMPLETED','CANCELLED')",
                Integer.class, uuidBytes(userId));

        // openRisks：本人跟进、未关闭
        Integer openRisks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk WHERE followup_user_id = ? AND status <> 'CLOSED'",
                Integer.class, uuidBytes(userId));

        // todayTasks：当天计划任务（复用 TaskRepository 稳定排序）
        TaskRepository.Page page = taskRepository.listByAssignee(
                userId, date, date, null, null, null, null, 5000);

        return new Dashboard(date, planned, inProgress, completed,
                overdue == null ? 0 : overdue,
                openRisks == null ? 0 : openRisks,
                page.items());
    }

    @Override
    public int countOpenRisks(UUID userId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM risk WHERE followup_user_id = ? AND status <> 'CLOSED'",
                Integer.class, uuidBytes(userId));
        return n == null ? 0 : n;
    }

    @Override
    public ServiceStatistics loadStatistics(LocalDate startDate, LocalDate endDate,
                                            UUID storeId, UUID employeeId) {
        // 服务三态 + 逾期：按门店业务日区间（planned_date）
        int planned = countTasks(null, storeId, employeeId, startDate, endDate);
        int completed = countTasks("COMPLETED", storeId, employeeId, startDate, endDate);
        int cancelled = countTasks("CANCELLED", storeId, employeeId, startDate, endDate);
        int overdue = countOverdueTasks(storeId, employeeId, startDate, endDate);

        // 风险三态：按门店 + 发现时间落区间
        int riskOpen = 0, riskClosed = 0;
        List<Object> riskArgs = new ArrayList<>();
        StringBuilder riskSql = new StringBuilder(
                "SELECT r.status, COUNT(*) FROM risk r WHERE r.found_at >= ? AND r.found_at < ?");
        riskArgs.add(java.sql.Date.valueOf(startDate));
        riskArgs.add(java.sql.Date.valueOf(endDate));
        if (storeId != null) {
            riskSql.append(" AND r.store_id = ?");
            riskArgs.add(uuidBytes(storeId));
        }
        riskSql.append(" GROUP BY r.status");
        List<Object[]> riskRows = jdbc.query(riskSql.toString(),
                (rs, i) -> new Object[]{rs.getString(1), rs.getLong(2)},
                riskArgs.toArray());
        for (Object[] r : riskRows) {
            String st = (String) r[0];
            long c = (Long) r[1];
            if ("CLOSED".equals(st)) {
                riskClosed += (int) c;
            } else {
                riskOpen += (int) c;
            }
        }

        // 风险逾期：未关闭且 due_date 早于今天
        int riskOverdue = 0;
        List<Object> overdueRiskArgs = new ArrayList<>();
        StringBuilder overdueRiskSql = new StringBuilder(
                "SELECT COUNT(*) FROM risk r WHERE r.status <> 'CLOSED' AND r.due_date IS NOT NULL AND r.due_date < CURDATE()");
        if (storeId != null) {
            overdueRiskSql.append(" AND r.store_id = ?");
            overdueRiskArgs.add(uuidBytes(storeId));
        }
        Integer riskOverdueCount = jdbc.queryForObject(overdueRiskSql.toString(),
                Integer.class, overdueRiskArgs.toArray());
        riskOverdue = riskOverdueCount == null ? 0 : riskOverdueCount;

        // 设施检查覆盖 + 来源统计：基于当前正式报告（current_report_id）
        List<Object> factArgs = new ArrayList<>();
        StringBuilder factWhere = new StringBuilder(
                " WHERE r.service_started_at >= ? AND r.service_started_at < ?");
        factArgs.add(java.sql.Date.valueOf(startDate));
        factArgs.add(java.sql.Date.valueOf(endDate));
        if (storeId != null) {
            factWhere.append(" AND s.store_id = ?");
            factArgs.add(uuidBytes(storeId));
        }

        Integer checkedFacilities = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT i.facility_id) FROM service s "
                        + "JOIN service_report r ON r.id = s.current_report_id AND r.status='PUBLISHED' "
                        + "JOIN snapshot_inspection i ON i.snapshot_id = r.snapshot_id AND i.store_id = s.store_id"
                        + factWhere + " AND i.check_result='CHECKED'",
                Integer.class, factArgs.toArray());
        Integer eligibleFacilities = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT i.facility_id) FROM service s "
                        + "JOIN service_report r ON r.id = s.current_report_id AND r.status='PUBLISHED' "
                        + "JOIN snapshot_inspection i ON i.snapshot_id = r.snapshot_id AND i.store_id = s.store_id"
                        + factWhere,
                Integer.class, factArgs.toArray());

        List<ServiceStatistics.SourceBucket> buckets = loadSourceBuckets(storeId, startDate, endDate);

        return new ServiceStatistics(startDate, endDate,
                planned, completed, cancelled, overdue,
                riskOpen, riskOverdue, riskClosed,
                checkedFacilities == null ? 0 : checkedFacilities,
                eligibleFacilities == null ? 0 : eligibleFacilities,
                buckets);
    }

    /** 统计任务数（可指定 status，null 表示全部）。 */
    private int countTasks(String status, UUID storeId, UUID employeeId,
                           LocalDate start, LocalDate end) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM service_task t WHERE t.planned_date >= ? AND t.planned_date <= ?");
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Date.valueOf(start));
        args.add(java.sql.Date.valueOf(end));
        if (status != null) {
            sql.append(" AND t.status = ?");
            args.add(status);
        }
        if (storeId != null) {
            sql.append(" AND t.store_id = ?");
            args.add(uuidBytes(storeId));
        }
        if (employeeId != null) {
            sql.append(" AND t.assignee_id = ?");
            args.add(uuidBytes(employeeId));
        }
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    private int countOverdueTasks(UUID storeId, UUID employeeId, LocalDate start, LocalDate end) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM service_task t WHERE t.planned_end_at IS NOT NULL "
                        + "AND t.planned_end_at < NOW(6) AND t.status NOT IN ('COMPLETED','CANCELLED') "
                        + "AND t.planned_date >= ? AND t.planned_date <= ?");
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Date.valueOf(start));
        args.add(java.sql.Date.valueOf(end));
        if (storeId != null) {
            sql.append(" AND t.store_id = ?");
            args.add(uuidBytes(storeId));
        }
        if (employeeId != null) {
            sql.append(" AND t.assignee_id = ?");
            args.add(uuidBytes(employeeId));
        }
        Integer n = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n == null ? 0 : n;
    }

    /** 虫情 source 分组：EXACT 数量之和 + UNKNOWN 记录数。 */
    private List<ServiceStatistics.SourceBucket> loadSourceBuckets(UUID storeId,
                                                                   LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT f.source, f.pest_type_id, "
                        + "SUM(CASE WHEN f.quantity_state='EXACT' THEN f.quantity ELSE 0 END) AS known, "
                        + "SUM(CASE WHEN f.quantity_state='UNKNOWN' THEN 1 ELSE 0 END) AS unknown "
                        + "FROM service s "
                        + "JOIN service_report r ON r.id = s.current_report_id AND r.status='PUBLISHED' "
                        + "JOIN snapshot_finding f ON f.snapshot_id = r.snapshot_id AND f.store_id = s.store_id "
                        + "WHERE r.service_started_at >= ? AND r.service_started_at < ?");
        List<Object> args = new ArrayList<>();
        args.add(java.sql.Date.valueOf(startDate));
        args.add(java.sql.Date.valueOf(endDate));
        if (storeId != null) {
            sql.append(" AND s.store_id = ?");
            args.add(uuidBytes(storeId));
        }
        sql.append(" GROUP BY f.source, f.pest_type_id");

        List<Object[]> rows = jdbc.query(sql.toString(),
                (rs, i) -> new Object[]{rs.getString(1), rs.getBytes(2), rs.getLong(3), rs.getLong(4)},
                args.toArray());

        List<ServiceStatistics.SourceBucket> buckets = new ArrayList<>();
        for (Object[] r : rows) {
            String source = (String) r[0];
            UUID pestTypeId = bytesToUuid((byte[]) r[1]);
            int known = ((Number) r[2]).intValue();
            int unknown = ((Number) r[3]).intValue();
            buckets.add(new ServiceStatistics.SourceBucket(
                    source, pestTypeId, known, unknown, 0, 0));
        }
        return buckets;
    }

    static byte[] uuidBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
