package com.sws.pest.api.dto;

/**
 * 签署凭证响应（对应 openapi SignatureRecord）。
 *
 * signature 回显请求，isValid 表示当前是否有效（未作废），receivedAt 为服务器接收时间。
 */
public record SignatureResponse(
        SignatureEcho signature,
        boolean isValid,
        String receivedAt) {

    /** 回显的签署字段（精简自 SignatureInput）。 */
    public record SignatureEcho(
            String id,
            String role,
            String snapshotId,
            String contentHash,
            String signerName,
            String staffUserId,
            String staffSignatureId,
            String previewSessionId,
            String signedAt,
            Integer rating,
            String comment) {
    }
}
