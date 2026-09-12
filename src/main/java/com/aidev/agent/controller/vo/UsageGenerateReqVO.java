package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 使用说明生成 Request VO
 */
@Data
public class UsageGenerateReqVO {

    /**
     * 开发任务编号
     */
    @NotNull(message = "任务编号不能为空")
    private Long projectId;

}