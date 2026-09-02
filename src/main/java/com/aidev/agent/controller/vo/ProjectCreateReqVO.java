package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * AI 开发任务创建 Request VO
 */
@Data
public class ProjectCreateReqVO {

    /**
     * 任务名称
     */
    @NotEmpty(message = "任务名称不能为空")
    private String name;

    /**
     * 需求文档内容（Markdown）
     */
    @NotEmpty(message = "需求文档内容不能为空")
    private String requirementContent;

}
