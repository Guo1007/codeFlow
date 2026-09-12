package com.aidev.agent.service;

import reactor.core.publisher.Flux;

/**
 * AI 使用说明生成 Service。
 * <p>
 * 代码定稿后，基于定稿设计文档 + 目标项目真实代码，流式生成本模块/需求的
 * 使用说明（部署/集成/使用/注意事项），生成结束后自动落库为 MANUAL 产物
 * 并将任务阶段推进到「使用说明已生成」(5)，即流水线终态。支持重新生成
 * （每次生成 MANUAL 产物版本 +1，阶段始终保持在 5）。
 * </p>
 */
public interface DevUsageService {

    /**
     * 流式生成使用说明，结束后自动保存产物并推进阶段
     *
     * @param projectId 开发任务编号
     * @return 使用说明 Markdown 内容流
     */
    Flux<String> streamGenerateManual(Long projectId);

}