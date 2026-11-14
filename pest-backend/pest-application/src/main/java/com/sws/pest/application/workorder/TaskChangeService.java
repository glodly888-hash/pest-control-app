package com.sws.pest.application.workorder;

import com.sws.pest.domain.workorder.Task;
import com.sws.pest.domain.workorder.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 任务变更用例服务（派工/改派/改期/取消）。
 *
 * 校验规则（对应 openapi changeTask 语义）：
 * - ASSIGN/REASSIGN 需 assigneeId；RESCHEDULE 需 plannedDate；CANCEL 两者都不需要。
 * - reason 必填。
 * - 乐观锁：expectedRevision 不匹配抛 OptimisticLockException（映射 412）。
 */
@Service
public class TaskChangeService {

    private final TaskRepository taskRepository;

    public TaskChangeService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * @param action           ASSIGN / REASSIGN / RESCHEDULE / CANCEL
     * @param reason           变更原因（必填）
     * @param assigneeId       派工/改派的目标执行人
     * @param plannedDate      改期后的计划日期
     * @param expectedRevision 客户端 If-Match 携带的 revision
     */
    public Task change(UUID taskId, UUID actorId, String action, String reason,
                       UUID assigneeId, LocalDate plannedDate, long expectedRevision) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("变更原因必填");
        }
        // 校验 action 与字段的组合（交给仓储层细化，此处只做空值兜底）
        TaskRepository.ChangeCommand command = new TaskRepository.ChangeCommand(
                taskId, actorId, action, reason, assigneeId, plannedDate, expectedRevision);
        return taskRepository.applyChange(command);
    }

    /**
     * 员工创建应急任务（createEmergencyTask）。
     */
    public Task createEmergency(UUID taskId, UUID storeId, UUID actorId, LocalDate plannedDate,
                                String reason, java.time.Instant discoveredAt,
                                UUID offlineContextId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("应急原因必填");
        }
        TaskRepository.EmergencyCommand command = new TaskRepository.EmergencyCommand(
                taskId, storeId, actorId, plannedDate, reason, discoveredAt, offlineContextId);
        return taskRepository.createEmergency(command);
    }
}
