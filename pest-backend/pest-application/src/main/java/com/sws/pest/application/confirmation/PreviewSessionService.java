package com.sws.pest.application.confirmation;

import com.sws.pest.domain.confirmation.PreviewSession;
import com.sws.pest.domain.confirmation.PreviewSessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 客户阅读回执用例服务（对齐 openapi recordPreview）。
 *
 * 规则：可见阅读时长至少 5000ms 且非离线（联网）才 canAcknowledge=true。
 * 离线证据联网后检验，不能证明客户理解或防止被篡改客户端伪造。
 */
@Service
public class PreviewSessionService {

    private final PreviewSessionRepository repository;

    public PreviewSessionService(PreviewSessionRepository repository) {
        this.repository = repository;
    }

    public record PreviewReceiptResult(
            UUID id, UUID snapshotId, boolean canAcknowledge, long minimumDurationMs, Instant receivedAt) {}

    /**
     * 记录客户阅读过程，返回回执（含 canAcknowledge）。
     */
    public PreviewReceiptResult recordPreview(
            UUID id, UUID snapshotId, UUID userId, String contentHash,
            Instant startedAt, Instant endedAt, long visibleDurationMs,
            boolean isOffline, String deviceId) {

        if (!repository.snapshotExists(snapshotId)) {
            throw new PreviewSessionRepository.SnapshotNotFound("快照不存在");
        }

        // DDL 硬约束 ck_preview_session_1 CHECK (visible_duration_ms >= 5000)：
        // 少于 5000ms 的阅读直接拒绝，不落入数据库 CHECK 的 500。
        if (visibleDurationMs < PreviewSession.MIN_VISIBLE_DURATION_MS) {
            throw new IllegalArgumentException("阅读时长不足 5000ms，无法记录有效回执");
        }

        PreviewSession session = new PreviewSession(
                id, snapshotId, userId, contentHash, startedAt, endedAt,
                visibleDurationMs, isOffline, deviceId, Instant.now());

        PreviewSession saved = repository.save(session);

        return new PreviewReceiptResult(
                saved.id(), saved.snapshotId(), saved.canAcknowledge(),
                PreviewSession.MIN_VISIBLE_DURATION_MS, saved.createdAt());
    }
}
