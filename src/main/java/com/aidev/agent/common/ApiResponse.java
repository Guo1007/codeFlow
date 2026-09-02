package com.aidev.agent.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结构（自建极简版，替代 yudao CommonResult）。
 * <p>
 * 约定：code = 0 表示成功，非 0 表示失败；SSE 流式接口不经过本结构包装。
 * </p>
 *
 * @param <T> 数据类型
 */
@Data
public class ApiResponse<T> implements Serializable {

    /**
     * 成功状态码
     */
    public static final int SUCCESS_CODE = 0;

    private int code;

    private T data;

    private String msg;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = SUCCESS_CODE;
        response.data = data;
        return response;
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = code;
        response.msg = msg;
        return response;
    }

}
