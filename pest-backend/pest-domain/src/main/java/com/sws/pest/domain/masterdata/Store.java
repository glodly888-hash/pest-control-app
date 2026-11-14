package com.sws.pest.domain.masterdata;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 门店（store）。授权门店列表与详情的展示对象。
 * 数据范围由 user_scope 决定，查询层已按 scope 过滤。
 *
 * 详情字段（regionId/location/revision/areas/contact）仅在 getStore 时填充，
 * 列表查询时可为 null / 空。
 */
public final class Store {

    private final UUID id;
    private final UUID customerId;
    private final UUID brandId;
    private final String code;
    private final String name;
    private final String address;
    private final String timezone;
    private final String brandName;
    // 详情扩展字段
    private final UUID regionId;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final long revision;
    private final String contactName;
    private final String contactPhone;
    private final List<Area> areas;

    public Store(UUID id, UUID customerId, UUID brandId, String code, String name,
                 String address, String timezone, String brandName,
                 UUID regionId, BigDecimal latitude, BigDecimal longitude, long revision,
                 String contactName, String contactPhone, List<Area> areas) {
        this.id = id;
        this.customerId = customerId;
        this.brandId = brandId;
        this.code = code;
        this.name = name;
        this.address = address;
        this.timezone = timezone;
        this.brandName = brandName;
        this.regionId = regionId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.revision = revision;
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.areas = areas;
    }

    public UUID id() { return id; }
    public UUID customerId() { return customerId; }
    public UUID brandId() { return brandId; }
    public String code() { return code; }
    public String name() { return name; }
    public String address() { return address; }
    public String timezone() { return timezone; }
    public String brandName() { return brandName; }
    public UUID regionId() { return regionId; }
    public BigDecimal latitude() { return latitude; }
    public BigDecimal longitude() { return longitude; }
    public long revision() { return revision; }
    public String contactName() { return contactName; }
    public String contactPhone() { return contactPhone; }
    public List<Area> areas() { return areas; }

    /**
     * 门店分区（store_area）。
     */
    public record Area(UUID id, String name, String floor) {
    }
}
