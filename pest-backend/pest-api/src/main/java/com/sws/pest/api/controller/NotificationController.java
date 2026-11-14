package com.sws.pest.api.controller;

import com.sws.pest.api.NotFoundException;
import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.AckResponse;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.NotificationListResponse;
import com.sws.pest.api.dto.NotificationResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.platform.NotificationService;
import com.sws.pest.domain.platform.NotificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 员工通知接口。对应 openapi listNotifications（GET /notifications）、
 * readNotification（PUT /notifications/{notificationId}/read-receipt）。
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> listNotifications(
            HttpServletRequest request,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        UUID userId = requireLogin(request);
        NotificationRepository.Page page = service.list(userId, cursor, limit);
        List<NotificationResponse> items = page.items().stream()
                .map(n -> new NotificationResponse(
                        n.id().toString(), n.type(), n.title(), n.body(),
                        n.resourceType(), n.resourceId() != null ? n.resourceId().toString() : null,
                        n.isRead(), n.createdAt().toString()))
                .toList();
        return ApiResponse.of(new NotificationListResponse(items, page.nextCursor(), page.hasMore()));
    }

    @PutMapping("/{notificationId}/read-receipt")
    public ApiResponse<AckResponse> readNotification(
            HttpServletRequest request,
            @PathVariable UUID notificationId,
            @RequestBody(required = false) Map<String, Object> body) {
        UUID userId = requireLogin(request);
        boolean ok = service.markRead(userId, notificationId);
        if (!ok) {
            throw new NotFoundException("通知不存在");
        }
        return ApiResponse.of(new AckResponse(true, Instant.now().toString()));
    }

    private static UUID requireLogin(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        return userId;
    }
}
