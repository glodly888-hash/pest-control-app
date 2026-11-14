package com.sws.pest.api.dto;

/**
 * 风险跟进记录（对应 openapi RiskFollowupRecord schema：entry + 元数据）。
 */
public record RiskFollowupRecordResponse(
        RiskFollowupEntryResponse entry,
        long riskRevisionAfter,
        String operatorId,
        String serviceId,
        String receivedAt) {}
