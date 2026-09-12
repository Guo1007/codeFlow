package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 登录请求 VO
 */
@Data
public class LoginReqVO {

    @NotEmpty(message = "用户名不能为空")
    private String username;

    @NotEmpty(message = "密码不能为空")
    private String password;

}