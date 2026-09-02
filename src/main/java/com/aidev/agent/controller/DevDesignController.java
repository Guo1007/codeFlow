package com.aidev.agent.controller;

import com.aidev.agent.common.ApiResponse;
import com.aidev.agent.controller.vo.DesignApproveReqVO;
import com.aidev.agent.controller.vo.DesignGenerateReqVO;
import com.aidev.agent.controller.vo.DesignSaveReqVO;
import com.aidev.agent.service.DevDesignService;
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
 * AI 开发设计文档控制器
 */
@Slf4j
@RestController
@RequestMapping("/dev/design")
@Validated
@RequiredArgsConstructor
public class DevDesignController {

    private final DevDesignService designService;

    /**
     * 根据需求文档流式生成设计文档（结束后自动保存版本）
     * <p>
     * 无评审意见为首次生成，有则为按意见修订。
     * 流中断（模型连接断开/超时）时以提示文本结束流，前端可见失败原因，
     * 已生成的部分内容不落库（由 Service 的 onError 逻辑保证）。
     * </p>
     */
    @PostMapping(value = "/generate-stream", produces = "text/event-stream;charset=utf-8")
    public Flux<String> generateDesignStream(@Valid @RequestBody DesignGenerateReqVO reqVO) {
        Flux<String> stream = reqVO.getOpinion() == null || reqVO.getOpinion().trim().isEmpty()
                ? designService.streamGenerateDesign(reqVO.getProjectId())
                : designService.streamReviseDesign(reqVO.getProjectId(), reqVO.getOpinion());
        return stream.onErrorResume(e -> {
            log.error("[generateDesignStream][设计文档流式生成中断，projectId={}]", reqVO.getProjectId(), e);
            return Flux.just("\n\n---\n**⚠️ 生成中断**：与 AI 服务的连接中断（" + e.getMessage()
                    + "）。本次已生成的内容未保存，请稍后重试。");
        });
    }

    /**
     * 保存人工编辑的设计文档（落为人工修改版本）
     */
    @PostMapping("/save")
    public ApiResponse<Boolean> saveDesign(@Valid @RequestBody DesignSaveReqVO reqVO) {
        designService.saveHumanEditedDesign(reqVO.getProjectId(), reqVO.getContent());
        return ApiResponse.success(true);
    }

    /**
     * 定稿设计文档（设计阶段的人工卡点，定稿后不可再修改）
     */
    @PostMapping("/approve")
    public ApiResponse<Boolean> approveDesign(@Valid @RequestBody DesignApproveReqVO reqVO) {
        designService.approveDesign(reqVO);
        return ApiResponse.success(true);
    }

}
