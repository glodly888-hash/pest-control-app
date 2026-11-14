package com.sws.pest.domain.platform;

import java.time.Instant;
import java.util.UUID;

/**
 * 服务须知（对应 openapi Notice）。
 */
public record Notice(
        UUID id,
        String version,
        String title,
        String content,
        Instant effectiveAt) {
}
