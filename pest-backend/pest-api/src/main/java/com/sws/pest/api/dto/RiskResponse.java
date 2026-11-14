package com.sws.pest.api.dto;

import java.util.List;

/**
 * 风险台账详情（对应 openapi Risk schema）。
 */
public record RiskResponse(
        String id,
        String storeId,
        String typeId,
        List<String> pestTypeIds,
        String areaId,
        String floor,
        String position,
        String description,
        String suggestion,
        String responsibleParty,
        String contactName,
        String ownerId,
        String dueDate,
        String status,
        long revision,
        String discoveredAt,
        String closedAt,
        List<AttachmentRefResponse> photos) {

    public record AttachmentRefResponse(String attachmentId) {}
}
