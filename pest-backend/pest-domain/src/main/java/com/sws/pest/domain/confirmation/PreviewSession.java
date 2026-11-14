package com.sws.pest.domain.confirmation;

import java.time.Instant;
import java.util.UUID;

/**
 * 客户阅读回执（preview_session 表实体）。
 */
public record PreviewSession(
        UUID id,
        UUID snapshotId,
        UUID userId,
        String contentHash,
        Instant startedAt,
        Instant completedAt,
        long visibleDurationMs,
        boolean isOffline,
        String deviceId,
        Instant createdAt
) {
    /** 是否可视为有效回执：前台可见时长至少 5000ms，且非离线（离线不能视为可信服务器计时）。 */
    public boolean canAcknowledge() {
        return visibleDurationMs >= 5000 && !isOffline;
    }

    /** 最短有效时长（毫秒）。 */
    public static final long MIN_VISIBLE_DURATION_MS = 5000;
}
