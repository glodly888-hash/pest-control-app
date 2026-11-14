package com.sws.pest.domain.platform;

/**
 * 客户端应用配置（对应 openapi AppConfig）。
 *
 * 来源为 config_release(kind=APP) 的 payload_json，字段均为有界阈值/版本号，
 * 缺省时返回内置默认值（见 {@link #defaults}）。
 */
public record AppConfig(
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

    /** 首期内置默认值：与 openapi 描述一致（建议时长 30、预览 5 秒）。 */
    public static AppConfig defaults() {
        return new AppConfig(
                "default-1",
                30, 5,
                10 * 1024 * 1024,      // 图片 10MB
                20 * 1024 * 1024,      // 文档 20MB
                50 * 1024 * 1024,      // 提交 50MB
                3600,                   // 上传会话 1 小时
                72,                     // 离线授权 72 小时
                20,                     // 最多离线任务 20
                "",
                "privacy-1",
                "terms-1",
                "template-1");
    }
}
