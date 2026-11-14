package com.sws.pest.domain.workorder;

import java.time.Instant;
import java.util.UUID;

/**
 * 实际服务聚合（service 表）。
 *
 * 一任务对应一次实际服务（uk_service_task），phase 表示作业阶段，
 * 与任务业务状态（ServiceStatus）独立。
 *
 * 命名为 FieldService 以区别于 Spring 的 @Service 注解。
 */
public final class FieldService {

    private final UUID id;
    private final UUID taskId;
    private final UUID storeId;
    private final UUID actorId;
    private final UUID contextId;
    private final ServicePhase phase;
    private final long headRevision;
    private final UUID activeSnapshotId;
    private final UUID currentReportId;
    private final Instant startedAt;
    private final Instant completedAt;

    public FieldService(UUID id, UUID taskId, UUID storeId, UUID actorId, UUID contextId,
                        ServicePhase phase, long headRevision, UUID activeSnapshotId,
                        UUID currentReportId, Instant startedAt, Instant completedAt) {
        this.id = id;
        this.taskId = taskId;
        this.storeId = storeId;
        this.actorId = actorId;
        this.contextId = contextId;
        this.phase = phase;
        this.headRevision = headRevision;
        this.activeSnapshotId = activeSnapshotId;
        this.currentReportId = currentReportId;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public UUID id() { return id; }
    public UUID taskId() { return taskId; }
    public UUID storeId() { return storeId; }
    public UUID actorId() { return actorId; }
    public UUID contextId() { return contextId; }
    public ServicePhase phase() { return phase; }
    public long headRevision() { return headRevision; }
    public UUID activeSnapshotId() { return activeSnapshotId; }
    public UUID currentReportId() { return currentReportId; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
}
