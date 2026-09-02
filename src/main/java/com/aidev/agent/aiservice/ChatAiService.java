package com.aidev.agent.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * 通用对话 AI 服务接口（多目标项目版）。
 * <p>
 * 不再由 @AiService 声明式装配为单例：不同目标项目需要绑定不同的读码工具实例
 * 与项目专属系统提示词（含框架画像注入），因此由 {@link ProjectChatServiceFactory}
 * 按项目构建并缓存。接口只定义方法签名，模型调用、多轮记忆、工具调用、
 * 流式输出仍由 LangChain4j 生成的代理完成。
 * </p>
 */
public interface ChatAiService {

    /**
     * 同步对话，等待模型输出完整结果后一次性返回
     *
     * @param memoryId 聊天记忆标识（会话级），相同标识的多次调用共享上下文
     * @param message  用户输入
     * @return 模型输出的完整内容
     */
    String chat(@MemoryId String memoryId, @UserMessage String message);

    /**
     * 流式对话（多轮记忆），逐块推送模型输出
     *
     * @param memoryId 聊天记忆标识（会话级），相同标识的多次调用共享上下文
     * @param message  用户输入
     * @return 模型输出流，每个元素为一个文本片段
     */
    Flux<String> streamChat(@MemoryId String memoryId, @UserMessage String message);

}
