package com.sws.pest.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * 离线上下文响应（对应 openapi OfflineContext）。
 */
public record OfflineContextResponse(
        String id,
        String schemaVersion,
        String contextDigest,
        JsonNode contextBody,
        String grantId,
        long permissionVersion,
        String issuedAt,
        String expiresAt,
        long downloadBytes) {
}
