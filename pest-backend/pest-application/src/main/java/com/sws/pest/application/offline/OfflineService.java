package com.sws.pest.application.offline;

import com.sws.pest.domain.ledger.Risk;
import com.sws.pest.domain.ledger.RiskRepository;
import com.sws.pest.domain.masterdata.Facility;
import com.sws.pest.domain.masterdata.FacilityRepository;
import com.sws.pest.domain.masterdata.Store;
import com.sws.pest.domain.masterdata.StoreRepository;
import com.sws.pest.domain.offline.OfflineContextBody;
import com.sws.pest.domain.offline.OfflineRepository;
import com.sws.pest.domain.platform.DictionaryItem;
import com.sws.pest.domain.platform.PlatformConfigRepository;
import com.sws.pest.domain.workorder.Task;
import com.sws.pest.domain.workorder.TaskRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 离线同步用例（getTaskContext / getStoreContext / confirmDownload /
 * submitOfflineWork / getSubmission / sync exception 处理）。
 *
 * 首期简化：
 * - contextBody 以最小 JSON 字符串组装（不含完整字典/品牌/资产清单）；
 * - contextDigest 用 SHA-256（非严格 JCS）；
 * - submitOfflineWork 仅接收原包 + 入队，应用由 worker 异步执行。
 */
@Service
public class OfflineService {

    private final OfflineRepository offlineRepository;
    private final StoreRepository storeRepository;
    private final FacilityRepository facilityRepository;
    private final RiskRepository riskRepository;
    private final TaskRepository taskRepository;
    private final PlatformConfigRepository platformConfigRepository;

    public OfflineService(OfflineRepository offlineRepository,
                          StoreRepository storeRepository,
                          FacilityRepository facilityRepository,
                          RiskRepository riskRepository,
                          TaskRepository taskRepository,
                          PlatformConfigRepository platformConfigRepository) {
        this.offlineRepository = offlineRepository;
        this.storeRepository = storeRepository;
        this.facilityRepository = facilityRepository;
        this.riskRepository = riskRepository;
        this.taskRepository = taskRepository;
        this.platformConfigRepository = platformConfigRepository;
    }

    /**
     * 下载任务上下文（getTaskContext）。
     */
    public OfflineRepository.OfflineContextView getTaskContext(UUID taskId, UUID actorId,
                                                               UUID contextId, String deviceId) {
        Task task = taskRepository.findById(taskId);
        if (task == null) {
            throw new OfflineRepository.NotFound("任务不存在");
        }
        if (!task.assigneeId().equals(actorId)) {
            throw new IllegalArgumentException("无权下载该任务上下文");
        }
        return buildContext(contextId, task.storeId(), taskId, actorId, deviceId);
    }

    /**
     * 下载门店应急上下文（getStoreContext）。
     */
    public OfflineRepository.OfflineContextView getStoreContext(UUID storeId, UUID actorId,
                                                                UUID contextId, String deviceId) {
        if (!riskRepository.storeExists(storeId)) {
            throw new OfflineRepository.NotFound("门店不存在");
        }
        return buildContext(contextId, storeId, null, actorId, deviceId);
    }

    private OfflineRepository.OfflineContextView buildContext(UUID contextId, UUID storeId,
                                                              UUID taskId, UUID actorId,
                                                              String deviceId) {
        long permissionVersion = 1L; // 首期简化
        Instant expiresAt = Instant.now().plus(Duration.ofDays(30));
        Instant evidenceUntil = expiresAt.plus(Duration.ofDays(7));

        String contextBody = assembleContextBody(storeId, taskId, actorId);
        String contextDigest = sha256(contextBody);

        return offlineRepository.createContext(contextId, storeId, taskId, actorId, deviceId,
                permissionVersion, contextDigest, contextBody, expiresAt, evidenceUntil);
    }

