package com.sws.pest.api.dto;

import java.util.UUID;

/**
 * 统一成功响应包装：{ data, requestId }。
 */
public record ApiResponse<T>(T data, String requestId) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, UUID.randomUUID().toString());
    }
}
