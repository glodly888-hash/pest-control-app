package com.sws.pest.domain.workorder;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 服务任务（service_task 聚合）。
 *
 * 架构设计约束：
 * - 主执行人唯一（assigneeId），派工/取消/改期递增 assignmentVersion；
 * - status 是业务状态，不含上传状态（LocalSyncStatus 仅客户端保存）；
 * - plannedDate 是门店本地日期，跨时区比较前需按门店 timezone 换算。
 */
public final class Task {

    private final UUID id;
    private final UUID storeId;
    private final String storeName;
    private final String serviceType;
    private final ServiceStatus status;
    private final LocalDate plannedDate;
    private final Instant plannedStartAt;
    private final Instant plannedEndAt;
    private final UUID assigneeId;
    private final long assignmentVersion;
    private final long revision;
    private final String instructions;
    private final String source;
    private final UUID serviceId;
    private final boolean overdue;
    private final UUID latestReportId;
    private final List<ModuleState> moduleStates;

    public Task(UUID id, UUID storeId, String storeName, String serviceType,
                ServiceStatus status, LocalDate plannedDate, Instant plannedStartAt,
                Instant plannedEndAt, UUID assigneeId, long assignmentVersion,
                long revision, String instructions, String source, UUID serviceId,
                boolean overdue, UUID latestReportId, List<ModuleState> moduleStates) {
        this.id = id;
        this.storeId = storeId;
        this.storeName = storeName;
        this.serviceType = serviceType;
        this.status = status;
        this.plannedDate = plannedDate;
        this.plannedStartAt = plannedStartAt;
        this.plannedEndAt = plannedEndAt;
        this.assigneeId = assigneeId;
        this.assignmentVersion = assignmentVersion;
        this.revision = revision;
        this.instructions = instructions;
        this.source = source;
        this.serviceId = serviceId;
        this.overdue = overdue;
        this.latestReportId = latestReportId;
        this.moduleStates = moduleStates;
    }

    public UUID id() { return id; }
    public UUID storeId() { return storeId; }
    public String storeName() { return storeName; }
    public String serviceType() { return serviceType; }
    public ServiceStatus status() { return status; }
    public LocalDate plannedDate() { return plannedDate; }
    public Instant plannedStartAt() { return plannedStartAt; }
    public Instant plannedEndAt() { return plannedEndAt; }
    public UUID assigneeId() { return assigneeId; }
    public long assignmentVersion() { return assignmentVersion; }
    public long revision() { return revision; }
    public String instructions() { return instructions; }
    public String source() { return source; }
    public UUID serviceId() { return serviceId; }
    public boolean overdue() { return overdue; }
    public UUID latestReportId() { return latestReportId; }
    public List<ModuleState> moduleStates() { return moduleStates; }

    /**
     * 模块完成度状态（UNFILLED / DRAFT / COMPLETE / NOT_APPLICABLE）。
     * 首期简化：由 service_draft 的模块填写情况推导，先返回占位。
     */
    public record ModuleState(String module, String state) {
    }
}
