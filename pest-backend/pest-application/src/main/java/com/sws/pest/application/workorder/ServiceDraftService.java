package com.sws.pest.application.workorder;

import com.sws.pest.domain.workorder.FieldService;
import com.sws.pest.domain.workorder.ServicePhase;
import com.sws.pest.domain.workorder.ServiceRepository;
import com.sws.pest.domain.workorder.Task;
import com.sws.pest.domain.workorder.TaskRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 现场作业草稿用例服务（service / draft / snapshot 编排）。
 *
 * 首期闭环：createService → getDraft → saveDraft → validate → freeze。
 * content 以 JSON 透传，签字/发布留待下一轮。
 */
@Service
public class ServiceDraftService {

    private final ServiceRepository serviceRepository;
    private final TaskRepository taskRepository;

    public ServiceDraftService(ServiceRepository serviceRepository, TaskRepository taskRepository) {
        this.serviceRepository = serviceRepository;
        this.taskRepository = taskRepository;
    }

    /**
     * 创建（或幂等返回）服务草稿。校验任务存在且属于当前员工。
     */
    public FieldService createService(UUID taskId, UUID actorId, UUID serviceId, UUID contextId) {
        Task task = taskRepository.findById(taskId);
        if (task == null) {
            throw new ServiceNotFound("任务不存在: " + taskId);
        }
        if (!task.assigneeId().equals(actorId)) {
            throw new ServiceForbidden("无权为该任务创建服务");
        }

        FieldService existing = serviceRepository.findByTaskId(taskId).orElse(null);
        if (existing != null) {
            return existing;
        }

        // 若无显式 contextId，用服务 ID 派生一个占位上下文（首期不落地完整离线上下文）
        UUID effectiveContextId = contextId != null ? contextId : serviceId;

        FieldService service = new FieldService(
                serviceId, taskId, task.storeId(), actorId,
                effectiveContextId, ServicePhase.EDITABLE, 1L,
                null, null, null, null);
        return serviceRepository.createService(service);
    }

    /**
     * 读取草稿，不存在返回 null。
     */
    public ServiceRepository.DraftView getDraft(UUID serviceId) {
        return serviceRepository.findDraft(serviceId).orElse(null);
    }

    /**
     * 保存草稿。expectedRevision 为 If-Match 版本。
     * 返回新的草稿版本。
     */
    public long saveDraft(UUID serviceId, UUID actorId, long expectedRevision, String contentJson) {
        ServiceRepository.DraftView view = getDraft(serviceId);
        if (view == null) {
            throw new ServiceNotFound("服务不存在: " + serviceId);
        }
        return serviceRepository.saveDraft(serviceId, contentJson, actorId, expectedRevision);
    }

    /**
     * 签字前完整性校验（首期简化：仅要求 checkin 存在）。
     * 返回 canSign + 草稿版本 + 内容 SHA-256。
     */
    public ValidationResult validate(UUID serviceId) {
        ServiceRepository.DraftView view = getDraft(serviceId);
        if (view == null) {
            throw new ServiceNotFound("服务不存在: " + serviceId);
        }
        String content = view.contentJson();
        boolean canSign = content != null && content.contains("\"checkin\"");
        return new ValidationResult(canSign, view.draftRevision(), sha256(content == null ? "" : content));
    }

    /**
     * 冻结快照。校验 draftRevision + contentHash，创建不可变快照，phase → FROZEN。
     */
    public UUID freeze(UUID serviceId, long draftRevision, String contentHash) {
        ServiceRepository.DraftView view = getDraft(serviceId);
        if (view == null) {
            throw new ServiceNotFound("服务不存在: " + serviceId);
        }
        String content = view.contentJson() == null ? "" : view.contentJson();
        String computed = sha256(content);
        if (!computed.equals(contentHash)) {
            throw new ServiceConflict("内容摘要不匹配");
        }
        return serviceRepository.freezeSnapshot(serviceId, draftRevision, contentHash,
                content, content, view.service().headRevision());
    }

    /**
     * 读取快照详情（getSnapshot）。
     */
    public ServiceRepository.SnapshotDetail getSnapshot(UUID snapshotId) {
        return serviceRepository.findSnapshot(snapshotId).orElse(null);
    }

    /**
     * 签后未发布修订（reopenDraft）。
     * 校验服务属于当前员工、快照是当前 active_snapshot，返回新的 head_revision。
     */
    public long reopenDraft(UUID serviceId, UUID actorId, UUID fromSnapshotId) {
        ServiceRepository.DraftView view = getDraft(serviceId);
        if (view == null) {
            throw new ServiceNotFound("服务不存在: " + serviceId);
        }
        if (!view.service().actorId().equals(actorId)) {
            throw new ServiceForbidden("无权修订该服务");
        }
        return serviceRepository.reopenDraft(serviceId, fromSnapshotId, view.service().headRevision());
    }

    /**
     * 已发布报告更正（createCorrection）。
     */
    public void createCorrection(UUID serviceId, UUID actorId, UUID reportId) {
        ServiceRepository.DraftView view = getDraft(serviceId);
        if (view == null) {
            throw new ServiceNotFound("服务不存在: " + serviceId);
        }
        if (!view.service().actorId().equals(actorId)) {
            throw new ServiceForbidden("无权更正该报告");
        }
        serviceRepository.createCorrection(serviceId, reportId, view.service().headRevision());
    }

    public record ValidationResult(boolean canSign, long draftRevision, String contentHash) {
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 服务不存在（映射 404）。 */
    public static class ServiceNotFound extends RuntimeException {
        public ServiceNotFound(String message) {
            super(message);
        }
    }

    /** 无权限（映射 403）。 */
    public static class ServiceForbidden extends RuntimeException {
        public ServiceForbidden(String message) {
            super(message);
        }
    }

    /** 业务冲突（映射 409）。 */
    public static class ServiceConflict extends RuntimeException {
        public ServiceConflict(String message) {
            super(message);
        }
    }
}
