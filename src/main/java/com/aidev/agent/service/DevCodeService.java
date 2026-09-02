package com.aidev.agent.service;

import com.aidev.agent.controller.vo.CodeSandboxTreeVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 代码生成 Service
 * <p>
 * 设计文档定稿后，AI 基于设计文档 + 目标项目框架画像，生成代码文件到沙箱目录
 * （{目标项目路径}/ai-output/{projectId}/），人工通过前端预览确认后，
 * 再执行应用操作将文件复制到目标项目真实路径。
 * </p>
 */
public interface DevCodeService {

    /**
     * 流式生成代码：AI 读取设计文档与目标项目代码，生成代码文件写入沙箱
     *
     * @param projectId       开发任务编号
     * @param targetProject   目标项目名称（用户选择的项目）
     * @return AI 的流式输出（包含思考过程与文件写入确认）
     */
    Flux<String> streamGenerateCode(Long projectId, String targetProject);

    /**
     * 列出沙箱中已生成的全部文件（树形结构，供前端文件树预览）
     *
     * @param projectId 开发任务编号
     * @return 文件树列表
     */
    List<CodeSandboxTreeVO> listSandboxFiles(Long projectId);

    /**
     * 读取沙箱中指定文件的内容（供前端预览）
     *
     * @param projectId 开发任务编号
     * @param relativePath 相对项目根目录的路径
     * @return 文件内容
     */
    String readSandboxFile(Long projectId, String relativePath);

    /**
     * 应用单个文件：从沙箱复制到目标项目真实路径
     *
     * @param projectId   开发任务编号
     * @param relativePath 相对路径
     */
    void applyFile(Long projectId, String relativePath);

    /**
     * 应用全部文件：将所有沙箱文件复制到目标项目真实路径
     *
     * @param projectId 开发任务编号
     */
    void applyAllFiles(Long projectId);

    /**
     * 清空沙箱目录（撤销本次生成）
     *
     * @param projectId 开发任务编号
     */
    void clearSandbox(Long projectId);

    /**
     * 代码定稿：将任务阶段推进到 CODE_FINALIZED，锁定代码生成阶段
     *
     * @param projectId 开发任务编号
     */
    void finalizeCode(Long projectId);

    /**
     * 将沙箱中的生成文件打成一个 zip 压缩包写入输出流（导出用）
     *
     * @param projectId 开发任务编号
     * @param out       输出流（zip 内容）
     * @return zip 中的文件数量
     */
    int exportSandboxZip(Long projectId, java.io.OutputStream out);

}