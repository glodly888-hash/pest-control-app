package com.sws.pest.application.reporting;

import com.sws.pest.domain.confirmation.Signature;
import com.sws.pest.domain.confirmation.SignatureRepository;
import com.sws.pest.domain.reporting.PublicationJob;
import com.sws.pest.domain.reporting.PublicationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 报告发布用例服务（publishReport）。
 *
 * 重新校验：快照属于该服务、快照已冻结未发布、员工+客户双签齐全。
 * 返回 202 仅表示排队（QUEUED），PDF 渲染与 service_report 生成由 worker 异步完成。
 */
@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final SignatureRepository signatureRepository;

    public PublicationService(PublicationRepository publicationRepository,
                              SignatureRepository signatureRepository) {
        this.publicationRepository = publicationRepository;
        this.signatureRepository = signatureRepository;
    }

    /**
     * 请求生成正式报告（幂等：同 serviceId+snapshotId 返回原任务）。
     */
    public PublicationJob publish(UUID jobId, UUID serviceId, UUID snapshotId,
                                  UUID staffSignatureId, UUID customerSignatureId) {
        // 幂等：同快照已排队则直接返回
        PublicationJob existing = publicationRepository.findBySnapshot(serviceId, snapshotId).orElse(null);
        if (existing != null) {
            return existing;
        }

        // 快照归属与状态校验
        SignatureRepository.SnapshotView snapshot =
                signatureRepository.findSnapshot(snapshotId).orElse(null);
        if (snapshot == null) {
            throw new PublicationNotFound("快照不存在");
        }
        if (!snapshot.service().id().equals(serviceId)) {
            throw new PublicationConflict("快照不属于该服务");
        }
        if (snapshot.published()) {
            throw new PublicationConflict("快照已发布");
        }

        // 双签齐全校验
        Signature staff = signatureRepository.findActive(snapshotId, Signature.Role.STAFF).orElse(null);
        Signature client = signatureRepository.findActive(snapshotId, Signature.Role.CLIENT).orElse(null);
        if (staff == null || client == null) {
            throw new PublicationConflict("需员工与客户双签齐全后方可发布");
        }
        if (!staff.id().equals(staffSignatureId)) {
            throw new PublicationConflict("员工签字不匹配");
        }
        if (!client.id().equals(customerSignatureId)) {
            throw new PublicationConflict("客户签字不匹配");
        }

        PublicationJob job = new PublicationJob(
                jobId, serviceId, snapshotId, staffSignatureId, customerSignatureId,
                PublicationJob.Status.QUEUED, null, null, null,
                java.time.Instant.now(), java.time.Instant.now());
        return publicationRepository.create(job);
    }

    /**
     * 查询报告生成任务结果（getPublication）。
     */
    public PublicationJob getById(UUID jobId) {
        return publicationRepository.findById(jobId)
                .orElseThrow(() -> new PublicationNotFound("发布任务不存在"));
    }

    /**
     * 重试报告生成（retryPublication）：仅 FAILED/BLOCKED 可重试，复用原快照与版本。
     */
    public PublicationJob retry(UUID jobId, String reason) {
        PublicationJob job = publicationRepository.findById(jobId)
                .orElseThrow(() -> new PublicationNotFound("发布任务不存在"));
        if (!job.canRetry()) {
            throw new PublicationConflict("任务当前状态不可重试");
        }
        return publicationRepository.retry(jobId);
    }

    /** 发布任务不存在（404）。 */
    public static class PublicationNotFound extends RuntimeException {
        public PublicationNotFound(String message) { super(message); }
    }

    /** 业务冲突（409）。 */
    public static class PublicationConflict extends RuntimeException {
        public PublicationConflict(String message) { super(message); }
    }
}
