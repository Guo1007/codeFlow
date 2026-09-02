package com.aidev.agent.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 开发阶段产物状态枚举
 * <p>
 * 状态流转：AI 生成 → 人工修改 → 已定稿（定稿后不可再变更，如需调整需发起新版本）
 */
@Getter
@RequiredArgsConstructor
public enum DevArtifactStatusEnum {

    AI_GENERATED(0, "AI 生成"),
    HUMAN_MODIFIED(1, "人工修改"),
    FINALIZED(2, "已定稿");

    /**
     * 状态编号，对应 dev_artifact.status
     */
    private final Integer status;

    /**
     * 状态名称
     */
    private final String name;

}
