package com.sws.pest.domain.workorder;

/**
 * 捕获/点检数量与检查状态的四态建模。
 *
 * 架构设计约束（不得混淆）：
 * - EXACT：明确数量（0 ~ 1,000,000,000）
 * - UNKNOWN：数量不明，不存数量
 * - NOT_CHECKED：未检查，绝不自动补 0
 * - UNCONFIRMABLE：无法确认，独立于 NOT_CHECKED
 *
 * 报告统计中「未检查」「数量不明」单独展示，不纳入捕获数量求和。
 */
public enum InspectionStatus {
    EXACT,
    UNKNOWN,
    NOT_CHECKED,
    UNCONFIRMABLE
}
