package com.aidev.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话历史消息实体（完整留存，用于前端回显历史）
 */
@Data
@TableName("agent_chat_message")
public class AgentChatMessage {

    @TableId
    private Long id;

    /**
     * 所属会话 ID
     */
    private String conversationId;

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