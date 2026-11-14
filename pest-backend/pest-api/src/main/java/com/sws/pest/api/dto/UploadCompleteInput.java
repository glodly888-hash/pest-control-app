package com.sws.pest.api.dto;

/**
 * 上传完成校验请求（对应 openapi UploadCompleteInput）。
 */
public record UploadCompleteInput(String sha256, Long sizeBytes) {
}
