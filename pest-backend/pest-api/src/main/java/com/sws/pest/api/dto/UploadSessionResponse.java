package com.sws.pest.api.dto;

/**
 * 上传会话响应（对应 openapi UploadSession）。
 */
public record UploadSessionResponse(
        String id,
        String attachmentId,
        String status,
        String uploadPath,
        String expiresAt,
        long maxBytes,
        boolean isAlreadyVerified) {
}
