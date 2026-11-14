package com.sws.pest.api.dto;

/**
 * 创建服务草稿请求（对应 CreateServiceInput）。
 */
public record CreateServiceInput(
        String id,
        String contextId,
        Long assignmentVersion) {
}
