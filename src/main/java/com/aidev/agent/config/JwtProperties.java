package com.aidev.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 认证配置项（jwt.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 签名密钥（至少 32 字节，用于 HS256）
     */
    private String secret;

    /**
     * token 有效期（小时），默认 24 小时
     */
    private long expireHours = 24;

}