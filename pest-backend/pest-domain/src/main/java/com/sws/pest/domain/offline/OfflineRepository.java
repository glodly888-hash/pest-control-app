package com.sws.pest.domain.offline;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 离线同步持久化端口（offline_context / offline_submission / sync_exception / offline_asset）。
 *
 * 首期简化：
 * - 上下文正文（contextBody）以 JSON 字符串透传，contextDigest 用 SHA-256（非严格 JCS）；
 * - 离线提交两段式：接收阶段持久化原包 + 入队 SYNC_APPLY，应用阶段由 worker 异步执行。
 */
public interface OfflineRepository {

    // ---- 上下文 ----

    record OfflineContextView(
            UUID id, UUID storeId, UUID taskId, UUID userId, String deviceId,
            long permissionVersion, String contextDigest, String contextBody,
            Instant expiresAt, Instant evidenceUntil, Instant downloadedAt,
            Instant revokedAt, Instant issuedAt, long downloadBytes) {
    }

    /**
     * 创建或复用离线上下文（同 store+task+user 已存在且未过期则复用）。
     */
    OfflineContextView createContext(UUID id, UUID storeId, UUID taskId, UUID userId,
                                     String deviceId, long permissionVersion,
                                     String contextDigest, String contextBody,
                                     Instant expiresAt, Instant evidenceUntil);

    /**
     * 把结构化 contextBody 序列化为 JSON 字符串（由 infrastructure 用 Jackson 实现，
     * 避免 application 层依赖 JSON 库）。序列化产物与 contextDigest 计算口径一致。
     */
    String serializeContextBody(OfflineContextBody body);

    Optional<OfflineContextView> findContext(UUID contextId);

    /** 标记资料完整下载回执。 */
    void markDownloaded(UUID contextId, Instant downloadedAt);

    // ---- 提交 ----

    record SubmissionView(
            UUID id, UUID userId, UUID contextId, UUID serviceId, UUID taskId,
            long assignmentVersion, String status, String resultJson,
            Instant receivedAt, Instant appliedAt) {
    }

    /**
     * 接收离线提交包（事务内持久化 + 入队 SYNC_APPLY）。
     * 同 id 相同 requestHash 幂等返回原记录；同 id 不同内容抛冲突。
     */
    SubmissionView receive(UUID submissionId, UUID userId, UUID contextId, UUID serviceId,
                           UUID taskId, long assignmentVersion, String requestHash,
                           String canonicalPayload, Instant receivedAt);

    Optional<SubmissionView> findSubmission(UUID submissionId);

    // ---- 异常 ----

    record SyncExceptionView(
            UUID id, UUID submissionId, String kind, String status, String message,
            UUID submittedServiceId, Instant createdAt, String resolution,
            Instant resolvedAt, UUID taskId, long currentTaskRevision) {
    }

    List<SyncExceptionView> listExceptions(String status, UUID actorId);

    Optional<SyncExceptionView> findException(UUID exceptionId);

    /** 处理同步异常（REASSIGN_TO_ORIGINAL / KEEP_AS_EVIDENCE / RETURN_FOR_REBASE）。 */
    SyncExceptionView resolve(UUID exceptionId, UUID actorId, String decision,
                              String reason, long expectedTaskRevision);

    // ---- 离线资源 ----

    record OfflineAssetView(UUID id, String role, String sha256, String objectKey,
                            long sizeBytes, String mimeType) {
    }

    Optional<OfflineAssetView> findAsset(UUID assetId);

    byte[] readAssetContent(UUID assetId);

    /** 冲突异常（映射 409）。 */
    class Conflict extends RuntimeException {
        public Conflict(String message) {
            super(message);
        }
    }

    /** 资源不存在（映射 404）。 */
    class NotFound extends RuntimeException {
        public NotFound(String message) {
            super(message);
        }
    }
}
