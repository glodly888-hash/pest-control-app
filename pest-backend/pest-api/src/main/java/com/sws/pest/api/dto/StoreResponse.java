package com.sws.pest.api.dto;

import java.util.List;

/**
 * 门店响应（对应 openapi Store）。
 *
 * 列表与详情共用；详情时 regionId/location/revision/areas 有值，列表时可为 null/空。
 */
public record StoreResponse(
        String id,
        String customerId,
        String brandId,
        String code,
        String name,
        String address,
        String timezone,
        String brandName,
        String regionId,
        LocationResponse location,
        long revision,
        String contactName,
        String contactPhone,
        List<AreaResponse> areas) {

    public record AreaResponse(String id, String name, String floor) {
    }

    public record LocationResponse(Double latitude, Double longitude) {
    }
}
