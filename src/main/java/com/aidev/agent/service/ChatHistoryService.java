package com.aidev.agent.service;

import com.aidev.agent.controller.vo.ChatMessageVO;
import com.aidev.agent.controller.vo.ChatSessionVO;

import java.util.List;

/**
 * AI 对话历史持久化 Service。
 * <p>
 * 会话索引 + 历史消息完整落库，供前端列出历史对话、一键载入回显并继续；
 * AI 多轮上下文记忆仍由 Redis（ChatMemoryProvider）承担，两者互补。
 * </p>
 */
public interface ChatHistoryService {

    /**
     * 确保会话存在（不存在则创建，标题取首条用户消息摘要），返回 conversationId
     *
     * @param userId 归属用户 ID（写入 creator）
     */
    String ensureSession(String conversationId, String project, String firstPrompt, String userId);

    /**
     * 保存一条用户消息（并递增会话消息数）
     */
    void saveUserMessage(String conversationId, String content);

    /**
     * 保存一条 AI 回复（并递增会话消息数、刷新会话时间）
     */
    void saveAssistantMessage(String conversationId, String content);

    /**
     * 历史会话列表（按归属用户过滤，可选按项目过滤，按最后对话时间倒序）
     */
    List<ChatSessionVO> listSessions(String project, String userId);

    /**
     * 某会话的完整历史消息（校验归属）
     */
    List<ChatMessageVO> listMessages(String conversationId, String userId);

    /**
     * 删除会话（校验归属；逻辑删除会话记录 + 物理删除该会话消息）
     */
    void deleteSession(String conversationId, String userId);

    /**
     * 重命名会话标题（校验归属）
     */
    void renameSession(String conversationId, String title, String userId);

}