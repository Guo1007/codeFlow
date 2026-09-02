package com.aidev.agent.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的聊天记忆存储（自 ruoyi-vue-pro 原型阶段原样迁移，无框架依赖）。
 * <p>
 * 将 LangChain4j 的聊天消息序列化（JSON）后存入 Redis，支持消息的存取和删除。
 * 聊天记忆默认保存 7 天，过期后自动清除；Redis Key 为会话级 memoryId（由 Controller 拼接传入）。
 * </p>
 */
@Repository
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    /**
     * 聊天记忆有效期：7 天
     */
    private static final Duration MEMORY_TTL = Duration.ofDays(7);

    /**
     * Redis 字符串操作模板
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 根据会话 ID 获取聊天历史消息
     *
     * @param memoryId 会话标识
     * @return 历史消息列表，无记录时返回空列表
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = stringRedisTemplate.opsForValue().get(memoryId.toString());
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    /**
     * 更新指定会话的聊天消息，并刷新有效期
     *
     * @param memoryId 会话标识
     * @param messages 最新的聊天消息列表
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(messages);
        stringRedisTemplate.opsForValue().set(memoryId.toString(), json, MEMORY_TTL);
    }

    /**
     * 删除指定会话的所有聊天消息
     *
     * @param memoryId 会话标识
     */
    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(memoryId.toString());
    }

}
