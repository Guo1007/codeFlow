package com.aidev.agent.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 文档级生成模型装配。
 * <p>
 * 文档级任务（生成整份设计文档）耗时长，在 langchain4j.open-ai.streaming-chat-model 配置的
 * 超时基础上按 agent.doc.timeout-multiplier 倍数放大，独立于通用对话模型 Bean。
 * </p>
 */
@Configuration(proxyBeanMethods = false)
public class DevDocModelConfig {

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
