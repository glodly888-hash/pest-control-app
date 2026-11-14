package com.sws.pest.api.dto;

/**
 * 报告生成重试请求（对应 openapi retryPublication requestBody）。
 */
public record RetryPublicationInput(String reason) {
}
