package com.aidev.agent.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Set;

/**
 * 全局异常处理（自建极简版）。
 * <p>
 * 说明：本项目的 ApiResponse 由 Controller 显式返回，不使用 ResponseBodyAdvice 全局响应包装，
 * 因此 Flux / SSE 流式返回天然不受包装器影响。
 * SSE 接口的异常由 Controller 层的 onErrorResume 兜底（降级为流内错误事件）；
 * 本类兜住同步接口的异常，并对 text/event-stream 请求跳过 JSON 序列化
 * （响应 Content-Type 已定为 SSE，无法写 JSON，否则引发二次异常 HttpMessageNotWritableException）。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：正常提示，不打印堆栈
     */
    @ExceptionHandler(ServiceException.class)
    public ApiResponse<Void> serviceExceptionHandler(ServiceException ex,
                                                     HttpServletRequest request,
                                                     HttpServletResponse response) {
        if (isEventStreamRequest(request)) {
            markStreamError(response);
            return null;
        }
        return ApiResponse.error(ex.getCode(), ex.getMessage());
    }

    /**
     * 未知异常：打印堆栈，对外隐藏细节
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> defaultExceptionHandler(Exception ex,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        log.error("[defaultExceptionHandler][未捕获异常 uri={}]", request.getRequestURI(), ex);
        if (isEventStreamRequest(request)) {
            markStreamError(response);
            return null;
        }
        return ApiResponse.error(500, "系统异常，请联系管理员");
    }

    /**
     * 判断是否 SSE 流式请求（HandlerMapping 记录的可生产类型含 text/event-stream）
     */
    private boolean isEventStreamRequest(HttpServletRequest request) {
        Set<MediaType> producibleTypes = (Set<MediaType>) request
                .getAttribute(HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE);
        return producibleTypes != null && producibleTypes.contains(MediaType.TEXT_EVENT_STREAM);
    }

    /**
     * SSE 请求异常：响应可能已提交，仅标记状态码，不再写 JSON 体
     */
    private void markStreamError(HttpServletResponse response) {
        try {
            if (!response.isCommitted()) {
                response.setStatus(500);
            }
        } catch (Exception ignored) {
            // 连接已断开等场景，忽略
        }
    }

}
