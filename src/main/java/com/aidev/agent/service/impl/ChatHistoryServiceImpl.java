package com.aidev.agent.service.impl;

import com.aidev.agent.common.ServiceException;
import com.aidev.agent.controller.vo.ChatMessageVO;
import com.aidev.agent.controller.vo.ChatSessionVO;
import com.aidev.agent.dal.entity.AgentChatMessage;
import com.aidev.agent.dal.entity.AgentChatSession;
import com.aidev.agent.dal.mapper.AgentChatMessageMapper;
import com.aidev.agent.dal.mapper.AgentChatSessionMapper;
import com.aidev.agent.service.ChatHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * AI 对话历史持久化 Service 实现类
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private static final int TITLE_MAX = 40;

    private final AgentChatSessionMapper sessionMapper;

    private final AgentChatMessageMapper messageMapper;

    @Override
    public String ensureSession(String conversationId, String project, String firstPrompt, String userId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new ServiceException("会话标识不能为空");
        }
        AgentChatSession exist = sessionMapper.selectOne(new LambdaQueryWrapper<AgentChatSession>()
                .eq(AgentChatSession::getConversationId, conversationId)
                .eq(userId != null && !userId.isBlank(), AgentChatSession::getCreator, userId)
                .last("LIMIT 1"));
        if (exist != null) {
            return conversationId;
        }
        AgentChatSession session = new AgentChatSession();
        session.setConversationId(conversationId);
        session.setProject(project == null ? "" : project);
        session.setTitle(buildTitle(firstPrompt));
        session.setMessageCount(0);
        session.setCreator(userId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        session.setDeleted(false);
        sessionMapper.insert(session);
        return conversationId;
    }

    @Override
    public void saveUserMessage(String conversationId, String content) {
        insertMessage(conversationId, "user", content);
    }

    @Override
    public void saveAssistantMessage(String conversationId, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        insertMessage(conversationId, "assistant", content);
    }

    @Override
    public List<ChatSessionVO> listSessions(String project, String userId) {
        LambdaQueryWrapper<AgentChatSession> wrapper = new LambdaQueryWrapper<AgentChatSession>()
                .eq(AgentChatSession::getCreator, userId)
                .eq(project != null && !project.isBlank(), AgentChatSession::getProject, project)
                .orderByDesc(AgentChatSession::getUpdateTime)
                .last("LIMIT 200");
        return sessionMapper.selectList(wrapper).stream().map(s -> {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setConversationId(s.getConversationId());
            vo.setProject(s.getProject());
            vo.setTitle(s.getTitle());
            vo.setMessageCount(s.getMessageCount());
            vo.setCreateTime(s.getCreateTime());
            vo.setUpdateTime(s.getUpdateTime());
            return vo;
        }).toList();
    }

    @Override
    public List<ChatMessageVO> listMessages(String conversationId, String userId) {
        getOwnedSession(conversationId, userId);
        List<AgentChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getConversationId, conversationId)
                .orderByAsc(AgentChatMessage::getId));
        return messages.stream().map(m -> {
            ChatMessageVO vo = new ChatMessageVO();
            vo.setRole(m.getRole());
            vo.setContent(m.getContent());
            vo.setCreateTime(m.getCreateTime());
            return vo;
        }).toList();
    }

    @Override
    public void deleteSession(String conversationId, String userId) {
        AgentChatSession session = getOwnedSession(conversationId, userId);
        // 物理删除会话记录（连同会话消息，均不再保留）
        sessionMapper.deleteById(session.getId());
        // 物理删除该会话所有消息
        messageMapper.delete(new LambdaQueryWrapper<AgentChatMessage>()
                .eq(AgentChatMessage::getConversationId, conversationId));
    }

    @Override
    public void renameSession(String conversationId, String title, String userId) {
        AgentChatSession session = getOwnedSession(conversationId, userId);
        session.setTitle(title.trim());
        sessionMapper.updateById(session);
    }

    private void insertMessage(String conversationId, String role, String content) {
        AgentChatMessage message = new AgentChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        // 递增会话消息数、刷新最后对话时间
        AgentChatSession session = getSession(conversationId);
        AgentChatSession update = new AgentChatSession();
        update.setId(session.getId());
        update.setMessageCount(session.getMessageCount() + 1);
        update.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(update);
    }

    private AgentChatSession getSession(String conversationId) {
        AgentChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AgentChatSession>()
                .eq(AgentChatSession::getConversationId, conversationId)
                .eq(AgentChatSession::getDeleted, false)
                .last("LIMIT 1"));
        if (session == null) {
            throw new ServiceException("会话不存在");
        }
        return session;
    }

    /**
     * 查询并校验会话归属，非本人会话抛出无权操作
     */
    private AgentChatSession getOwnedSession(String conversationId, String userId) {
        AgentChatSession session = getSession(conversationId);
        if (!String.valueOf(session.getCreator()).equals(userId)) {
            throw new ServiceException("无权操作该会话");
        }
        return session;
    }

    /**
     * 由首条用户消息生成标题：压缩空白换行后截断
     */
    private static String buildTitle(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "新对话";
        }
        String single = prompt.trim().replaceAll("\\s+", " ");
        return single.length() <= TITLE_MAX ? single : single.substring(0, TITLE_MAX) + "…";
    }

}