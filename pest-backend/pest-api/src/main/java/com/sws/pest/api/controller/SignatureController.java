package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.SignatureInput;
import com.sws.pest.api.dto.SignatureResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.confirmation.SignatureService;
import com.sws.pest.domain.confirmation.Signature;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * 签字接口（对应 openapi createSignature）。
 *
 * POST /snapshots/{snapshotId}/signatures —— 保存员工或客户签字。
 * 规则：先员工后客户；员工=登录人；客户引用有效员工签字+阅读回执。
 */
@RestController
@RequestMapping("/snapshots/{snapshotId}/signatures")
public class SignatureController {

    private final SignatureService signatureService;

    public SignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SignatureResponse>> sign(
            HttpServletRequest request,
            @PathVariable UUID snapshotId,
            @RequestBody SignatureInput input) {
        UUID actorId = requireLogin(request);

        Signature.Role role = parseRole(input.role());

        // 附件 ID 从 signatureImage.attachmentId 提取
        UUID attachmentId = null;
        JsonNode image = input.signatureImage();
        if (image != null) {
            JsonNode att = image.get("attachmentId");
            if (att != null && att.isTextual()) {
                attachmentId = UUID.fromString(att.asText());
            }
        }
        if (attachmentId == null) {
            throw new IllegalArgumentException("缺少签字图片 attachmentId");
        }

        UUID id = input.id() != null ? UUID.fromString(input.id()) : UUID.randomUUID();

        Signature signature = new Signature(
                id,
                snapshotId,
                role,
                input.signerName(),
                input.staffUserId() != null ? UUID.fromString(input.staffUserId()) : null,
                input.staffSignatureId() != null ? UUID.fromString(input.staffSignatureId()) : null,
                input.previewSessionId() != null ? UUID.fromString(input.previewSessionId()) : null,
                attachmentId,
                input.contentHash(),
                input.attestationHash(),
                parseInstant(input.signedAt()),
                input.rating(),
                input.comment(),
                input.supersedesSignatureId() != null ? UUID.fromString(input.supersedesSignatureId()) : null,
                input.replacementReason(),
                null);

        Signature saved = signatureService.sign(signature, actorId);

        return ResponseEntity.status(201).body(ApiResponse.of(toResponse(saved)));
    }

    private static SignatureResponse toResponse(Signature s) {
        return new SignatureResponse(
                new SignatureResponse.SignatureEcho(
                        s.id().toString(),
                        s.role() == Signature.Role.STAFF ? "STAFF" : "CUSTOMER",
                        s.snapshotId().toString(),
                        s.contentHash(),
                        s.signerName(),
                        s.staffUserId() != null ? s.staffUserId().toString() : null,
                        s.staffSignatureId() != null ? s.staffSignatureId().toString() : null,
                        s.previewSessionId() != null ? s.previewSessionId().toString() : null,
                        s.signedAt().toString(),
                        s.rating(),
                        s.comment()),
                true,
                s.receivedAt() != null ? s.receivedAt().toString() : Instant.now().toString());
    }

    private static Signature.Role parseRole(String role) {
        if ("STAFF".equals(role)) {
            return Signature.Role.STAFF;
        }
        if ("CUSTOMER".equals(role)) {
            return Signature.Role.CLIENT;
        }
        throw new IllegalArgumentException("role 必须为 STAFF 或 CUSTOMER");
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("signedAt 时间格式错误");
        }
    }

    private static UUID requireLogin(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        return userId;
    }
}
