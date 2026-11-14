package com.sws.pest.api.dto;

/**
 * 服务须知响应（对应 openapi Notice）。
 */
public record NoticeResponse(String id, String version, String title, String content, String effectiveAt) {
}
