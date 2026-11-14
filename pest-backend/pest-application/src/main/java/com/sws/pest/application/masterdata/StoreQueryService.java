package com.sws.pest.application.masterdata;

import com.sws.pest.domain.masterdata.Store;
import com.sws.pest.domain.masterdata.StoreRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 门店查询用例服务（主数据域）。
 */
@Service
public class StoreQueryService {

    private final StoreRepository storeRepository;

    public StoreQueryService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public StoreRepository.Page listByScope(UUID userId, String keyword, String cursor, int limit) {
        return storeRepository.listByScope(userId, keyword, cursor, limit);
    }

    public Store findById(UUID storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("store not found"));
    }

    /**
     * 门店详情，不存在返回 null。
     */
    public Store getDetail(UUID storeId) {
        return storeRepository.findDetailById(storeId);
    }
}
