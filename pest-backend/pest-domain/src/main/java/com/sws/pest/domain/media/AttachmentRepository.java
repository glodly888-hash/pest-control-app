package com.sws.pest.domain.media;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 附件与上传会话的持久化端口。
 *
 * 首期存储：附件二进制写入本地文件系统（objectKey 指向文件路径），
 * 元数据（大小、sha256、MIME、状态）存 attachment / upload_session 表。
 */
public interface AttachmentRepository {

    /** 上传会话（upload_session 表）视图。 */
    record UploadSessionView(
            UUID id,
            UUID attachmentId,
            String status,
            Instant expiresAt,
            long receivedSize,
            Instant createdAt
    ) {}

    /** 按 id 查附件；不存在返回 empty。 */
    Optional<Attachment> findById(UUID attachmentId);

    /**
     * 创建或恢复上传会话。
     * 幂等规则：同 attachmentId 已存在 READY 附件时返回已校验（isAlreadyVerified=true）；
     * 已有 OPEN 会话则复用；否则新建 attachment（PENDING）+ upload_session（OPEN）。
     */
    UploadSessionView createOrResume(UUID attachmentId, UUID contextId, UUID storeId,
                                     UUID ownerId, String purpose, String fileName,
                                     String mediaType, long sizeBytes, String sha256);

    /** 查上传会话；不存在返回 empty。 */
    Optional<UploadSessionView> findSession(UUID uploadId);

    /** 写入文件二进制，记录 receivedSize，会话置 RECEIVED。返回实际字节数。 */
    long storeBytes(UUID uploadId, byte[] content);

    /** 校验完成：重算 sha256/size 与预期一致，attachment 置 READY + verifiedAt。返回附件。 */
    Attachment verify(UUID uploadId, String sha256, long sizeBytes);

    /** 读附件二进制。附件非 READY 时抛 IllegalStateException。 */
    byte[] readContent(UUID attachmentId);

    /** 附件不存在时抛出的领域异常。 */
    class AttachmentNotFound extends RuntimeException {
        public AttachmentNotFound(String msg) { super(msg); }
    }

    /** 上传会话不存在/已失效。 */
    class UploadSessionNotFound extends RuntimeException {
        public UploadSessionNotFound(String msg) { super(msg); }
    }
}
