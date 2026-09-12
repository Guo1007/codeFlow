package com.aidev.agent.config;

import com.aidev.agent.common.ApiResponse;
import com.aidev.agent.common.JwtUtil;
import com.aidev.agent.common.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import tools.jackson.databind.ObjectMapper;

/**
 * 登录鉴权拦截器。
 * <p>
 * 放行 /auth/register、/auth/login；其余接口要求请求头携带
 * "Authorization: Bearer <token>"，解析成功后写入 {@link UserContext} 供业务层取当前用户，
 * 请求结束后清理。未登录/无效/过期 token 直接返回 401 JSON。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // CORS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = (header != null && header.startsWith(BEARER_PREFIX))
                ? header.substring(BEARER_PREFIX.length()) : null;
        Claims claims = (token != null && !token.isBlank()) ? jwtUtil.parse(token) : null;
        if (claims == null) {
            writeUnauthorized(response);
            return false;
        }
        Number userId = claims.get("userId", Number.class);
        if (userId == null) {
            writeUnauthorized(response);
            return false;
        }
        UserContext.set(new UserContext.LoginUser(
                userId.longValue(),
                claims.get("username", String.class),
                claims.get("nickname", String.class)));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(401, "未登录或登录已过期")));
    }

}