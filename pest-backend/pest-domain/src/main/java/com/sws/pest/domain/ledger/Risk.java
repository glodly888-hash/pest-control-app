package com.sws.pest.domain.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 长期风险台账（risk 表）。
 *
 * content_json 内保存 description / suggestion / photos 等完整当前状态，
 * 这里将其拆为实体字段便于应用层消费；首期简化不做 JCS 规范化。
 */
public final class Risk {

    public enum Status {
        PENDING, IN_PROGRESS, AWAITING_REVIEW, CLOSED
    }

    private final UUID id;
    private final UUID storeId;
    private final UUID typeId;
    private final List<String> pestTypeIds;
    private final UUID areaId;
    private final String floor;
    private final String position;
    private final String description;
    private final String suggestion;
    private final String responsibleParty;
    private final String contactName;
    private final UUID ownerId;
    private final String dueDate;      // YYYY-MM-DD
    private final Status status;
    private final long revision;
    private final Instant discoveredAt;
    private final Instant closedAt;
    private final List<String> photos; // attachment UUID 列表

    public Risk(UUID id, UUID storeId, UUID typeId, List<String> pestTypeIds, UUID areaId,
                String floor, String position, String description, String suggestion,
                String responsibleParty, String contactName, UUID ownerId, String dueDate,
                Status status, long revision, Instant discoveredAt, Instant closedAt,
                List<String> photos) {
        this.id = id;
        this.storeId = storeId;
        this.typeId = typeId;
        this.pestTypeIds = pestTypeIds;
        this.areaId = areaId;
        this.floor = floor;
        this.position = position;
        this.description = description;
        this.suggestion = suggestion;
        this.responsibleParty = responsibleParty;
        this.contactName = contactName;
        this.ownerId = ownerId;
        this.dueDate = dueDate;
        this.status = status;
        this.revision = revision;
        this.discoveredAt = discoveredAt;
        this.closedAt = closedAt;
        this.photos = photos;
    }

    public UUID id() { return id; }
    public UUID storeId() { return storeId; }
    public UUID typeId() { return typeId; }
    public List<String> pestTypeIds() { return pestTypeIds; }
    public UUID areaId() { return areaId; }
    public String floor() { return floor; }
    public String position() { return position; }
    public String description() { return description; }
    public String suggestion() { return suggestion; }
    public String responsibleParty() { return responsibleParty; }
    public String contactName() { return contactName; }
    public UUID ownerId() { return ownerId; }
    public String dueDate() { return dueDate; }
    public Status status() { return status; }
    public long revision() { return revision; }
    public Instant discoveredAt() { return discoveredAt; }
    public Instant closedAt() { return closedAt; }
    public List<String> photos() { return photos; }
}