    /**
     * 组装完整 contextBody（结构化 POJO），由 infrastructure 序列化为 JSON。
     *
     * 内容：门店快照、在用设施台账、未关闭风险、门店分区、业务字典
     * （虫种 / 药剂 / 设施类型）。前端据此渲染完整离线表单的下拉选项。
     */
    private String assembleContextBody(UUID storeId, UUID taskId, UUID actorId) {
        // 门店快照（含分区）
        Store store = storeRepository.findDetailById(storeId);
        OfflineContextBody.StoreRef storeRef = null;
        List<Store.Area> areas = List.of();
        if (store != null) {
            storeRef = new OfflineContextBody.StoreRef(
                    store.id().toString(), store.code(), store.name());
            areas = store.areas() == null ? List.of() : store.areas();
        }

        // 在用设施台账（ACTIVE）
        List<Facility> facilities = facilityRepository
                .listByStore(storeId, null, "ACTIVE", null, 1000).items();

        // 未关闭风险（status != CLOSED）
        List<Risk> allRisks = riskRepository.listByStore(storeId, null, null, 1000);
        List<Risk> openRisks = new ArrayList<>();
        for (Risk r : allRisks) {
            if (r.status() != Risk.Status.CLOSED) {
                openRisks.add(r);
            }
        }

        // 业务字典（过滤 isActive，全量）
        List<DictionaryItem> dictionaries = new ArrayList<>();
        var dict = platformConfigRepository.latestDictionary();
        if (dict != null) {
            for (DictionaryItem item : dict.items()) {
                if (item.isActive()) {
                    dictionaries.add(item);
                }
            }
        }

        OfflineContextBody body = new OfflineContextBody(storeRef, facilities, openRisks, areas, dictionaries);
        return offlineRepository.serializeContextBody(body);
    }

    /**
     * 确认离线资料完整下载（confirmDownload）。
     */
    public void confirmDownload(UUID contextId, UUID actorId, Instant downloadedAt) {
        OfflineRepository.OfflineContextView ctx = offlineRepository.findContext(contextId)
                .orElseThrow(() -> new OfflineRepository.NotFound("上下文不存在"));
        if (!ctx.userId().equals(actorId)) {
            throw new IllegalArgumentException("无权确认该上下文");
        }
        offlineRepository.markDownloaded(contextId, downloadedAt);
    }

    /**
     * 提交离线服务包（submitOfflineWork），返回 202 回执。
     */
    public OfflineRepository.SubmissionView submit(UUID submissionId, UUID actorId,
                                                   UUID contextId, UUID serviceId,
                                                   UUID taskId, long assignmentVersion,
                                                   String canonicalPayload) {
        // 校验上下文归属
        OfflineRepository.OfflineContextView ctx = offlineRepository.findContext(contextId)
                .orElseThrow(() -> new OfflineRepository.NotFound("上下文不存在"));
        if (!ctx.userId().equals(actorId)) {
            throw new IllegalArgumentException("无权使用该上下文提交");
        }
        String requestHash = sha256(canonicalPayload);
        return offlineRepository.receive(submissionId, actorId, contextId, serviceId, taskId,
                assignmentVersion, requestHash, canonicalPayload, Instant.now());
    }

    /**
     * 查询离线提交状态（getSubmission）。
     */
    public OfflineRepository.SubmissionView getSubmission(UUID submissionId) {
        return offlineRepository.findSubmission(submissionId)
                .orElseThrow(() -> new OfflineRepository.NotFound("提交不存在"));
    }

    /**
     * 同步异常列表（listSyncExceptions）。
     */
    public java.util.List<OfflineRepository.SyncExceptionView> listExceptions(String status,
                                                                              UUID actorId) {
        return offlineRepository.listExceptions(status, actorId);
    }

    /**
     * 查看同步异常（getSyncException）。
     */
    public OfflineRepository.SyncExceptionView getException(UUID exceptionId) {
        return offlineRepository.findException(exceptionId)
                .orElseThrow(() -> new OfflineRepository.NotFound("同步异常不存在"));
    }

    /**
     * 处理同步异常（resolveSyncException）。
     */
    public OfflineRepository.SyncExceptionView resolve(UUID exceptionId, UUID actorId,
                                                       String decision, String reason,
                                                       long expectedTaskRevision) {
        return offlineRepository.resolve(exceptionId, actorId, decision, reason, expectedTaskRevision);
    }

    /**
     * 下载离线资源（downloadOfflineAsset）。
     */
    public byte[] downloadAsset(UUID assetId) {
        return offlineRepository.readAssetContent(assetId);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
