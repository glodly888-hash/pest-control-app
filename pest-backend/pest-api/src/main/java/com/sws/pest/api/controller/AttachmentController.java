package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.AckResponse;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.AttachmentResponse;
import com.sws.pest.api.dto.UploadCompleteInput;
import com.sws.pest.api.dto.UploadInput;
import com.sws.pest.api.dto.UploadSessionResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.media.AttachmentService;
import com.sws.pest.domain.media.Attachment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 附件上传会话接口。
 * 对应 openapi：createUpload、uploadBytes、verifyUpload。
 */
@RestController
@RequestMapping("/attachment-upload-sessions")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** createUpload：创建或恢复附件上传会话。 */
    @PostMapping
    public ResponseEntity<ApiResponse<UploadSessionResponse>> createUpload(
            @RequestBody UploadInput input, HttpServletRequest request) {
        UUID ownerId = requireUser(request);

        UUID attachmentId = UUID.fromString(input.attachmentId());
        UUID contextId = UUID.fromString(input.contextId());
        // serviceId 可选，storeId 从 context 反查（repository 内处理）
        UUID storeId = null;

        AttachmentService.CreateUploadResult result = attachmentService.createUpload(
                attachmentId, contextId, storeId, ownerId,
                input.purpose(), input.fileName(), input.mediaType(),
                input.sizeBytes(), input.sha256());

        UploadSessionResponse data = new UploadSessionResponse(
                result.sessionId().toString(),
                result.attachmentId().toString(),
                result.status(),
                "/attachment-upload-sessions/" + result.sessionId() + "/content",
                result.expiresAt().toString(),
                result.maxBytes(),
                result.alreadyVerified());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(data));
    }

    /** uploadBytes：上传完整附件二进制（application/octet-stream）。 */
    @PutMapping("/{uploadId}/content")
    public ResponseEntity<ApiResponse<AckResponse>> uploadBytes(
            @PathVariable String uploadId,
            @RequestBody byte[] content,
            HttpServletRequest request) {
        UUID ownerId = requireUser(request);
        UUID uploadUuid = UUID.fromString(uploadId);

        long stored = attachmentService.uploadBytes(uploadUuid, ownerId, content);

        AckResponse ack = new AckResponse(true, Instant.now().toString());
        return ResponseEntity.ok(ApiResponse.of(ack));
    }

    /** verifyUpload：校验附件上传完成。 */
    @PostMapping("/{uploadId}/completions")
    public ResponseEntity<ApiResponse<AttachmentResponse>> verifyUpload(
            @PathVariable String uploadId,
            @RequestBody UploadCompleteInput input,
            HttpServletRequest request) {
        requireUser(request);
        UUID uploadUuid = UUID.fromString(uploadId);

        Attachment attachment = attachmentService.verifyUpload(
                uploadUuid, input.sha256(), input.sizeBytes());

        return ResponseEntity.ok(ApiResponse.of(toResponse(attachment)));
    }

    private static UUID requireUser(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        return userId;
    }

    private static AttachmentResponse toResponse(Attachment a) {
        String status = switch (a.status()) {
            case "READY" -> "VERIFIED";
            case "FAILED" -> "REJECTED";
            default -> "WAITING";
        };
        return new AttachmentResponse(
                a.id().toString(),
                a.sha256(),
                a.mimeType(),
                a.byteSize(),
                status,
                a.purpose(),
                a.createdAt().toString());
    }
}
