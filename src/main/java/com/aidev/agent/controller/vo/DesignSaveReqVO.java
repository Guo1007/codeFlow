package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI 设计文档人工保存 Request VO
 */
@Data
public class DesignSaveReqVO {

    /**
     * 任务编号
     */
    @NotNull(message = "任务编号不能为空")
    private Long projectId;

    /**
     * 人工编辑后的设计文档全文
     */
    @NotNull(message = "设计文档内容不能为空")
    private String content;

}
