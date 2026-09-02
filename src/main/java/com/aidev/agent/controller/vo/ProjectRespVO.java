package com.aidev.agent.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 开发任务详情 Response VO
 */
@Data
public class ProjectRespVO {

    /**
     * 任务编号
     */
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 需求文档内容（Markdown）
     */
    private String requirementContent;

    /**
     * 当前阶段：0-需求已录入 1-设计评审中 2-设计已定稿 3-代码生成中 4-代码已定稿 5-使用说明已生成
     */
    private Integer stage;

    /**
     * 代码生成阶段绑定的目标项目名称（未绑定时为 null）
     */
    private String targetProject;

    /**
     * 最新设计文档内容（未生成时为 null）
     */
    private String designContent;

    /**
     * 最新设计文档版本号（未生成时为 null）
     */
    private Integer designVersion;

    /**
     * 最新设计文档状态：0-AI 生成 1-人工修改 2-已定稿（未生成时为 null）
     */
    private Integer designStatus;
    /**
     * 最新代码产物版本号（未生成为 null）
     */
    private Integer codeVersion;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
