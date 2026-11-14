package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.AppConfigResponse;
import com.sws.pest.api.dto.DictionaryItemResponse;
import com.sws.pest.api.dto.DictionarySetResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.platform.PlatformConfigService;
import com.sws.pest.domain.platform.AppConfig;
import com.sws.pest.domain.platform.DictionarySet;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础支撑接口：客户端配置、业务字典。
 * 对应 openapi getAppConfig（GET /app-config）、getDictionaries（GET /dictionaries）。
 */
@RestController
public class AppConfigController {

    private final PlatformConfigService service;

    public AppConfigController(PlatformConfigService service) {
        this.service = service;
    }

    @GetMapping("/app-config")
    public ApiResponse<AppConfigResponse> getAppConfig(HttpServletRequest request) {
        requireLogin(request);
        AppConfig c = service.getAppConfig();
        return ApiResponse.of(new AppConfigResponse(
                c.configVersion(), c.recommendedMinutes(), c.previewMinSeconds(),
                c.maxImageBytes(), c.maxDocumentBytes(), c.maxSubmissionBytes(),
                c.uploadSessionTtlSeconds(), c.offlineGrantHours(), c.maxOfflineTasks(),
                c.supportContact(), c.privacyVersion(), c.termsVersion(), c.templateVersion()));
    }

    @GetMapping("/dictionaries")
    public ApiResponse<DictionarySetResponse> getDictionaries(HttpServletRequest request) {
        requireLogin(request);
        DictionarySet d = service.getDictionaries();
        var items = d.items().stream()
                .map(i -> new DictionaryItemResponse(
                        i.id() != null ? i.id().toString() : null,
                        i.kind(), i.code(), i.name(), i.isActive(), i.revision(), i.formulation()))
                .toList();
        return ApiResponse.of(new DictionarySetResponse(d.version(), items));
    }

    private static void requireLogin(HttpServletRequest request) {
        if (JwtAuthFilter.currentUserId(request) == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
    }
}
