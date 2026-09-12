package com.aidev.agent.common;

import java.util.Objects;

/**
 * 当前登录用户上下文（线程级，由 LoginInterceptor 填充，请求结束后清理）。
 * <p>
 * 替代原来写死的 admin：业务层通过 getUserId() 取当前登录用户，用于数据按用户隔离。
 * </p>
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /**
     * 当前用户 ID 字符串（业务表 creator 字段存储用）
     */
    public static String getUserIdStr() {
        Long id = getUserId();
        return id == null ? "" : String.valueOf(id);
    }

    public static String getUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? "" : user.getUsername();
    }

    public static String getNickname() {
        LoginUser user = HOLDER.get();
        return user == null ? "" : user.getNickname();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 当前登录用户信息
     */
    public static class LoginUser {

        private final Long userId;
        private final String username;
        private final String nickname;

        public LoginUser(Long userId, String username, String nickname) {
            this.userId = Objects.requireNonNull(userId, "userId 不能为空");
            this.username = username;
            this.nickname = nickname;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getNickname() {
            return nickname;
        }
    }

}