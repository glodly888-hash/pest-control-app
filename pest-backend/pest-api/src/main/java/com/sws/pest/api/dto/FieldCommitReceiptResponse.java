package com.sws.pest.api.dto;

import java.util.List;

/**
 * 现场台账提交回执（对应 openapi FieldCommitReceipt）。
 */
public record FieldCommitReceiptResponse(
        String serviceId,
        long draftRevision,
        String committedAt,
        List<EffectEcho> effects) {

    public record EffectEcho(
            String eventId,
            String resourceId,
            long revision,
            String effect) {
    }
}
