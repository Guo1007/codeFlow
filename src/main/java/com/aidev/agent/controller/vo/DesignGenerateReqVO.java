package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 设计文档生成/修订 Request VO
 */
@Data
public class DesignGenerateReqVO {

    /**
     * 任务编号
     */
    @NotNull(message = "任务编号不能为空")
    private Long projectId;

    /**
     * 评审意见（仅修订时需要，生成为首次时无需传）
     */
    private String opinion;

}
