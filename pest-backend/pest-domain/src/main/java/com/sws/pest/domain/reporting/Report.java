package com.sws.pest.domain.reporting;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 正式报告（service_report 表 + join service 派生 task_id/store_id）。
 */
public final class Report {

    public enum Status {
        PUBLISHED,
        SUPERSEDED
    }

    private final UUID id;
    private final UUID serviceId;
    private final UUID taskId;
    private final UUID storeId;
    private final String reportNumber;
    private final int versionNo;
    private final Status status;
    private final UUID snapshotId;
    private final String contentHash;
    private final String pdfSha256;
    private final Instant publishedAt;
    private final Instant serviceStartedAt;
    private final Instant serviceEndedAt;
    private final List<ReportSignature> signatures;
    private final UUID supersedesReportId;
    private final int openRiskCountAtService;
    private final String templateVersion;
    private final String objectKey;
    private final long byteSize;

    public Report(UUID id, UUID serviceId, UUID taskId, UUID storeId, String reportNumber,
                  int versionNo, Status status, UUID snapshotId, String contentHash,
                  String pdfSha256, Instant publishedAt, Instant serviceStartedAt,
                  Instant serviceEndedAt, List<ReportSignature> signatures,
                  UUID supersedesReportId, int openRiskCountAtService, String templateVersion,
                  String objectKey, long byteSize) {
        this.id = id;
        this.serviceId = serviceId;
        this.taskId = taskId;
        this.storeId = storeId;
        this.reportNumber = reportNumber;
        this.versionNo = versionNo;
        this.status = status;
        this.snapshotId = snapshotId;
        this.contentHash = contentHash;
        this.pdfSha256 = pdfSha256;
        this.publishedAt = publishedAt;
        this.serviceStartedAt = serviceStartedAt;
        this.serviceEndedAt = serviceEndedAt;
        this.signatures = signatures;
        this.supersedesReportId = supersedesReportId;
        this.openRiskCountAtService = openRiskCountAtService;
        this.templateVersion = templateVersion;
        this.objectKey = objectKey;
        this.byteSize = byteSize;
    }

    public UUID id() { return id; }
    public UUID serviceId() { return serviceId; }
    public UUID taskId() { return taskId; }
    public UUID storeId() { return storeId; }
    public String reportNumber() { return reportNumber; }
    public int versionNo() { return versionNo; }
    public Status status() { return status; }
    public UUID snapshotId() { return snapshotId; }
    public String contentHash() { return contentHash; }
    public String pdfSha256() { return pdfSha256; }
    public Instant publishedAt() { return publishedAt; }
    public Instant serviceStartedAt() { return serviceStartedAt; }
    public Instant serviceEndedAt() { return serviceEndedAt; }
    public List<ReportSignature> signatures() { return signatures; }
    public UUID supersedesReportId() { return supersedesReportId; }
    public int openRiskCountAtService() { return openRiskCountAtService; }
    public String templateVersion() { return templateVersion; }
    public String objectKey() { return objectKey; }
    public long byteSize() { return byteSize; }

    /** 仅 PUBLISHED / SUPERSEDED 状态允许下载 PDF。 */
    public boolean downloadable() { return status == Status.PUBLISHED || status == Status.SUPERSEDED; }

    /** 报告内嵌签署凭证（供 signatures 数组）。 */
    public record ReportSignature(
            String role, String signerName, Instant signedAt, Integer rating, String comment) {
    }
}
