package com.aidev.agent.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 开发任务摘要 Response VO（历史列表用，不含需求文档大字段）
 */
@Data
public class ProjectSimpleVO {

    /**
     * 任务编号
     */
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 当前阶段：0-需求已录入 1-设计评审中 2-设计已定稿 3-代码生成中 4-代码已定稿 5-使用说明已生成
     */
    private Integer stage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
