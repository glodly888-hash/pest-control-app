package com.sws.pest.domain.platform;

/**
 * 幂等记录端口。三层幂等中的 HTTP 层：
 * actor＋operationId＋Idempotency-Key 摘要 → 同一业务结果；
 * 同键不同请求返回 409。幂等记录与业务变更同一事务提交。
 */
public interface IdempotencyStore {

    /**
     * 尝试登记幂等键。首次登记返回 null 表示可执行；
     * 已存在且请求摘要一致返回原结果标识；摘要不一致抛冲突。
     */
    String putIfAbsent(String actor, String operationId, String idempotencyKey, String requestHash);
}
