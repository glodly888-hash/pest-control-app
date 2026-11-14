package com.sws.pest.api.dto;

/**
 * 服务草稿响应（对应 ServiceDraft schema）。
 */
public record ServiceDraftResponse(
        String serviceId,
        String taskId,
        String contextId,
        long draftRevision,
        long fieldCommitRevision,
        String phase,
        String contentJson,
        String updatedAt) {
}
