package com.sws.pest.api.dto;

/**
 * 错误响应（对应 openapi Error 响应体，简化版）。
 */
public record ErrorResponse(String code, String message, String requestId) {
}
