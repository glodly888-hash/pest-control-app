package com.sws.pest.application.workorder;

import com.sws.pest.domain.workorder.Task;
import com.sws.pest.domain.workorder.TaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 任务查询用例服务（派工/workorder 域）。
 */
@Service
public class TaskQueryService {

    private final TaskRepository taskRepository;

    public TaskQueryService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public TaskRepository.Page listByAssignee(UUID userId, LocalDate startDate, LocalDate endDate,
                                              String status, String keyword, UUID storeId,
                                              String cursor, int limit) {
        return taskRepository.listByAssignee(userId, startDate, endDate, status, keyword,
                storeId, cursor, limit);
    }

    /**
     * 按 id 查任务详情，不存在返回 null。
     */
    public Task getById(UUID taskId) {
        return taskRepository.findById(taskId);
    }
}
