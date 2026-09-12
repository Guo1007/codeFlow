package com.aidev.agent.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * AI 使用说明生成服务接口。
 * <p>
 * 代码定稿后，基于定稿设计文档 + 目标项目的真实落盘代码（读码工具），
 * 生成面向部署/运行/使用的说明文档。与代码生成类似，需要读码工具，
 * 因此不由 @AiService 声明式为单例，而由 {@link com.aidev.agent.service.DevUsageService}
 * 按任务动态构建（绑定目标项目读码工具）。
 * </p>
 */
public interface UsageManualAiService {

    /**
     * 流式生成使用说明
     *
     * @param context 定稿设计文档 + 目标项目信息
     * @return 使用说明 Markdown 内容流
     */
    @SystemMessage(fromResource = "usage-manual-prompt.txt")
    Flux<String> generateManual(@UserMessage String context);

}