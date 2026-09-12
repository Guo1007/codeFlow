package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 知识库文档上传请求 VO
 */
@Data
public class KnowledgeUploadReqVO {

    /**
     * 所属目标项目名称（空 = 全局，对所有项目对话可见）
     */
    private String project;

    @NotEmpty(message = "文档名称不能为空")
    private String name;

    @NotEmpty(message = "文档内容不能为空")
    private String content;

}