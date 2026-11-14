package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.RefreshInput;
import com.sws.pest.api.dto.SessionResponse;
import com.sws.pest.api.dto.UserResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.api.security.JwtService;
import com.sws.pest.application.identity.AuthenticateService;
import com.sws.pest.domain.identity.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 会话令牌接口。对应 openapi logout（DELETE /auth/sessions/current）与
 * refresh（POST /auth/refreshes）。
 */
@RestController
@RequestMapping("/auth")
public class AuthTokenController {

    private final AuthenticateService authenticateService;
    private final JwtService jwtService;

    public AuthTokenController(AuthenticateService authenticateService, JwtService jwtService) {
        this.authenticateService = authenticateService;
        this.jwtService = jwtService;
    }

    /**
     * POST /auth/refreshes —— 刷新令牌（轮换 refresh，签发新 access）。
     */
    @PostMapping("/refreshes")
    public ApiResponse<SessionResponse> refresh(@Valid @RequestBody RefreshInput input) {
        AuthenticateService.RefreshResult result = authenticateService.refresh(
                input.refreshToken(), input.deviceId());

        String accessToken = jwtService.issue(
                result.user().id(), result.sessionId(),
                result.user().permissionVersion(), result.accessExpiresAt());

        SessionResponse session = new SessionResponse(
                accessToken,
                result.refreshToken(),
                "Bearer",
                result.accessExpiresAt().toString(),
                result.refreshExpiresAt().toString(),
                result.sessionId().toString(),
                toUser(result.user()));

        return ApiResponse.of(session);
    }

    /**
     * DELETE /auth/sessions/current —— 退出当前设备会话。
     */
    @DeleteMapping("/sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        UUID sessionId = currentSessionId(request);
        if (sessionId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }
        authenticateService.logout(sessionId);
    }

    private static UUID currentSessionId(HttpServletRequest request) {
        return (UUID) request.getAttribute("currentSessionId");
    }

    private static UserResponse toUser(AppUser user) {
        return new UserResponse(
                user.id().toString(),
                user.companyId().toString(),
                user.employeeCode(),
                user.name(),
                List.of("EMPLOYEE"),
                List.of(),
                null,
                List.of(),
                user.permissionVersion());
    }
}
