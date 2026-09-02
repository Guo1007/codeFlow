package com.aidev.agent.controller.vo;

import lombok.Data;

import java.util.List;

/**
 * 沙箱文件树节点 VO
 */
@Data
public class CodeSandboxTreeVO {

    /**
     * 文件/目录名
     */
    private String name;

    /**
     * 相对路径（目录为空串）
     */
    private String path;

    /**
     * 是否为目录
     */
    private Boolean isDir;

    /**
     * 文件大小（字节），目录为 0
     */
    private Long size;

    /**
     * 子节点（仅目录有）
     */
    private List<CodeSandboxTreeVO> children;

    public CodeSandboxTreeVO(String name, String path, Boolean isDir, Long size, List<CodeSandboxTreeVO> children) {
        this.name = name;
        this.path = path;
        this.isDir = isDir;
        this.size = size;
        this.children = children;
    }

}