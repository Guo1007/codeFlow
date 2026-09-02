package com.aidev.agent.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 开发阶段产物类型枚举
 */
@Getter
@RequiredArgsConstructor
public enum DevArtifactTypeEnum {

    DESIGN_DOC(1, "设计文档"),
    CODE(2, "代码"),
    MANUAL(3, "使用说明");

    /**
     * 类型编号，对应 dev_artifact.type
     */
    private final Integer type;

    /**
     * 类型名称
     */
    private final String name;

}
