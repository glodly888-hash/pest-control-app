package com.sws.pest.domain.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 风险跟进写入/读取端口。
 *
 * 台账事件驱动：createFollowup 在同一事务内
 * 1) 插入 ledger_event（kind=RISK_FOLLOWUP，payload 含前后状态）；
 * 2) 插入 risk_followup（引用 ledger_event_id）；
 * 3) 以乐观锁更新 risk.status / revision / last_event_id。
 */
public interface FollowupRepository {

    /** 一条跟进记录（读侧视图）。 */
    record FollowupView(
            UUID id,
            UUID riskId,
            long riskRevisionAfter,
            UUID operatorId,
            UUID serviceId,
            Instant receivedAt,
            RiskFollowup entry) {}

    /** 应用一次跟进，返回应用后 risk 的 revision；乐观锁冲突抛 OptimisticLockException。 */
    long apply(RiskFollowup entry, UUID operatorId, UUID serviceId, UUID storeId,
               Risk.Status fromStatus, Risk.Status toStatus, Instant receivedAt);

    /** 风险跟进历史，按 occurred_at 倒序分页。 */
    List<FollowupView> listByRisk(UUID riskId, String cursor, int limit);

    boolean hasMore(UUID riskId, String cursor, int limit);

    class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String msg) { super(msg); }
    }
}
