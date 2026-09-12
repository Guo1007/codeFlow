package com.aidev.agent.dal.mapper;

import com.aidev.agent.dal.entity.AgentChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话历史消息 Mapper
 */
@Mapper
public interface AgentChatMessageMapper extends BaseMapper<AgentChatMessage> {

}