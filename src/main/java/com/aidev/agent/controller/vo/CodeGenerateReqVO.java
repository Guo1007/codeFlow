package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 代码生成 Request VO
 */
@Data
public class CodeGenerateReqVO {

    /**
     * 开发任务编号
     */
    @NotNull(message = "任务编号不能为空")
    private Long projectId;

    /**
     * 目标项目名称（需先在 AgentProperties 或数据库中注册）
     */
    @NotBlank(message = "目标项目不能为空")
    private String targetProject;

}