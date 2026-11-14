package com.sws.pest.domain.ledger;

/**
 * 风险整改状态机：待整改 → 整改中 → 待复核 → 已关闭。
 * 架构设计约束：服务完成不等于风险关闭；
 * 已关闭风险再次发生时可重开并保留历史。
 */
public enum RiskStatus {
    /** 待整改 */
    PENDING,
    /** 整改中 */
    IN_PROGRESS,
    /** 待复核 */
    AWAITING_REVIEW,
    /** 已关闭 */
    CLOSED
}
