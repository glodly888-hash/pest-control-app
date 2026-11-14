package com.sws.pest.api.dto;

import java.util.List;

/**
 * 创建风险跟进请求（对应 openapi FollowupInput：entry + contextId）。
 */
public record FollowupInput(FollowupEntryInput entry, String contextId) {

    public record FollowupEntryInput(
            String id,
            String riskId,
            long expectedRiskRevision,
            String result,
            String description,
            String notCheckedReason,
            String materialSource,
            String materialProvidedBy,
            List<PhotoInput> photos,
            String occurredAt,
            String supersedesEventId) {}

    public record PhotoInput(String attachmentId) {}
}
