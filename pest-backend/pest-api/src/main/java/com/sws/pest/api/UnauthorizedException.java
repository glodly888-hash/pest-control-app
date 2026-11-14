package com.sws.pest.api;

/**
 * 未授权/未登录异常。
 *
 * 与 {@link com.sws.pest.application.identity.AuthenticateService.AuthenticationException}
 * （登录凭据错误，401）区分：本异常表示「请求缺少有效访问令牌」，同样映射 401，
 * 但语义更明确，避免被 {@code IllegalArgumentException} 处理器误判为 400。
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
