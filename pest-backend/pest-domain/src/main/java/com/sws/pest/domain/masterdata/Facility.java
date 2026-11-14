package com.sws.pest.domain.masterdata;

import java.time.Instant;
import java.util.UUID;

/**
 * 设施台账（facility）。门店内可巡检的设备/点位。
 */
public final class Facility {

    private final UUID id;
    private final UUID storeId;
    private final String code;
    private final UUID typeId;
    private final String typeName;
    private final UUID areaId;
    private final String floor;
    private final String position;
    private final String status;
    private final long revision;
    private final Instant updatedAt;

    public Facility(UUID id, UUID storeId, String code, UUID typeId, String typeName,
                    UUID areaId, String floor, String position, String status,
                    long revision, Instant updatedAt) {
        this.id = id;
        this.storeId = storeId;
        this.code = code;
        this.typeId = typeId;
        this.typeName = typeName;
        this.areaId = areaId;
        this.floor = floor;
        this.position = position;
        this.status = status;
        this.revision = revision;
        this.updatedAt = updatedAt;
    }

    public UUID id() { return id; }
    public UUID storeId() { return storeId; }
    public String code() { return code; }
    public UUID typeId() { return typeId; }
    public String typeName() { return typeName; }
    public UUID areaId() { return areaId; }
    public String floor() { return floor; }
    public String position() { return position; }
    public String status() { return status; }
    public long revision() { return revision; }
    public Instant updatedAt() { return updatedAt; }
}
