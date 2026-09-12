package com.aidev.agent.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话会话 Response VO（历史会话列表项）
 */
@Data
public class ChatSessionVO {

    /**
     * 会话唯一标识
     */
    private String conversationId;

    /**
     * 所属目标项目名称
     */
    private String project;

    /**
     * 会话标题
     */
    private String title;

    /**
     * 消息条数
     */
    private Integer messageCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后对话时间
     */
    private LocalDateTime updateTime;

}