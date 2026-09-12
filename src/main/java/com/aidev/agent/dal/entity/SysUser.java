package com.aidev.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（注册/登录，用于业务数据按用户隔离）。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId
    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 密码（BCrypt 加密密文）
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否删除
     */
    private Boolean deleted;

}