package com.aidev.agent.controller;

import com.aidev.agent.common.ApiResponse;
import com.aidev.agent.controller.vo.LoginReqVO;
import com.aidev.agent.controller.vo.LoginRespVO;
import com.aidev.agent.controller.vo.RegisterReqVO;
import com.aidev.agent.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器（注册 / 登录），放行不拦截。
 */
@Validated
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<LoginRespVO> register(@Valid @RequestBody RegisterReqVO reqVO) {
        return ApiResponse.success(authService.register(reqVO));
    }

    @PostMapping("/login")
    public ApiResponse<LoginRespVO> login(@Valid @RequestBody LoginReqVO reqVO) {
        return ApiResponse.success(authService.login(reqVO));
    }

}