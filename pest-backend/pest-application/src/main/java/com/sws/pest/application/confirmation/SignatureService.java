package com.sws.pest.application.confirmation;

import com.sws.pest.domain.confirmation.Signature;
import com.sws.pest.domain.confirmation.SignatureRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 签署凭证用例服务。
 *
 * 业务规则（对齐 openapi createSignature）：
 * 1. 快照必须已冻结且未发布；
 * 2. 同一快照必须先员工（STAFF）后客户（CLIENT）；
 * 3. 员工签字 staffUserId 必须等于登录人；
 * 4. 客户签字必须引用有效员工签字 + 阅读回执（previewSessionId）；
 * 5. 员工/客户各只能有一条有效签字（重复 → 409，除非走 supersedes 替换）。
 */
@Service
public class SignatureService {

    private final SignatureRepository signatureRepository;

    public SignatureService(SignatureRepository signatureRepository) {
        this.signatureRepository = signatureRepository;
    }

    /**
     * 保存签署凭证。role 入参用 STAFF/CUSTOMER，内部映射 CLIENT。
     * 返回完整记录（含 receivedAt）。
     */
    public Signature sign(Signature signature, UUID actorId) {
        SignatureRepository.SnapshotView snapshot =
                signatureRepository.findSnapshot(signature.snapshotId()).orElse(null);
        if (snapshot == null) {
            throw new SignatureNotFound("快照不存在");
        }
        if (snapshot.published()) {
            throw new SignatureConflict("快照已发布，不可再签署");
        }

        Signature.Role role = signature.role();

        // 员工签字：签署人必须是登录人
        if (role == Signature.Role.STAFF) {
            if (signature.staffUserId() == null || !signature.staffUserId().equals(actorId)) {
                throw new SignatureForbidden("员工签字必须为登录人本人");
            }
        }

        // 客户签字：必须引用有效员工签字
        if (role == Signature.Role.CLIENT) {
            Signature staff = signatureRepository.findActive(
                    signature.snapshotId(), Signature.Role.STAFF).orElse(null);
            if (staff == null) {
                throw new SignatureConflict("必须先由员工签字");
            }
            if (signature.staffSignatureId() == null
                    || !signature.staffSignatureId().equals(staff.id())) {
                throw new SignatureConflict("客户签字必须引用有效的员工签字");
            }
            if (signature.previewSessionId() == null) {
                throw new SignatureConflict("客户签字必须提供阅读回执");
            }
        }

        // 同一角色有效签字唯一（首期不支持 supersedes 替换，重复直接冲突）
        Signature existing = signatureRepository.findActive(signature.snapshotId(), role).orElse(null);
        if (existing != null) {
            throw new SignatureConflict("该角色已签署，不可重复");
        }

        return signatureRepository.save(signature);
    }

    /** 快照/签字不存在（404）。 */
    public static class SignatureNotFound extends RuntimeException {
        public SignatureNotFound(String message) { super(message); }
    }

    /** 无权限（403）。 */
    public static class SignatureForbidden extends RuntimeException {
        public SignatureForbidden(String message) { super(message); }
    }

    /** 业务冲突（409）。 */
    public static class SignatureConflict extends RuntimeException {
        public SignatureConflict(String message) { super(message); }
    }
}
