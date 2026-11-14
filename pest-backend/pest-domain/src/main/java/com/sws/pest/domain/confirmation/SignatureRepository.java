package com.sws.pest.domain.confirmation;

import com.sws.pest.domain.workorder.FieldService;
import com.sws.pest.domain.workorder.TaskRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 签署凭证持久化端口（service_signature + 依赖的 attachment / preview_session）。
 *
 * 首期简化：
 * - 签字图片附件与客户阅读回执以占位记录满足外键约束（真实上传/回执链路后续模块实现）；
 * - 替换未发布凭证（supersedes）与 attestation JCS 摘要仅做字段存储，不做全量校验。
 */
public interface SignatureRepository {

    /**
     * 快照视图：快照对应的服务、内容摘要、快照是否已发布。
     */
    record SnapshotView(UUID snapshotId, FieldService service, String contentHash, boolean published) {
    }

    /**
     * 读取快照及其服务信息。
     */
    Optional<SnapshotView> findSnapshot(UUID snapshotId);

    /**
     * 按快照查找某角色当前有效签字（invalidated_at 为空）。
     */
    Optional<Signature> findActive(UUID snapshotId, Signature.Role role);

    /**
     * 按 ID 查找签字（含已作废）。
     */
    Optional<Signature> findById(UUID signatureId);

    /**
     * 保存签署凭证，返回带 receivedAt 的完整记录。
     * 依赖附件/回执由实现方确保存在（占位）。
     */
    Signature save(Signature signature);

    /** 签字版本冲突（乐观锁，映射 412）。 */
    class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }

    /** 签字依赖的引用已失效（映射 409）。 */
    class InvalidReferenceException extends RuntimeException {
        public InvalidReferenceException(String message) {
            super(message);
        }
    }
}
