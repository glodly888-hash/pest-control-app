package com.sws.pest.domain.reporting;

/**
 * 报告状态。架构设计约束：
 * 正式报告成功生成后才标记服务完成；
 * 更正版发布后旧版标记 SUPERSEDED，一个 service 最多一个 PUBLISHED。
 */
public enum ReportStatus {
    /** 草稿 */
    DRAFT,
    /** 待生成 */
    PENDING,
    /** 生成失败 */
    FAILED,
    /** 已发布（当前有效正式版本） */
    PUBLISHED,
    /** 已被新版替代 */
    SUPERSEDED
}
