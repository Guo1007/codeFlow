package com.aidev.agent.controller.vo;

import lombok.Data;

/**
 * 知识库检索命中片段（供对话 RAG 注入与检索接口返回）
 */
@Data
public class KnowledgeHitVO {

    /**
     * 来源文档名称
     */
    private String name;

    /**
     * 所属目标项目（可能为空=全局）
     */
    private String project;

    /**
     * 命中文本片段
     */
    private String content;

}