package com.sws.pest.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * 提交离线服务包（对应 openapi OfflineSubmission）。
 * 首期简化：canonicalPayload 以 JsonNode 透传，服务端按字符串规范化后落库。
 */
public record OfflineSubmissionInput(
        @NotBlank String id,
        @NotBlank String deviceId,
        @NotBlank String contextId,
        String taskId,
        JsonNode emergencyTask,
        @NotBlank String serviceId,
        @NotNull Long assignmentVersion,
        @NotNull Long baseDraftRevision,
        JsonNode draft,
        String snapshotId,
        JsonNode snapshotPayload,
        String contentHash,
        JsonNode confirmations,
        JsonNode publicationRequest,
        @NotBlank String submittedAt) {
}
