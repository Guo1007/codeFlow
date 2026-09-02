package com.aidev.agent.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * AI 开发流水线文档生成服务接口（声明式 @AiService）。
 * <p>
 * 与 {@link ChatAiService}（多轮对话助手）不同，本接口面向文档级生成任务：
 * 不挂聊天记忆（每次调用都携带完整上下文，避免长文档被记忆窗口截断）、
 * 不挂工具（纯文档生成，避免模型在生成过程中分心调用工具）。
 * 绑定独立的文档模型 Bean（超时按 agent.doc.timeout-multiplier 倍数放大）。
 * 每个流水线阶段（设计文档生成/修订、后续的代码生成、使用说明生成）对应一个方法，
 * 各自绑定专属的阶段提示词资源文件。
 * </p>
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        streamingChatModel = "devDocStreamingModel"
)
public interface DevDocAiService {

    /**
     * 根据需求文档生成设计文档（流式输出）
     *
     * @param context 包含需求文档全文及框架约束的输入上下文
     * @return 设计文档的 Markdown 内容流
     */
    @SystemMessage(fromResource = "design-generate-prompt.txt")
    Flux<String> generateDesignDoc(@UserMessage String context);

    /**
     * 根据评审意见修订设计文档（流式输出）
     *
     * @param context 包含当前设计文档全文与人工评审意见的输入上下文
     * @return 修订后设计文档的完整 Markdown 内容流
     */
    @SystemMessage(fromResource = "design-revise-prompt.txt")
    Flux<String> reviseDesignDoc(@UserMessage String context);

}
