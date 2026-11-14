package com.sws.pest.application.confirmation;

import com.sws.pest.domain.confirmation.ConfirmationException;
import com.sws.pest.domain.confirmation.ConfirmationExceptionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * 客户缺席/拒签登记用例服务（对齐 openapi recordRefusal）。
 *
 * 规则：登记不能代替客户签名，不能触发正式报告；任务置待确认（AWAITING_CONFIRMATION）。
 */
@Service
public class RefusalService {

    private final ConfirmationExceptionRepository repository;

    public RefusalService(ConfirmationExceptionRepository repository) {
        this.repository = repository;
    }

    /**
     * 登记缺席或拒签。reasonType=ABSENT/REFUSED。
     */
    public ConfirmationException recordRefusal(
            UUID id, UUID serviceId, UUID snapshotId, String reasonType,
            String reason, UUID actorId, Instant recordedAt) {

        if (!"ABSENT".equals(reasonType) && !"REFUSED".equals(reasonType)) {
            throw new IllegalArgumentException("reasonType 必须是 ABSENT 或 REFUSED");
        }

        if (!repository.serviceFrozen(serviceId)) {
            throw new ConfirmationExceptionRepository.ServiceConflict(
                    "服务尚未冻结或已进入后续阶段，不能登记缺席/拒签");
        }

        ConfirmationException exception = new ConfirmationException(
                id, serviceId, snapshotId, reasonType, reason, actorId, recordedAt, Instant.now());

        return repository.save(exception);
    }
}
