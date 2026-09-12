package com.aidev.agent.dal.mapper;

import com.aidev.agent.dal.entity.AgentChatSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话会话 Mapper
 */
@Mapper
public interface AgentChatSessionMapper extends BaseMapper<AgentChatSession> {

}