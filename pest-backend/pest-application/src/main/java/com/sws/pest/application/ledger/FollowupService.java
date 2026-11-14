package com.sws.pest.application.ledger;

import com.sws.pest.domain.ledger.FollowupRepository;
import com.sws.pest.domain.ledger.Risk;
import com.sws.pest.domain.ledger.RiskFollowup;
import com.sws.pest.domain.ledger.RiskRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 风险跟进用例。
 *
 * 状态机规则（result → 目标状态）：
 * - NO_CHANGE / IN_PROGRESS：目标状态 PENDING/IN_PROGRESS（视 from 而定，见下）
 * - SUBMIT_REVIEW：AWAITING_REVIEW
 * - APPROVE：CLOSED
 * - REJECT：IN_PROGRESS（驳回继续整改）
 * - NOT_CHECKED：保持 from 状态
 * - REOPEN：仅 CLOSED 可重开 → IN_PROGRESS
 *
 * 首期简化状态映射：
 *   PENDING + IN_PROGRESS → IN_PROGRESS（开始整改）
 *   PENDING + NO_CHANGE / NOT_CHECKED → PENDING
 *   IN_PROGRESS + NO_CHANGE / NOT_CHECKED → IN_PROGRESS
 *   IN_PROGRESS / AWAITING_REVIEW + SUBMIT_REVIEW → AWAITING_REVIEW
 *   AWAITING_REVIEW + APPROVE → CLOSED
 *   AWAITING_REVIEW / IN_PROGRESS + REJECT → IN_PROGRESS
 *   CLOSED + REOPEN → IN_PROGRESS
 */
@Service
public class FollowupService {

    private final FollowupRepository followupRepository;
    private final RiskRepository riskRepository;

    public FollowupService(FollowupRepository followupRepository, RiskRepository riskRepository) {
        this.followupRepository = followupRepository;
        this.riskRepository = riskRepository;
    }

    public record FollowupPage(List<FollowupRepository.FollowupView> items,
                               String nextCursor, boolean hasMore) {}

    public FollowupRepository.FollowupView createFollowup(RiskFollowup entry, UUID operatorId,
                                                          UUID serviceId, String contextId) {
        UUID riskId = entry.riskId();
        Risk risk = riskRepository.findById(riskId)
                .orElseThrow(() -> new RiskRepository.RiskNotFound("风险不存在"));

        // 状态机：从当前状态 + result 推导目标状态
        Risk.Status from = risk.status();
        Risk.Status to = deriveTarget(from, entry.result());

        // 乐观锁 + 台账事件写入（事务内）
        long revisionAfter;
        try {
            revisionAfter = followupRepository.apply(
                    entry, operatorId, serviceId, risk.storeId(), from, to, Instant.now());
        } catch (FollowupRepository.OptimisticLockException e) {
            throw new FollowupConflict(e.getMessage());
        }

        return new FollowupRepository.FollowupView(
                entry.id(), riskId, revisionAfter, operatorId, serviceId, Instant.now(), entry);
    }

    public FollowupPage listFollowups(UUID riskId, String cursor, int limit) {
        if (!riskRepository.exists(riskId)) {
            throw new RiskRepository.RiskNotFound("风险不存在");
        }
        List<FollowupRepository.FollowupView> all =
                followupRepository.listByRisk(riskId, cursor, limit);
        boolean hasMore = all.size() > limit;
        if (hasMore) {
            all = all.subList(0, limit);
        }
        String nextCursor = "";
        if (hasMore && !all.isEmpty()) {
            FollowupRepository.FollowupView last = all.get(all.size() - 1);
            nextCursor = last.receivedAt() + "|" + last.id();
        }
        return new FollowupPage(all, nextCursor, hasMore);
    }

    /** 由当前状态 + 跟进结果推导目标状态。 */
    private Risk.Status deriveTarget(Risk.Status from, RiskFollowup.Result result) {
        return switch (result) {
            case SUBMIT_REVIEW -> Risk.Status.AWAITING_REVIEW;
            case APPROVE -> {
                if (from != Risk.Status.AWAITING_REVIEW && from != Risk.Status.IN_PROGRESS) {
                    throw new FollowupConflict("仅待复核或整改中的风险可审批关闭");
                }
                yield Risk.Status.CLOSED;
            }
            case REJECT -> {
                if (from == Risk.Status.CLOSED) {
                    throw new FollowupConflict("已关闭风险不可驳回");
                }
                yield Risk.Status.IN_PROGRESS;
            }
            case REOPEN -> {
                if (from != Risk.Status.CLOSED) {
                    throw new FollowupConflict("仅已关闭风险可重开");
                }
                yield Risk.Status.IN_PROGRESS;
            }
            case IN_PROGRESS -> {
                if (from == Risk.Status.CLOSED) {
                    throw new FollowupConflict("已关闭风险不可改回进行中");
                }
                yield Risk.Status.IN_PROGRESS;
            }
            case NO_CHANGE, NOT_CHECKED -> from;
        };
    }

    public static class FollowupConflict extends RuntimeException {
        public FollowupConflict(String msg) { super(msg); }
    }
}
