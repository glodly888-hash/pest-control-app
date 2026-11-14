package com.sws.pest.domain.workorder;

/**
 * 服务作业阶段（service.phase），与任务业务状态（ServiceStatus）独立。
 *
 * 状态机：EDITABLE → FROZEN → AWAITING_CONFIRMATION → PUBLISHING → PUBLISHED
 * - EDITABLE：草稿可编辑；
 * - FROZEN：已冻结待签，草稿禁止修改；
 * - AWAITING_CONFIRMATION：客户缺席/拒签，任务置待确认；
 * - PUBLISHING：报告生成排队中；
 * - PUBLISHED：正式报告已发布。
 */
public enum ServicePhase {
    EDITABLE,
    FROZEN,
    AWAITING_CONFIRMATION,
    PUBLISHING,
    PUBLISHED
}
