package com.aidev.agent.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话历史消息 Response VO
 */
@Data
public class ChatMessageVO {

    /**
     * 角色：user / assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}