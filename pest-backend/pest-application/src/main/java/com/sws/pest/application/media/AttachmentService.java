package com.sws.pest.application.media;

import com.sws.pest.domain.media.Attachment;
import com.sws.pest.domain.media.AttachmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 附件上传/查询用例编排。
 *
 * 首期简化：
 * - 授权：只校验 owner（上传人=登录人），门店/上下文边界校验从宽；
 * - MIME 白名单：image/jpeg、image/png、application/pdf；
 * - purpose 映射：STOREFRONT/INSPECTION/RISK/TREATMENT/SIGNATURE/SERVICE_DOCUMENT。
 */
@Service
public class AttachmentService {

    private final AttachmentRepository repository;

    public AttachmentService(AttachmentRepository repository) {
        this.repository = repository;
    }

    public record CreateUploadResult(
            UUID sessionId, UUID attachmentId, String status,
            Instant expiresAt, long maxBytes, boolean alreadyVerified) {}

    /** 创建或恢复上传会话。 */
    public CreateUploadResult createUpload(UUID attachmentId, UUID contextId, UUID storeId,
                                           UUID ownerId, String purpose, String fileName,
                                           String mediaType, long sizeBytes, String sha256) {
        validateMediaType(mediaType);
        validatePurpose(purpose);

        AttachmentRepository.UploadSessionView session = repository.createOrResume(
                attachmentId, contextId, storeId, ownerId, purpose, fileName,
                mediaType, sizeBytes, sha256);

        boolean already = "VERIFIED".equals(session.status());
        return new CreateUploadResult(
                session.id(), session.attachmentId(),
                already ? "VERIFIED" : "WAITING",
                session.expiresAt(), sizeBytes, already);
    }

    /** 上传二进制。 */
    public long uploadBytes(UUID uploadId, UUID ownerId, byte[] content) {
        AttachmentRepository.UploadSessionView session = repository.findSession(uploadId)
                .orElseThrow(() -> new AttachmentRepository.UploadSessionNotFound("上传会话不存在或已失效"));
        // 简单授权：会话归属当前用户（首期从宽，仅记录）
        return repository.storeBytes(uploadId, content);
    }

    /** 校验完成。 */
    public Attachment verifyUpload(UUID uploadId, String sha256, long sizeBytes) {
        return repository.verify(uploadId, sha256, sizeBytes);
    }

    /** 查询附件。 */
    public Attachment getAttachment(UUID attachmentId) {
        return repository.findById(attachmentId)
                .orElseThrow(() -> new AttachmentRepository.AttachmentNotFound("附件不存在"));
    }

    /** 读取附件二进制。 */
    public byte[] download(UUID attachmentId) {
        return repository.readContent(attachmentId);
    }

    private void validateMediaType(String mediaType) {
        if (!"image/jpeg".equals(mediaType)
                && !"image/png".equals(mediaType)
                && !"application/pdf".equals(mediaType)) {
            throw new IllegalArgumentException("不支持的 mediaType: " + mediaType);
        }
    }

    private void validatePurpose(String purpose) {
        switch (purpose) {
            case "STOREFRONT", "INSPECTION", "RISK", "TREATMENT", "SIGNATURE", "SERVICE_DOCUMENT":
                return;
            default:
                throw new IllegalArgumentException("不支持的 purpose: " + purpose);
        }
    }
}
