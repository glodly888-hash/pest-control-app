package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.PublicationJobResponse;
import com.sws.pest.api.dto.PublishInput;
import com.sws.pest.api.dto.RetryPublicationInput;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.reporting.PublicationService;
import com.sws.pest.domain.reporting.PublicationJob;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 报告发布接口（对应 openapi publishReport / getPublication / retryPublication）。
 *
 * POST /services/{serviceId}/publications —— 请求生成正式报告，返回 202 排队。
 * GET  /publication-jobs/{jobId}           —— 查询报告生成结果。
 * POST /publication-jobs/{jobId}/retries   —— 重试报告生成。
 */
@RestController
public class PublicationController {

    private final PublicationService publicationService;

    public PublicationController(PublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @PostMapping("/services/{serviceId}/publications")
    public ResponseEntity<ApiResponse<PublicationJobResponse>> publish(
            HttpServletRequest request,
            @PathVariable UUID serviceId,
            @RequestBody PublishInput input) {
        requireLogin(request);

        UUID jobId = input.id() != null ? UUID.fromString(input.id()) : UUID.randomUUID();
        UUID snapshotId = UUID.fromString(input.snapshotId());
        UUID staffSignatureId = UUID.fromString(input.staffSignatureId());
        UUID customerSignatureId = UUID.fromString(input.customerSignatureId());

        PublicationJob job = publicationService.publish(
                jobId, serviceId, snapshotId, staffSignatureId, customerSignatureId);

        return ResponseEntity.status(202).body(ApiResponse.of(toResponse(job)));
    }

    /** getPublication：查询报告生成结果。 */
    @GetMapping("/publication-jobs/{jobId}")
    public ApiResponse<PublicationJobResponse> get(
            HttpServletRequest request,
            @PathVariable UUID jobId) {
        requireLogin(request);
        PublicationJob job = publicationService.getById(jobId);
        return ApiResponse.of(toResponse(job));
    }

    /** retryPublication：重试报告生成。 */
    @PostMapping("/publication-jobs/{jobId}/retries")
    public ResponseEntity<ApiResponse<PublicationJobResponse>> retry(
            HttpServletRequest request,
            @PathVariable UUID jobId,
            @RequestBody(required = false) RetryPublicationInput input) {
        requireLogin(request);
        String reason = input != null ? input.reason() : null;
        PublicationJob job = publicationService.retry(jobId, reason);
        return ResponseEntity.status(202).body(ApiResponse.of(toResponse(job)));
    }

    private static PublicationJobResponse toResponse(PublicationJob job) {
        return new PublicationJobResponse(
                job.id().toString(),
                job.serviceId().toString(),
                job.snapshotId().toString(),
                job.status().name(),
                job.reportId() != null ? job.reportId().toString() : null,
                job.errorCode(),
                job.errorMessage(),
                job.canRetry(),
                job.createdAt().toString(),
                job.updatedAt().toString());
    }

    private static void requireLogin(HttpServletRequest request) {
        if (JwtAuthFilter.currentUserId(request) == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
    }
}
