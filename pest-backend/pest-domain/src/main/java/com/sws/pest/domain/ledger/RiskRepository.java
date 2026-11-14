package com.sws.pest.domain.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 风险台账读取端口。
 */
public interface RiskRepository {

    /** 门店风险台账分页；status 可空表示不过滤。 */
    List<Risk> listByStore(UUID storeId, Risk.Status status, String cursor, int limit);

    /** 判断是否有更多记录（用于 hasMore，取 limit+1 后的溢出）。 */
    boolean hasMore(UUID storeId, Risk.Status status, String cursor, int limit);

    Optional<Risk> findById(UUID riskId);

    /** 风险是否存在。 */
    boolean exists(UUID riskId);

    /** 读当前 revision（乐观锁基准）。 */
    long currentRevision(UUID riskId);

    /** 门店是否存在（授权校验用）。 */
    boolean storeExists(UUID storeId);

    class RiskNotFound extends RuntimeException {
        public RiskNotFound(String msg) { super(msg); }
    }
}
