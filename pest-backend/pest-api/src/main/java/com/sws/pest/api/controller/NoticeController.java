package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.NoticeListResponse;
import com.sws.pest.api.dto.NoticeResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.platform.PlatformConfigService;
import com.sws.pest.domain.platform.Notice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务须知接口。对应 openapi listNotices（GET /service-notices）。
 *
 * 须知数据量小，全量返回（游标参数保留契约兼容，首期不做服务端分页）。
 */
@RestController
@RequestMapping("/service-notices")
public class NoticeController {

    private final PlatformConfigService service;

    public NoticeController(PlatformConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<NoticeListResponse> listNotices(
            HttpServletRequest request,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        requireLogin(request);
        List<Notice> notices = service.listNotices();
        List<NoticeResponse> items = notices.stream()
                .map(n -> new NoticeResponse(
                        n.id().toString(), n.version(), n.title(), n.content(),
                        n.effectiveAt().toString()))
                .toList();
        return ApiResponse.of(new NoticeListResponse(items, "", false));
    }

    private static void requireLogin(HttpServletRequest request) {
        if (JwtAuthFilter.currentUserId(request) == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
    }
}
