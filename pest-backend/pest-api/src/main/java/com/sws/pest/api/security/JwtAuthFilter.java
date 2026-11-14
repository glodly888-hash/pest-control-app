package com.sws.pest.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 鉴权过滤器：解析 Authorization: Bearer <token>，把当前用户放进请求属性。
 * 登录、刷新等 PUBLIC 接口由 Controller 声明跳过；这里只做「有 token 就解析」。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                JwtService.Claims claims = jwtService.verify(token);
                request.setAttribute("currentUserId", claims.userId());
                request.setAttribute("currentSessionId", claims.sessionId());
                request.setAttribute("permissionVersion", claims.permissionVersion());
            } catch (JwtService.TokenException e) {
                // token 无效：交给后续 Controller/拦截器处理，这里不直接中断
                request.setAttribute("authError", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 从请求取当前用户（未登录返回 null）。 */
    public static UUID currentUserId(HttpServletRequest request) {
        return (UUID) request.getAttribute("currentUserId");
    }
}
