package com.sws.pest.domain.workorder;

/**
 * 虫情来源。三类来源分开统计，绝不默认相加。
 * 架构设计约束：客户反馈、现场发现、设施捕获分别保存，
 * 每条统计事实保留 source，按来源和虫种分组。
 */
public enum PestSource {
    /** 客户反馈 */
    CUSTOMER,
    /** 现场发现 */
    ON_SITE,
    /** 设施捕获 */
    FACILITY
}
