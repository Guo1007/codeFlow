package com.aidev.agent.service.impl;

import com.aidev.agent.common.JwtUtil;
import com.aidev.agent.common.ServiceException;
import com.aidev.agent.controller.vo.LoginReqVO;
import com.aidev.agent.controller.vo.LoginRespVO;
import com.aidev.agent.controller.vo.RegisterReqVO;
import com.aidev.agent.dal.entity.SysUser;
import com.aidev.agent.dal.mapper.SysUserMapper;
import com.aidev.agent.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 认证服务实现类
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;

    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginRespVO register(RegisterReqVO reqVO) {
        String username = reqVO.getUsername().trim();
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (exists > 0L) {
            throw new ServiceException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(reqVO.getPassword()));
        user.setNickname(reqVO.getNickname() == null || reqVO.getNickname().isBlank()
                ? username : reqVO.getNickname().trim());
        user.setCreateTime(LocalDateTime.now());
        user.setDeleted(false);
        userMapper.insert(user);
        return buildLoginResp(user);
    }

    @Override
    public LoginRespVO login(LoginReqVO reqVO) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, reqVO.getUsername().trim())
                .last("LIMIT 1"));
        if (user == null || !passwordEncoder.matches(reqVO.getPassword(), user.getPassword())) {
            throw new ServiceException("用户名或密码错误");
        }
        return buildLoginResp(user);
    }

    private LoginRespVO buildLoginResp(SysUser user) {
        LoginRespVO respVO = new LoginRespVO();
        respVO.setToken(jwtUtil.generate(user.getId(), user.getUsername(), user.getNickname()));
        respVO.setUserId(user.getId());
        respVO.setUsername(user.getUsername());
        respVO.setNickname(user.getNickname());
        return respVO;
    }

}