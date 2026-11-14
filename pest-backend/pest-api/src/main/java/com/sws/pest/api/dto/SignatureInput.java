package com.sws.pest.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * 签署凭证请求（对应 openapi SignatureInput）。
 *
 * role：STAFF（员工）/ CUSTOMER（客户）。
 * 客户签字需 staffSignatureId（指向先前员工签字）+ previewSessionId（阅读回执）。
 * 首期不做 attestationHash 的 JCS 全量校验（仅存字段）。
 */
public record SignatureInput(
        String id,
        String role,
        String snapshotId,
        String contentHash,
        String signerName,
        String staffUserId,
        String staffSignatureId,
        JsonNode signatureImage,
        String signedAt,
        String deviceId,
        Boolean isAcknowledged,
        String previewSessionId,
        Integer rating,
        String comment,
        String attestationHash,
        String supersedesSignatureId,
        String replacementReason) {
}
