package com.aidev.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档实体（元数据）。向量段存 Redis，本表仅存文档元数据与原文，供列表/去重/删除管理。
 */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId
    private Long id;

    /**
     * 文档唯一标识（与 Redis 向量段元数据 documentId 关联）
     */
    private String documentId;

    /**
     * 所属目标项目名称（空 = 全局）
     */
    private String project;

    /**
     * 类型：upload=手动上传 / design=设计文档（定稿自动索引）
     */
    private String docType;

    /**
     * 版本号（定稿文档随版本变化，用于去重/替换）
     */
    private Integer version;

    /**
     * 文档名称
     */
    private String name;

    /**
     * 文档原文
     */
    private String content;

    /**
     * Redis 中该文档所有向量段 ID（逗号分隔，删除/替换向量用）
     */
    private String segmentIds;

    /**
     * 向量化状态：0 处理中 / 1 已完成
     */
    private Integer embeddingStatus;

    /**
     * 创建者
     */
    private String creator;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Boolean deleted;

}