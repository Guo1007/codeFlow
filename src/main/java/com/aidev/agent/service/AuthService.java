package com.aidev.agent.service;

import com.aidev.agent.controller.vo.LoginReqVO;
import com.aidev.agent.controller.vo.LoginRespVO;
import com.aidev.agent.controller.vo.RegisterReqVO;

/**
 * 认证服务：注册 / 登录
 */
public interface AuthService {

    /**
     * 注册新用户并签发 token
     */
    LoginRespVO register(RegisterReqVO reqVO);

    /**
     * 登录并签发 token
     */
    LoginRespVO login(LoginReqVO reqVO);

}