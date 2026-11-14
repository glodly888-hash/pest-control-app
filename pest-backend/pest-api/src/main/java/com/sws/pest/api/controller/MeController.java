package com.sws.pest.api.controller;

import com.sws.pest.api.UnauthorizedException;
import com.sws.pest.api.dto.ApiResponse;
import com.sws.pest.api.dto.UserResponse;
import com.sws.pest.api.security.JwtAuthFilter;
import com.sws.pest.application.identity.UserQueryService;
import com.sws.pest.domain.identity.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 当前员工接口。对应 openapi GET /me（operationId=getMe）。
 */
@RestController
@RequestMapping("/me")
public class MeController {

    private final UserQueryService userQueryService;

    public MeController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @GetMapping
    public ApiResponse<UserResponse> getMe(HttpServletRequest request) {
        UUID userId = JwtAuthFilter.currentUserId(request);
        if (userId == null) {
            throw new UnauthorizedException("未登录或 token 无效");
        }

        AppUser user = userQueryService.findById(userId);

        UserResponse data = new UserResponse(
                user.id().toString(),
                user.companyId().toString(),
                user.employeeCode(),
                user.name(),
                List.of("EMPLOYEE"),       // 首期角色固定 EMPLOYEE，后续从 user_role join
                List.of(),                  // 权限后续从 role_permission join
                null,                       // teamName 后续从 org_unit join
                List.of(),                  // regionIds 后续从 user_scope 汇总
                user.permissionVersion());

        return ApiResponse.of(data);
    }
}
