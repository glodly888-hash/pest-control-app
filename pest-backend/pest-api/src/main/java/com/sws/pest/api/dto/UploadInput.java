package com.sws.pest.api.dto;

/**
 * 创建附件上传会话请求（对应 openapi UploadInput）。
 */
public record UploadInput(
        String attachmentId,
        String contextId,
        String serviceId,
        String fileName,
        String mediaType,
        Long sizeBytes,
        String sha256,
        String purpose,
        String capturedAt) {
}
