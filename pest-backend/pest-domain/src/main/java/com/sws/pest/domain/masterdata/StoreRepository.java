package com.sws.pest.domain.masterdata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 门店仓储端口。授权门店列表按 user_scope 过滤后返回。
 */
public interface StoreRepository {

    /**
     * 按数据范围列出授权门店。userId 用于 join user_scope 过滤。
     * keyword 匹配 code 或 name（模糊）；cursor 为分页游标；limit 默认 20。
     */
    Page listByScope(UUID userId, String keyword, String cursor, int limit);

    Optional<Store> findById(UUID storeId);

    /**
     * 按 id 查询门店详情（含 regionId/location/revision/areas/contact）。
     * 不存在返回 null。
     */
    Store findDetailById(UUID storeId);

    /**
     * 分页结果。nextCursor 为 null 表示无更多。
     */
    record Page(List<Store> items, String nextCursor, boolean hasMore) {
    }
}
