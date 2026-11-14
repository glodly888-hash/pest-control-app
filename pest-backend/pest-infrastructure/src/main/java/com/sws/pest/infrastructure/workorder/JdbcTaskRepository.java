package com.sws.pest.infrastructure.workorder;

import com.sws.pest.domain.workorder.ServiceStatus;
import com.sws.pest.domain.workorder.Task;
import com.sws.pest.domain.workorder.TaskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * service_task 的 JDBC 实现。
 * 员工任务按 assignee_id 过滤，join store 取门店名，join service 取服务与报告指针。
 * 排序按 (planned_date, id) 稳定，游标为 base64(plannedDate|id)。
 */
@Repository
public class JdbcTaskRepository implements TaskRepository {

    private final JdbcTemplate jdbc;

    public JdbcTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page listByAssignee(UUID userId, LocalDate startDate, LocalDate endDate,
                               String status, String keyword, UUID storeId,
                               String cursor, int limit) {
        StringBuilder sql = new StringBuilder(TASK_SELECT);
        sql.append(" WHERE t.assignee_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(uuidBytes(userId));

        if (startDate != null) {
            sql.append(" AND t.planned_date >= ?");
            args.add(java.sql.Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append(" AND t.planned_date <= ?");
            args.add(java.sql.Date.valueOf(endDate));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND t.status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (s.name LIKE ? OR s.code LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (storeId != null) {
            sql.append(" AND t.store_id = ?");
            args.add(uuidBytes(storeId));
        }

        // 游标分页：按 (planned_date, id) 排序，cursor 为 base64(plannedDate|id)。
        if (cursor != null && !cursor.isBlank()) {
            sql.append(" AND (t.planned_date, t.id) > (?, ?)");
            String[] parts = cursor.split("\\|");
            args.add(java.sql.Date.valueOf(parts[0]));
            args.add(uuidBytes(UUID.fromString(parts[1])));
        }

        sql.append(" ORDER BY t.planned_date, t.id LIMIT ?");
        args.add(limit + 1);

        List<Task> tasks = jdbc.query(sql.toString(), rs -> {
            List<Task> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapTask(rs));
            }
            return list;
        }, args.toArray());

        boolean hasMore = tasks.size() > limit;
        if (hasMore) {
            tasks = tasks.subList(0, limit);
        }
        String nextCursor = null;
        if (hasMore && !tasks.isEmpty()) {
            Task last = tasks.get(tasks.size() - 1);
            nextCursor = last.plannedDate() + "|" + last.id();
        }
        return new Page(tasks, nextCursor, hasMore);
    }

    private static final String TASK_SELECT = """
            SELECT t.id, t.store_id, s.name AS store_name, t.service_type,
                   t.status, t.planned_date, t.planned_start_at, t.planned_end_at,
                   t.assignee_id, t.assignment_version, t.revision,
                   t.instructions, t.creation_source,
                   svc.id AS service_id, svc.current_report_id AS latest_report_id
            FROM service_task t
            JOIN store s ON s.id = t.store_id
            LEFT JOIN service svc ON svc.task_id = t.id
            """;

    @Override
    public Task findById(UUID taskId) {
        String sql = TASK_SELECT + " WHERE t.id = ?";
        List<Task> list = jdbc.query(sql, (rs, i) -> mapTask(rs), uuidBytes(taskId));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Task applyChange(ChangeCommand cmd) {
        // 1. 乐观锁校验：读取当前 revision，不匹配则 412
        Long currentRevision = jdbc.queryForObject(
                "SELECT revision FROM service_task WHERE id = ?", Long.class, uuidBytes(cmd.taskId()));
        if (currentRevision == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (currentRevision.longValue() != cmd.expectedRevision()) {
            throw new OptimisticLockException("任务已被他人修改，请刷新后重试");
        }

        // 2. 构建变更 SQL
        StringBuilder set = new StringBuilder("SET ");
        List<Object> args = new ArrayList<>();
        switch (cmd.action()) {
            case "ASSIGN", "REASSIGN" -> {
                if (cmd.assigneeId() == null) {
                    throw new IllegalArgumentException("派工/改派需 assigneeId");
                }
                set.append("assignee_id = ?, assignment_version = assignment_version + 1");
                args.add(uuidBytes(cmd.assigneeId()));
            }
            case "RESCHEDULE" -> {
                if (cmd.plannedDate() == null) {
                    throw new IllegalArgumentException("改期需 plannedDate");
                }
                set.append("planned_date = ?");
                args.add(java.sql.Date.valueOf(cmd.plannedDate()));
            }
            case "CANCEL" -> {
                set.append("status = 'CANCELLED', assignment_version = assignment_version + 1");
            }
            default -> throw new IllegalArgumentException("未知 action: " + cmd.action());
        }
        set.append(", revision = revision + 1");
        args.add(uuidBytes(cmd.taskId()));

        int updated = jdbc.update(
                "UPDATE service_task " + set + " WHERE id = ?", args.toArray());
        if (updated == 0) {
            throw new OptimisticLockException("任务已被他人修改，请刷新后重试");
        }

        // 3. 写变更日志（before_json / after_json 首期简化：记录变更关键信息）
        //    先取变更前快照（用当前内存已知值，首期用占位 JSON 记录 action/reason）
        String beforeJson = "{\"revision\":" + currentRevision + "}";
        String afterJson = "{\"revision\":" + (currentRevision + 1)
                + ",\"action\":\"" + cmd.action() + "\",\"reason\":\"" + escapeJson(cmd.reason()) + "\"}";
        byte[] changeId = uuidBytes(UUID.randomUUID());
        jdbc.update(
                "INSERT INTO task_change (id, task_id, actor_id, reason, before_json, after_json) VALUES (?, ?, ?, ?, ?, ?)",
                changeId, uuidBytes(cmd.taskId()), uuidBytes(cmd.actorId()), cmd.reason(),
                beforeJson, afterJson);

        return findById(cmd.taskId());
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    @Override
    public Task createEmergency(EmergencyCommand cmd) {
        // 幂等：同 id 已存在则返回原任务
        Task existing = findById(cmd.taskId());
        if (existing != null) {
            return existing;
        }

        // 离线应急需校验 offlineContextId 存在且授权该门店（首期简化：仅校验存在性）
        if (cmd.offlineContextId() != null) {
            Integer ctx = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM offline_context WHERE id = ? AND store_id = ?",
                    Integer.class, uuidBytes(cmd.offlineContextId()), uuidBytes(cmd.storeId()));
            if (ctx == null || ctx == 0) {
                throw new IllegalArgumentException("离线上下文无效或门店不匹配");
            }
        }

        jdbc.update("""
                INSERT INTO service_task (id, store_id, plan_id, occurrence_key, assignee_id,
                                          service_type, planned_date, status, assignment_version,
                                          revision, creation_source, instructions)
                VALUES (?, ?, NULL, NULL, ?, 'EMERGENCY', ?, 'PENDING', 1, 1,
                        'EMPLOYEE_EMERGENCY', ?)
                """,
                uuidBytes(cmd.taskId()),
                uuidBytes(cmd.storeId()),
                uuidBytes(cmd.actorId()),
                java.sql.Date.valueOf(cmd.plannedDate()),
                cmd.reason());

        return findById(cmd.taskId());
    }

    private static Task mapTask(ResultSet rs) throws SQLException {
        UUID id = bytesToUuid(rs.getBytes("id"));
        java.sql.Date plannedDate = rs.getDate("planned_date");
        Instant plannedStart = rs.getTimestamp("planned_start_at") != null
                ? rs.getTimestamp("planned_start_at").toInstant() : null;
        Instant plannedEnd = rs.getTimestamp("planned_end_at") != null
                ? rs.getTimestamp("planned_end_at").toInstant() : null;
        String status = rs.getString("status");

        // isOverdue：有计划结束时间且已过、且任务未完成/取消。
        boolean overdue = plannedEnd != null
                && plannedEnd.isBefore(Instant.now())
                && !"COMPLETED".equals(status)
                && !"CANCELLED".equals(status);

        // moduleStates 首期返回空，后续由 service_draft.content_json 推导模块完成度。
        return new Task(
                id,
                bytesToUuid(rs.getBytes("store_id")),
                rs.getString("store_name"),
                rs.getString("service_type"),
                ServiceStatus.valueOf(status),
                plannedDate.toLocalDate(),
                plannedStart,
                plannedEnd,
                bytesToUuid(rs.getBytes("assignee_id")),
                rs.getLong("assignment_version"),
                rs.getLong("revision"),
                rs.getString("instructions"),
                rs.getString("creation_source"),
                rs.getBytes("service_id") != null ? bytesToUuid(rs.getBytes("service_id")) : null,
                overdue,
                rs.getBytes("latest_report_id") != null ? bytesToUuid(rs.getBytes("latest_report_id")) : null,
                List.of());
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
