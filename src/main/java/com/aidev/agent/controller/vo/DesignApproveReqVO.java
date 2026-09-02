package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 设计文档定稿 Request VO
 */
@Data
public class DesignApproveReqVO {

    /**
     * 任务编号
     */
    @NotNull(message = "任务编号不能为空")
    private Long projectId;

    /**
     * 定稿时最后的全文修改（与最新版本相同时不另存版本）
     */
    private String content;

}
