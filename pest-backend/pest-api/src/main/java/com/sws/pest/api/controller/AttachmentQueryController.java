package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.AttachmentResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.media.AttachmentService;
import com.sws.pest.domain.media.Attachment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 附件查询/下载接口（对应 openapi getAttachment、downloadAttachment）。
 * 独立于上传会话，路径前缀 /attachments。
 */
@RestController
@RequestMapping("/attachments")
public class AttachmentQueryController {

    private final AttachmentService attachmentService;

    public AttachmentQueryController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** getAttachment：查询附件状态。 */
    @GetMapping("/{attachmentId}")
    public ApiResponse<AttachmentResponse> getAttachment(
            @PathVariable String attachmentId, HttpServletRequest request) {
        requireUser(request);
        UUID id = UUID.fromString(attachmentId);
        Attachment attachment = attachmentService.getAttachment(id);
        return ApiResponse.of(toResponse(attachment));
    }

    /** downloadAttachment：读取受保护附件二进制。 */
    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> downloadAttachment(
            @PathVariable String attachmentId, HttpServletRequest request) {
        requireUser(request);
        UUID id = UUID.fromString(attachmentId);
        byte[] content = attachmentService.download(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
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
