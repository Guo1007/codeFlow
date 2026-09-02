package com.aidev.agent.config;

import com.aidev.agent.repository.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天记忆装配（模型 Bean 由 langchain4j boot4 starter 自动注册，无需手动装配）。
 * <p>
 * 每个会话（memoryId）独立一份记忆，底层存储在 Redis，保留最近 20 条消息。
 * </p>
 */
@Configuration(proxyBeanMethods = false)
public class ChatMemoryConfig {

    /**
     * 每个会话保留的最大消息数（超出后淘汰最早的消息）
     */
    private static final int MAX_MEMORY_MESSAGES = 20;

    /**
     * 聊天记忆提供者，供 @AiService 的 chatMemoryProvider 装配引用
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(RedisChatMemoryStore redisChatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
    }

}
