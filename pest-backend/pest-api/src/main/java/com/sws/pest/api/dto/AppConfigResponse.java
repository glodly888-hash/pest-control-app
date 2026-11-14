package com.sws.pest.api.dto;

/**
 * 客户端配置响应（对应 openapi AppConfig）。
 */
public record AppConfigResponse(
        String configVersion,
        int recommendedMinutes,
        int previewMinSeconds,
        int maxImageBytes,
        int maxDocumentBytes,
        int maxSubmissionBytes,
        int uploadSessionTtlSeconds,
        int offlineGrantHours,
        int maxOfflineTasks,
        String supportContact,
        String privacyVersion,
        String termsVersion,
        String templateVersion) {
}
