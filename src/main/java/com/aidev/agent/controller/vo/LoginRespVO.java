package com.aidev.agent.controller.vo;

import lombok.Data;

/**
 * 登录/注册成功返回 VO：token + 用户信息
 */
@Data
public class LoginRespVO {

    /**
     * JWT token（登录后前端请求头携带）
     */
    private String token;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

}