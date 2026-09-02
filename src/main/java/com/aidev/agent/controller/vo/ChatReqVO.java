package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * AI 对话 Request VO
 */
@Data
public class ChatReqVO {

    /**
     * 用户输入
     */
    @NotEmpty(message = "用户输入不能为空")
    private String prompt;

    /**
     * 会话唯一标识（多轮对话），首次请求可不传，流式接口将自动生成并通过 meta 事件返回
     */
    private String conversationId;

    /**
     * 目标项目名称（agent.targets 中注册的项目，为空时取默认项目）
     */
    private String project;

}
