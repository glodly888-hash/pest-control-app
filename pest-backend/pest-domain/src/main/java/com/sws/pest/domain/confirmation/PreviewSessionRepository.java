package com.sws.pest.domain.confirmation;

import java.util.Optional;
import java.util.UUID;

/**
 * 客户阅读回执持久化端口（preview_session 表）。
 */
public interface PreviewSessionRepository {

    /** 保存阅读回执，返回完整记录（含 receivedAt）。 */
    PreviewSession save(PreviewSession session);

    /** 按 id 查阅读回执。 */
    Optional<PreviewSession> findById(UUID sessionId);

    /** 校验快照是否存在。 */
    boolean snapshotExists(UUID snapshotId);

    /** 快照不存在。 */
    class SnapshotNotFound extends RuntimeException {
        public SnapshotNotFound(String message) { super(message); }
    }
}
