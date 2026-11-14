package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 应急任务输入（对应 openapi EmergencyTaskInput）。
 */
public record EmergencyTaskInput(
        @NotBlank String id,
        @NotBlank String storeId,
        @NotBlank String plannedDate,
        @NotBlank String reason,
        @NotBlank String discoveredAt,
        String offlineContextId,
        List<String> acknowledgedDuplicateTaskIds,
        String distinctEventReason) {
}
