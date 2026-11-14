package com.sws.pest.api.dto;

/**
 * 字典项响应（对应 openapi DictionaryItem）。
 */
public record DictionaryItemResponse(
        String id,
        String kind,
        String code,
        String name,
        boolean isActive,
        int revision,
        String formulation) {
}
