package com.aidev.agent.config;

import com.aidev.agent.repository.RedisChatMemoryStore;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 组件统一装配配置类（参照 FurnitureSystem 的 AiConfig 风格）。
 * <p>
 * 将 LangChain4j 相关的所有组件集中在此注册：
 * <ul>
 *   <li>聊天记忆提供者 {@link ChatMemoryProvider}（Redis 存储多轮上下文）</li>
 *   <li>Redis 向量库 {@link EmbeddingStore}（知识库，RediSearch）</li>
 *   <li>RAG 内容检索器 {@link ContentRetriever}</li>
 *   <li>文档级生成模型 {@link dev.langchain4j.model.chat.StreamingChatModel}（devDocStreamingModel，超时按倍数放大）</li>
 * </ul>
 * 通用对话 {@link dev.langchain4j.model.chat.ChatModel} / {@link StreamingChatModel} 由
 * langchain4j-open-ai-spring-boot4-starter 自动注册；多目标项目的对话服务仍由
 * {@link com.aidev.agent.aiservice.ProjectChatServiceFactory} 按项目动态构建（保留多项目能力）。
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AiConfig {

    /**
     * 每个会话保留的最大消息数（超出后淘汰最早的消息）
     */
    private static final int MAX_MEMORY_MESSAGES = 20;

    /**
     * Redis 聊天记忆存储
     */
    private final RedisChatMemoryStore redisChatMemoryStore;

    /**
     * Redis 向量存储（由 langchain4j-community-redis starter 自动装配）
     */
    private final RedisEmbeddingStore redisEmbeddingStore;

    /**
     * 创建聊天记忆提供者。
     * <p>
     * 每个会话（memoryId）独立一份记忆，底层存储在 Redis，保留最近 20 条消息。
     * </p>
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(MAX_MEMORY_MESSAGES)
                .build();
    }

    /**
     * 创建 Redis 向量存储实例（知识库，RediSearch）。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("[AiConfig][embeddingStore] 已装配 Redis 向量存储");
        return redisEmbeddingStore;
    }

    /**
     * 创建 RAG 内容检索器，基于向量相似度检索相关片段。
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                             EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .minScore(0.2)
                .maxResults(5)
                .build();
    }

    /**
     * 文档解析器（Apache Tika）：知识库上传 PDF/Word/Excel/PPT/HTML 等文件时抽取为纯文本。
     */
    @Bean
    public DocumentParser documentParser() {
        return new ApacheTikaDocumentParser();
    }

    /**
     * 创建文档级生成模型（文档任务耗时长，超时在通用流式模型基础上按
     * agent.doc.timeout-multiplier 倍数放大，独立于通用对话模型 Bean）。
     */
    @Bean
    public StreamingChatModel devDocStreamingModel(
            @Value("${langchain4j.open-ai.streaming-chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.streaming-chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.streaming-chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.streaming-chat-model.temperature:0.7}") Double temperature,
            @Value("${langchain4j.open-ai.streaming-chat-model.timeout:PT60S}") Duration timeout,
            AgentProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(timeout.multipliedBy(properties.getDoc().getTimeoutMultiplier()))
                .build();
    }

}