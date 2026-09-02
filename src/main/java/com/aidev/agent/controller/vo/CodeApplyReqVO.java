package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 代码应用 Request VO
 */
@Data
public class CodeApplyReqVO {

    /**
     * 开发任务编号
     */
    @NotNull(message = "任务编号不能为空")
    private Long projectId;

    /**
     * 相对路径（应用单个文件时使用，为空时表示应用全部）
     */
    private String relativePath;

}