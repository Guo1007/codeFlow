package com.aidev.agent.common;

import lombok.Getter;

/**
 * 业务异常（自建极简版，替代 yudao ServiceException）。
 */
@Getter
public class ServiceException extends RuntimeException {

    private final int code;

    public ServiceException(String message) {
        this(500, message);
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

}
