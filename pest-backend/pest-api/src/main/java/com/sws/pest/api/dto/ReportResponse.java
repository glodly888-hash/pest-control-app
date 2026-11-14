package com.sws.pest.api.dto;

import java.util.List;

/**
 * 报告响应（对应 openapi Report）。
 */
public record ReportResponse(
        String id,
        String serviceId,
        String taskId,
        String storeId,
        String reportNumber,
        int version,
        String status,
        String snapshotId,
        String contentHash,
        String pdfSha256,
        String publishedAt,
        String serviceStartedAt,
        String serviceEndedAt,
        List<SignatureEcho> signatures,
        String supersedesReportId,
        int openRiskCountAtService,
        String templateVersion) {

    /** 报告内嵌签署凭证（对应 SignatureRecord 精简）。 */
    public record SignatureEcho(
            String role,
            String signerName,
            String signedAt,
            Integer rating,
            String comment) {
    }
}
