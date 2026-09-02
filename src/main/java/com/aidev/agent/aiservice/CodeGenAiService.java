package com.aidev.agent.aiservice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * AI 代码生成服务接口。
 * <p>
 * 与 {@link DevDocAiService}（纯文档生成，无工具）不同，代码生成需要：
 * 1. 读码工具（绑定目标项目根目录，了解现有分层与风格）
 * 2. 沙箱写入工具（将生成的代码文件写入 ai-output/{projectId} 沙箱，避免污染工程）
 * 因此该接口不通过 @AiService 声明式装配为单例，而由 {@link DevCodeService} 按任务动态构建
 * （每个开发任务绑定不同的沙箱目录）。
 * </p>
 * 返回流为 AI 的叙述文本（思考过程 + 文件写入结果），文件实体由工具调用落盘。
 */
public interface CodeGenAiService {

    /**
     * 根据设计文档在目标项目中生成代码（流式叙述）
     *
     * @param context 设计文档全文 + 目标项目框架画像
     * @return AI 的流式叙述输出
     */
    @SystemMessage(fromResource = "code-generate-prompt.txt")
    Flux<String> generateCode(@UserMessage String context);

}