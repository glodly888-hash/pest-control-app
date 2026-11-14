package com.sws.pest.api.dto;

import java.util.List;

/**
 * 风险跟进条目（对应 openapi RiskFollowup schema）。
 */
public record RiskFollowupEntryResponse(
        String id,
        String riskId,
        long expectedRiskRevision,
        String result,
        String description,
        String notCheckedReason,
        String materialSource,
        String materialProvidedBy,
        List<RiskResponse.AttachmentRefResponse> photos,
        String occurredAt,
        String supersedesEventId) {}
