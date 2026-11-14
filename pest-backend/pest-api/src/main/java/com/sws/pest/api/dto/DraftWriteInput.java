package com.sws.pest.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * 保存草稿请求（对应 DraftWrite）。
 * content 是完整的 ServiceContent JSON 对象，透传存储。
 */
public record DraftWriteInput(
        String contextId,
        JsonNode content,
        String savedAt) {
}
