package com.sws.pest.domain.confirmation;

import java.util.UUID;

/**
 * 客户缺席/拒签记录持久化端口（confirmation_exception 表）。
 */
public interface ConfirmationExceptionRepository {

    /**
     * 保存缺席/拒签记录，并把 service.phase 置 AWAITING_CONFIRMATION。
     * 返回带 receivedAt 的记录。
     */
    ConfirmationException save(ConfirmationException exception);

    /** 校验服务是否存在且已冻结（可登记缺席/拒签的状态）。 */
    boolean serviceFrozen(UUID serviceId);

    /** 服务不存在。 */
    class ServiceNotFound extends RuntimeException {
        public ServiceNotFound(String message) { super(message); }
    }

    /** 服务状态不允许登记（如已发布）。 */
    class ServiceConflict extends RuntimeException {
        public ServiceConflict(String message) { super(message); }
    }
}
