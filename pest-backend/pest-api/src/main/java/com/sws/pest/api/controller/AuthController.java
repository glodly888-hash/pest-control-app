package com.sws.pest.api.controller;

import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.LoginRequest;
import com.sws.pest.api.dto.SessionResponse;
import com.sws.pest.api.dto.UserResponse;
import com.sws.pest.api.security.JwtService;
import com.sws.pest.application.identity.AuthenticateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账号接口（登录）。对应 openapi POST /auth/sessions（operationId=login）。
 */
@RestController
@RequestMapping("/auth/sessions")
public class AuthController {

    private final AuthenticateService authenticateService;
    private final JwtService jwtService;

    public AuthController(AuthenticateService authenticateService, JwtService jwtService) {
        this.authenticateService = authenticateService;
        this.jwtService = jwtService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SessionResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthenticateService.LoginResult result = authenticateService.login(
                request.companyCode(), request.account(), request.password(), request.deviceId());

        String accessToken = jwtService.issue(
                result.user().id(), result.sessionId(),
                result.user().permissionVersion(), result.accessExpiresAt());

        UserResponse user = new UserResponse(
                result.user().id().toString(),
                result.user().companyId().toString(),
                result.user().employeeCode(),
                result.user().name(),
                List.of("EMPLOYEE"),       // 首期角色固定 EMPLOYEE，后续从 user_role join
                List.of(),                  // 权限后续从 role_permission join
                null,                       // teamName 后续从 org_unit join
                List.of(),                  // regionIds 后续从 user_scope 汇总
                result.user().permissionVersion());

        SessionResponse session = new SessionResponse(
                accessToken,
                result.refreshToken(),
                "Bearer",
                result.accessExpiresAt().toString(),
                result.refreshExpiresAt().toString(),
                result.sessionId().toString(),
                user);

        return ApiResponse.of(session);
    }
}
