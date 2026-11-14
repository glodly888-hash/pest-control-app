package com.sws.pest.domain.workorder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 服务与草稿的持久化端口（service + service_draft + service_snapshot）。
 */
public interface ServiceRepository {

    /**
     * 按任务查找已有服务（幂等：同任务重复创建返回原服务）。
     */
    Optional<FieldService> findByTaskId(UUID taskId);

    /**
     * 创建服务 + 空草稿（事务内），返回新建的服务与草稿版本。
     */
    FieldService createService(FieldService service);

    /**
     * 草稿视图：服务 + 草稿 JSON 内容 + 草稿版本。
     */
    record DraftView(FieldService service, String contentJson, long draftRevision,
                     long fieldCommitRevision, Instant updatedAt) {
    }

    /**
     * 读取服务草稿。
     */
    Optional<DraftView> findDraft(UUID serviceId);

    /**
     * 完整替换草稿内容，draftRevision 递增。
     * expectedRevision 为 If-Match 携带的当前版本，不匹配抛 TaskRepository.OptimisticLockException。
     * 返回新的草稿版本。
     */
    long saveDraft(UUID serviceId, String contentJson, UUID actorId, long expectedRevision);

    /**
     * 冻结草稿为不可变快照，phase 置 FROZEN。
     * 返回快照 ID。
     */
    UUID freezeSnapshot(UUID serviceId, long expectedDraftRevision, String contentHash,
                        String canonicalPayload, String payloadJson, long headRevision);

    /**
     * 快照详情视图（getSnapshot 用）。
     */
    record SnapshotDetail(UUID snapshotId, UUID serviceId, long contentVersion,
                          String contentHash, String payloadJson, String templateVersion,
                          Instant createdAt, boolean isValid) {
    }

    /**
     * 读取快照详情，不存在返回 empty。
     */
    Optional<SnapshotDetail> findSnapshot(UUID snapshotId);

    /**
     * 签后未发布修订：head_revision 递增、旧快照作废、签字失效、
     * 阻断旧发布任务、phase 回到 EDITABLE。返回新的 head_revision。
     * 校验 fromSnapshotId 必须是当前 active_snapshot_id。
     */
    long reopenDraft(UUID serviceId, UUID fromSnapshotId, long expectedHeadRevision);

    /**
     * 已发布报告更正：新建内容代次草稿（correction_of_report_id 指向旧报告），
     * head_revision 递增、active_snapshot_id 置空、phase 回到 EDITABLE、
     * current_report_id 保持指向旧版。返回新建草稿的 service（id 不变）。
     */
    void createCorrection(UUID serviceId, UUID reportId, long expectedHeadRevision);
}
