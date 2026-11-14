package com.sws.pest.domain.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 风险跟进条目（risk_followup 表 + 对应的 ledger_event）。
 */
public final class RiskFollowup {

    /** 复核结果 / 未核查。 */
    public enum Result {
        NO_CHANGE, IN_PROGRESS, SUBMIT_REVIEW, APPROVE, REJECT, NOT_CHECKED, REOPEN
    }

    /** 材料来源。 */
    public enum MaterialSource {
        ON_SITE, CUSTOMER_PROVIDED, BACKOFFICE
    }

    private final UUID id;
    private final UUID riskId;
    private final long expectedRiskRevision;
    private final Result result;
    private final String description;
    private final String notCheckedReason;
    private final MaterialSource materialSource;
    private final String materialProvidedBy;
    private final List<String> photos;
    private final Instant occurredAt;
    private final UUID supersedesEventId;

    public RiskFollowup(UUID id, UUID riskId, long expectedRiskRevision, Result result,
                        String description, String notCheckedReason, MaterialSource materialSource,
                        String materialProvidedBy, List<String> photos, Instant occurredAt,
                        UUID supersedesEventId) {
        this.id = id;
        this.riskId = riskId;
        this.expectedRiskRevision = expectedRiskRevision;
        this.result = result;
        this.description = description;
        this.notCheckedReason = notCheckedReason;
        this.materialSource = materialSource;
        this.materialProvidedBy = materialProvidedBy;
        this.photos = photos;
        this.occurredAt = occurredAt;
        this.supersedesEventId = supersedesEventId;
    }

    public UUID id() { return id; }
    public UUID riskId() { return riskId; }
    public long expectedRiskRevision() { return expectedRiskRevision; }
    public Result result() { return result; }
    public String description() { return description; }
    public String notCheckedReason() { return notCheckedReason; }
    public MaterialSource materialSource() { return materialSource; }
    public String materialProvidedBy() { return materialProvidedBy; }
    public List<String> photos() { return photos; }
    public Instant occurredAt() { return occurredAt; }
    public UUID supersedesEventId() { return supersedesEventId; }
}
