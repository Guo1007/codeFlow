package com.aidev.agent.controller;

import com.aidev.agent.controller.vo.UsageGenerateReqVO;
import com.aidev.agent.service.DevUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 使用说明控制器。
 * <p>
 * 代码定稿后：流式生成使用说明 → 前端预览 → 生成结束自动落库为 MANUAL 产物
 * 并将任务推进到流水线终态（使用说明已生成）；支持重新生成（版本 +1）。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/dev/usage")
@Validated
@RequiredArgsConstructor
public class DevUsageController {

    private final DevUsageService usageService;

    /**
     * 流式生成使用说明（SSE），结束后自动保存 MANUAL 产物并推进阶段
     */
    @PostMapping(value = "/generate-stream", produces = "text/event-stream;charset=utf-8")
    public Flux<String> generateManualStream(@Valid @RequestBody UsageGenerateReqVO reqVO) {
        return usageService.streamGenerateManual(reqVO.getProjectId())
                .onErrorResume(e -> {
                    log.error("[generateManualStream][使用说明生成流式中断，projectId={}]", reqVO.getProjectId(), e);
                    return Flux.just("\n\n---\n**⚠️ 生成中断**：与 AI 服务的连接中断（" + e.getMessage()
                            + "）。本次已生成的内容未保存，请稍后重试。");
                });
    }

}