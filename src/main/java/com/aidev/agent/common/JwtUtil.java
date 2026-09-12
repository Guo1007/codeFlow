package com.aidev.agent.common;

import com.aidev.agent.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT 工具：签发与解析 token。
 * <p>
 * 载荷含 userId / username / nickname，签发与校验共用同一密钥。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    /**
     * 根据用户信息生成 token
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param nickname 昵称
     * @return JWT 字符串
     */
    public String generate(Long userId, String username, String nickname) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpireHours() * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("username", username)
                .claim("nickname", nickname)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey())
                .compact();
    }

    /**
     * 解析 token，返回载荷；无效/过期时返回 null
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
    }

}