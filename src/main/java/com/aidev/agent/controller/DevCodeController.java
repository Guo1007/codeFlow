package com.aidev.agent.controller;

import com.aidev.agent.common.ApiResponse;
import com.aidev.agent.controller.vo.CodeApplyReqVO;
import com.aidev.agent.controller.vo.CodeGenerateReqVO;
import com.aidev.agent.controller.vo.CodeSandboxTreeVO;
import com.aidev.agent.service.DevCodeService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI 代码生成控制器。
 * <p>
 * 设计文档定稿后：选择一个目标项目 → 流式生成代码到沙箱 → 前端预览
 * → 应用到目标项目真实路径 → 代码定稿。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/dev/code")
@RequiredArgsConstructor
public class DevCodeController {

    private final DevCodeService codeService;

    /**
     * 流式生成代码（SSE），AI 叙述 + 写入沙箱；结束后自动保存代码产物清单并推进阶段
     */
    @PostMapping(value = "/generate-stream", produces = "text/event-stream;charset=utf-8")
    public Flux<String> generateCodeStream(@Valid @RequestBody CodeGenerateReqVO reqVO) {
        return codeService.streamGenerateCode(reqVO.getProjectId(), reqVO.getTargetProject())
                .onErrorResume(e -> {
                    log.error("[generateCodeStream][代码生成流式中断，projectId={}]", reqVO.getProjectId(), e);
                    return Flux.just("\n\n---\n**⚠️ 生成中断**：与 AI 服务的连接中断（" + e.getMessage()
                            + "）。本次已生成的内容未保存，请稍后重试。");
                });
    }

    /**
     * 沙箱文件树预览
     */
    @GetMapping("/tree")
    public ApiResponse<List<CodeSandboxTreeVO>> sandboxTree(@RequestParam("projectId") Long projectId) {
        return ApiResponse.success(codeService.listSandboxFiles(projectId));
    }

    /**
     * 读取沙箱中指定文件内容（预览）
     */
    @GetMapping("/file")
    public ApiResponse<String> readSandboxFile(@RequestParam("projectId") Long projectId,
                                               @RequestParam("path") String path) {
        return ApiResponse.success(codeService.readSandboxFile(projectId, path));
    }

    /**
     * 应用全部代码到目标项目真实路径
     */
    @PostMapping("/apply-all")
    public ApiResponse<Boolean> applyAll(@Valid @RequestBody CodeApplyReqVO reqVO) {
        codeService.applyAllFiles(reqVO.getProjectId());
        return ApiResponse.success(true);
    }

    /**
     * 应用单个代码文件到目标项目真实路径
     */
    @PostMapping("/apply")
    public ApiResponse<Boolean> apply(@Valid @RequestBody CodeApplyReqVO reqVO) {
        codeService.applyFile(reqVO.getProjectId(), reqVO.getRelativePath());
        return ApiResponse.success(true);
    }

    /**
     * 清空沙箱（撤销本次生成，重新生成）
     */
    @PostMapping("/clear")
    public ApiResponse<Boolean> clear(@Valid @RequestBody CodeApplyReqVO reqVO) {
        codeService.clearSandbox(reqVO.getProjectId());
        return ApiResponse.success(true);
    }

    /**
     * 应用代码后定稿
     */
    @PostMapping("/finalize")
    public ApiResponse<Boolean> finalize(@Valid @RequestBody CodeApplyReqVO reqVO) {
        codeService.finalizeCode(reqVO.getProjectId());
        return ApiResponse.success(true);
    }

    /**
     * 导出沙箱代码为 zip 压缩包（保留相对目录结构）
     * <p>
     * 直接写二进制 zip 流，不走 ApiResponse 包装（GlobalExceptionHandler 需放行该类型）。
     * </p>
     */
    @GetMapping("/export")
    public void export(@RequestParam("projectId") Long projectId, HttpServletResponse response) throws IOException {
        String filename = "code-" + projectId + ".zip";
        // 文件名中文做 URL 编码，避免下载时乱码
        String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encoded + "\"");
        codeService.exportSandboxZip(projectId, response.getOutputStream());
    }

}