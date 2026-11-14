package com.sws.pest.api.dto;

/**
 * 设施台账响应（对应 openapi Facility）。
 */
public record FacilityResponse(
        String id,
        String storeId,
        String code,
        String typeId,
        String typeName,
        String areaId,
        String floor,
        String position,
        String status,
        long revision,
        String updatedAt) {
}
