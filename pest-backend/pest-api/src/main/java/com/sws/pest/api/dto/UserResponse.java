package com.sws.pest.api.dto;

import java.util.List;

/**
 * 用户响应（对应 openapi User）。
 */
public record UserResponse(
        String id,
        String companyId,
        String employeeCode,
        String name,
        List<String> roles,
        List<String> permissions,
        String teamName,
        List<String> regionIds,
        long permissionVersion) {
}
