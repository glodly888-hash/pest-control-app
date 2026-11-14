package com.sws.pest.application.masterdata;

import com.sws.pest.domain.masterdata.FacilityRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 设施台账查询用例服务（主数据域）。
 */
@Service
public class FacilityQueryService {

    private final FacilityRepository facilityRepository;

    public FacilityQueryService(FacilityRepository facilityRepository) {
        this.facilityRepository = facilityRepository;
    }

    public FacilityRepository.Page listByStore(UUID storeId, UUID typeId, String status,
                                               String cursor, int limit) {
        return facilityRepository.listByStore(storeId, typeId, status, cursor, limit);
    }
}
