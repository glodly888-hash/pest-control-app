package com.sws.pest.domain.confirmation;

import java.time.Instant;
import java.util.UUID;

/**
 * 签署凭证（service_signature 表）。
 *
 * 同一快照必须先员工（STAFF）后客户（CLIENT）；员工签署人必须等于登录员工；
 * 客户签署必须引用有效的员工签字和阅读回执（preview_session）。
 * 签字图片应已 VERIFIED（attachment.status=READY）。
 *
 * 首期简化：role 在 API 用 STAFF/CUSTOMER，落库映射为 STAFF/CLIENT。
 */
public final class Signature {

    public enum Role {
        STAFF,
        CLIENT
    }

    private final UUID id;
    private final UUID snapshotId;
    private final Role role;
    private final String signerName;
    private final UUID staffUserId;
    private final UUID staffSignatureId;
    private final UUID previewSessionId;
    private final UUID attachmentId;
    private final String contentHash;
    private final String attestationHash;
    private final Instant signedAt;
    private final Integer rating;
    private final String comment;
    private final UUID supersedesId;
    private final String replacementReason;
    private final Instant receivedAt;

    public Signature(UUID id, UUID snapshotId, Role role, String signerName, UUID staffUserId,
                     UUID staffSignatureId, UUID previewSessionId, UUID attachmentId,
                     String contentHash, String attestationHash, Instant signedAt,
                     Integer rating, String comment, UUID supersedesId, String replacementReason,
                     Instant receivedAt) {
        this.id = id;
        this.snapshotId = snapshotId;
        this.role = role;
        this.signerName = signerName;
        this.staffUserId = staffUserId;
        this.staffSignatureId = staffSignatureId;
        this.previewSessionId = previewSessionId;
        this.attachmentId = attachmentId;
        this.contentHash = contentHash;
        this.attestationHash = attestationHash;
        this.signedAt = signedAt;
        this.rating = rating;
        this.comment = comment;
        this.supersedesId = supersedesId;
        this.replacementReason = replacementReason;
        this.receivedAt = receivedAt;
    }

    public UUID id() { return id; }
    public UUID snapshotId() { return snapshotId; }
    public Role role() { return role; }
    public String signerName() { return signerName; }
    public UUID staffUserId() { return staffUserId; }
    public UUID staffSignatureId() { return staffSignatureId; }
    public UUID previewSessionId() { return previewSessionId; }
    public UUID attachmentId() { return attachmentId; }
    public String contentHash() { return contentHash; }
    public String attestationHash() { return attestationHash; }
    public Instant signedAt() { return signedAt; }
    public Integer rating() { return rating; }
    public String comment() { return comment; }
    public UUID supersedesId() { return supersedesId; }
    public String replacementReason() { return replacementReason; }
    public Instant receivedAt() { return receivedAt; }
}
