package com.sws.pest.domain.ledger;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 现场台账提交回执持久化端口（field_commit 表）。
 */
public interface FieldCommitRepository {

    /**
     * 提交回执视图。
     */
    record CommitReceipt(UUID serviceId, long draftRevision, Instant committedAt,
                         List<Effect> effects) {
    }

    /**
     * 单个事件的应用回执。
     */
    record Effect(UUID eventId, UUID resourceId, long revision, String effect) {
    }

    /**
     * 提交现场台账变更（幂等：同 serviceId+draftRevision 返回原回执）。
     * 事务内：校验草稿版本匹配 → 写回执 → 更新 field_commit_revision。
     * 返回回执；草稿版本不匹配抛 TaskRepository.OptimisticLockException。
     */
    CommitReceipt commit(UUID serviceId, long draftRevision, String requestHash);
}
