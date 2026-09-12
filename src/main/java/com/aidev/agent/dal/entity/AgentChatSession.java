package com.aidev.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话会话实体（历史会话索引）。
 * <p>
 * 用于在前端列出历史对话并可一键载入继续对话；
 * 一条会话对应唯一的 conversationId（同时也是 Redis 记忆 memoryId 的一部分）。
 * </p>
 */
@Data
@TableName("agent_chat_session")
public class AgentChatSession {

    @TableId
    private Long id;

    /**
     * 会话唯一标识
     */
    private String conversationId;

    /**
     * 所属目标项目名称
     */
    private String project;

    /**
     * 会话标题（由首条用户消息摘要）
     */
    private String title;

    /**
     * 消息条数
     */
    private Integer messageCount;

    /**
     * 创建者
     */
    private String creator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 最后对话时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    private Boolean deleted;

}