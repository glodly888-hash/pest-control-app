package com.sws.pest.domain.workorder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 任务仓储端口。员工任务按 assignee 过滤，日期按门店业务时区比较。
 */
public interface TaskRepository {

    /**
     * 列出本人任务（assigneeId = userId）。
     *
     * @param userId    主执行人
     * @param startDate 计划日期下界（门店本地日期，含）
     * @param endDate   计划日期上界（门店本地日期，含）
     * @param status    业务状态过滤，null 表示全部
     * @param keyword   门店名/编号模糊匹配
     * @param storeId   指定门店
     * @param cursor    分页游标
     * @param limit     每页条数
     */
    Page listByAssignee(UUID userId, LocalDate startDate, LocalDate endDate,
                        String status, String keyword, UUID storeId,
                        String cursor, int limit);

    /**
     * 分页结果。nextCursor 为 null 表示无更多。
     */
    record Page(List<Task> items, String nextCursor, boolean hasMore) {
    }

    /**
     * 按 id 查询任务详情，不存在返回 null。
     */
    Task findById(UUID taskId);

    /**
     * 应用一次任务变更（派工/改派/改期/取消）。
     *
     * @param command 变更命令
     * @return 变更后的任务；若 expectedRevision 不匹配抛 OptimisticLockException
     */
    Task applyChange(ChangeCommand command);

    /**
     * 变更命令。action 决定需要哪些字段：
     * ASSIGN/REASSIGN 需 assigneeId；RESCHEDULE 需 plannedDate；CANCEL 两者都不需要。
     * reason 必填。
     */
    record ChangeCommand(
            UUID taskId,
            UUID actorId,
            String action,
            String reason,
            UUID assigneeId,
            LocalDate plannedDate,
            long expectedRevision) {
    }

    /**
     * 员工创建应急任务（creation_source=EMPLOYEE_EMERGENCY）。
     * 返回新建的任务；同 id 幂等返回原任务。
     */
    Task createEmergency(EmergencyCommand command);

    /**
     * 应急任务创建命令。
     */
    record EmergencyCommand(
            UUID taskId,
            UUID storeId,
            UUID actorId,
            LocalDate plannedDate,
            String reason,
            java.time.Instant discoveredAt,
            UUID offlineContextId) {
    }

    /**
     * 乐观锁冲突：客户端 ETag（revision）与当前不一致。
     */
    class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
