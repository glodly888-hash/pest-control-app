package com.sws.pest.domain.reporting;

import java.time.Instant;
import java.util.UUID;

/**
 * 报告生成业务任务（publication_job 表）。
 *
 * publishReport 返回 202 仅表示排队，实际 PDF 渲染由 worker 异步完成，
 * 成功后写 service_report 并置 phase=PUBLISHED。
 */
public final class PublicationJob {

    public enum Status {
        QUEUED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        BLOCKED
    }

    private final UUID id;
    private final UUID serviceId;
    private final UUID snapshotId;
    private final UUID staffSignatureId;
    private final UUID clientSignatureId;
    private final Status status;
    private final String errorCode;
    private final String errorMessage;
    private final UUID reportId;
    private final Instant createdAt;
    private final Instant updatedAt;

    public PublicationJob(UUID id, UUID serviceId, UUID snapshotId, UUID staffSignatureId,
                          UUID clientSignatureId, Status status, String errorCode,
                          String errorMessage, UUID reportId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.serviceId = serviceId;
        this.snapshotId = snapshotId;
        this.staffSignatureId = staffSignatureId;
        this.clientSignatureId = clientSignatureId;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.reportId = reportId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() { return id; }
    public UUID serviceId() { return serviceId; }
    public UUID snapshotId() { return snapshotId; }
    public UUID staffSignatureId() { return staffSignatureId; }
    public UUID clientSignatureId() { return clientSignatureId; }
    public Status status() { return status; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public UUID reportId() { return reportId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public boolean canRetry() { return status == Status.FAILED || status == Status.BLOCKED; }
}
