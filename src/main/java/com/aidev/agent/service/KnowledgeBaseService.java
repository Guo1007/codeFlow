package com.aidev.agent.service;

import com.aidev.agent.controller.vo.KnowledgeDocVO;
import com.aidev.agent.controller.vo.KnowledgeHitVO;

import java.util.List;

/**
 * 知识库服务：文档向量化入库（Redis 向量库）+ 检索。
 */
public interface KnowledgeBaseService {

    /**
     * 列出当前用户可见的知识库文档（管理用，不含原文）
     */
    List<KnowledgeDocVO> listDocuments(String userId, String project);

    /**
     * 手动上传文档并向量化入库
     */
    KnowledgeDocVO uploadDocument(String userId, String project, String name, String content);

    /**
     * 删除文档（物理删除 MySQL 记录 + 对应 Redis 向量段）
     */
    void deleteDocument(Long id, String userId);

    /**
     * 设计文档定稿时自动索引（幂等：同项目只保留最新定稿版本，替换旧版本）
     */
    void indexDesign(String projectName, String content, Integer version, String creator);

    /**
     * 向量检索：query 与知识库中最相关 topK 片段（按项目 + 全局）
     */
    List<KnowledgeHitVO> search(String project, String query, int topK);

}