package com.sws.pest.domain.workorder;

/**
 * 现场作业草稿内容（ServiceContent）。
 *
 * 架构设计约束：
 * - 未冻结时可完整替换，revision 控制并发；
 * - 冻结后成为不可变快照（SnapshotPayload），永不回写；
 * - 签到、各模块通过完整 PUT 保存，不能把局部表单当 PATCH。
 *
 * 首期实现：content 以 JSON 字符串透传存储（不逐字段建模校验），
 * 与契约 V01-V16 的逐字段校验解耦，校验逻辑后续在 validate 阶段补齐。
 */
public final class ServiceContent {

    private final String contentJson;

    public ServiceContent(String contentJson) {
        this.contentJson = contentJson;
    }

    /** 完整 ServiceContent 的 JSON 字符串，可能为空对象 "{}" 表示尚未填写。 */
    public String contentJson() {
        return contentJson;
    }
}
