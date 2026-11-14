package com.sws.pest.domain.platform;

import java.util.UUID;

/**
 * 业务字典项（对应 openapi DictionaryItem）。
 */
public record DictionaryItem(
        UUID id,
        String kind,
        String code,
        String name,
        boolean isActive,
        int revision,
        String formulation) {
}
