package com.aidev.agent.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 开发任务阶段枚举
 * <p>
 * 对应流水线：需求文档 → 设计文档（人机协同评审）→ 代码生成（人机协同精修）→ 使用说明
 */
@Getter
@RequiredArgsConstructor
public enum DevStageEnum {

    REQUIREMENT(0, "需求已录入"),
    DESIGN_REVIEWING(1, "设计评审中"),
    DESIGN_FINALIZED(2, "设计已定稿"),
    CODE_GENERATING(3, "代码生成中"),
    CODE_FINALIZED(4, "代码已定稿"),
    MANUAL_GENERATED(5, "使用说明已生成");

    /**
     * 阶段编号，对应 dev_project.stage
     */
    private final Integer stage;

    /**
     * 阶段名称
     */
    private final String name;

}
