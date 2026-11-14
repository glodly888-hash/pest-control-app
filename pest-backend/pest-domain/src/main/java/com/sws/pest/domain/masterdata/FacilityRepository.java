package com.sws.pest.domain.masterdata;

import java.util.List;
import java.util.UUID;

/**
 * 设施台账仓储端口。按门店过滤，游标分页。
 */
public interface FacilityRepository {

    /**
     * 列出某门店的设施台账。
     *
     * @param storeId 门店
     * @param typeId  设施类型过滤（字典 UUID），null 表示全部
     * @param status  状态过滤（ACTIVE/INACTIVE/REMOVED），null 表示全部
     * @param cursor  分页游标
     * @param limit   每页条数
     */
    Page listByStore(UUID storeId, UUID typeId, String status, String cursor, int limit);

    record Page(List<Facility> items, String nextCursor, boolean hasMore) {
    }
}
