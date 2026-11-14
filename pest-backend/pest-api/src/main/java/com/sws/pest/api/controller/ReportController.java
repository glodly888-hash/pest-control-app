package com.sws.pest.api.controller;

import com.sws.pest.api.NotFoundException;
import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.CorrectionInput;
import com.sws.pest.api.dto.ReportListResponse;
import com.sws.pest.api.dto.ReportResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.reporting.ReportQueryService;
import com.sws.pest.application.workorder.ServiceDraftService;
import com.sws.pest.domain.reporting.Report;
import com.sws.pest.domain.reporting.ReportRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 报告查询接口（对应 openapi listReports / getReport / downloadReport / createCorrection）。
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportQueryService reportQueryService;
    private final ServiceDraftService serviceDraftService;

    public ReportController(ReportQueryService reportQueryService,
                            ServiceDraftService serviceDraftService) {
        this.reportQueryService = reportQueryService;
        this.serviceDraftService = serviceDraftService;
    }

    @GetMapping
    public ApiResponse<ReportListResponse> list(
            HttpServletRequest request,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String status) {
        UUID actorId = requireLogin(request);
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);

        ReportRepository.ReportPage page = reportQueryService.list(
                actorId, cursor, effectiveLimit, storeId, serviceId, status);

        List<ReportResponse> items = page.items().stream()
                .map(ReportController::toResponse)
                .toList();
        return ApiResponse.of(new ReportListResponse(items, page.nextCursor(), page.hasMore()));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<ReportResponse> get(
            HttpServletRequest request,
            @PathVariable UUID reportId) {
        requireLogin(request);
        Report report = reportQueryService.getById(reportId);
        if (report == null) {
            throw new NotFoundException("报告不存在");
        }
        return ApiResponse.of(toResponse(report));
    }

    /** downloadReport：下载或系统分享 PDF。仅 PUBLISHED/SUPERSEDED。 */
    @GetMapping("/{reportId}/content")
    public ResponseEntity<byte[]> download(
            HttpServletRequest request,
            @PathVariable UUID reportId) {
        requireLogin(request);
        Report report = reportQueryService.getById(reportId);
        if (report == null) {
            throw new NotFoundException("报告不存在");
        }
        byte[] pdf = reportQueryService.downloadPdf(reportId);

        String fileName = report.reportNumber() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(pdf.length);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    /** createCorrection：创建报告更正草稿（201）。 */
    @PostMapping("/{reportId}/corrections")
    public ResponseEntity<ApiResponse<ReportResponse>> createCorrection(
            HttpServletRequest request,
            @PathVariable UUID reportId,
            @RequestBody CorrectionInput input) {
        UUID actorId = requireLogin(request);

        Report report = reportQueryService.getById(reportId);
        if (report == null) {
            throw new NotFoundException("报告不存在");
        }
        serviceDraftService.createCorrection(report.serviceId(), actorId, reportId);

        Report updated = reportQueryService.getById(reportId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(toResponse(updated)));
    }

    private static ReportResponse toResponse(Report r) {
        List<ReportResponse.SignatureEcho> sigs = r.signatures().stream()
                .map(s -> new ReportResponse.SignatureEcho(
                        s.role(), s.signerName(), s.signedAt().toString(), s.rating(), s.comment()))
                .toList();
        return new ReportResponse(
                r.id().toString(),
                r.serviceId().toString(),
                r.taskId().toString(),
                r.storeId().toString(),
                r.reportNumber(),
                r.versionNo(),
                r.status().name(),
                r.snapshotId().toString(),
                r.contentHash(),
                r.pdfSha256(),
                r.publishedAt() != null ? r.publishedAt().toString() : null,
                r.serviceStartedAt() != null ? r.serviceStartedAt().toString() : null,
                r.serviceEndedAt() != null ? r.serviceEndedAt().toString() : null,
                sigs,
                r.supersedesReportId() != null ? r.supersedesReportId().toString() : null,
                r.openRiskCountAtService(),
                r.templateVersion());
    }

    private static UUID requireLogin(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        return userId;
    }
}
