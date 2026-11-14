package com.sws.pest.domain.workorder;

/**
 * 服务业务状态。架构设计约束：数据同步状态与服务业务状态分开维护，
 * 不能以「已同步」代替「服务已完成」。
 */
public enum ServiceStatus {
    /** 待执行 */
    PENDING,
    /** 进行中 */
    IN_PROGRESS,
    /** 待确认 */
    AWAITING_CONFIRMATION,
    /** 已完成 */
    COMPLETED,
    /** 已取消 */
    CANCELLED
}
