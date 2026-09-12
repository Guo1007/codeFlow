package com.aidev.agent.controller.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档 Response VO（列表用，不含原文大字段）
 */
@Data
public class KnowledgeDocVO {

    private Long id;

    private String documentId;

    private String project;

    private String docType;

    private Integer version;

    private String name;

    private Integer embeddingStatus;

    private LocalDateTime createTime;

}