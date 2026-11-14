package com.sws.pest.domain.reporting;

import java.util.Optional;
import java.util.UUID;

/**
 * 报告生成任务持久化端口（publication_job）。
 */
public interface PublicationRepository {

    /**
     * 按 serviceId+snapshotId 查找已有发布任务（唯一约束，幂等）。
     */
    Optional<PublicationJob> findBySnapshot(UUID serviceId, UUID snapshotId);

    /**
     * 按 ID 查找发布任务。
     */
    Optional<PublicationJob> findById(UUID jobId);

    /**
     * 创建发布任务（QUEUED），事务内同时将 service.phase 置 PUBLISHING。
     * 依赖快照与双签由调用方先校验。
     */
    PublicationJob create(PublicationJob job);

    /**
     * 重试发布任务：仅 FAILED/BLOCKED 且 canRetry 可重试；
     * 事务内将 status 置 QUEUED、attempt_no 加一、清空 error_* 字段，返回更新后的任务。
     * 复用原快照与版本，不创建新报告。
     */
    PublicationJob retry(UUID jobId);
}
