package com.aidev.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

/**
 * AI 代码生成沙箱写入工具。
 * <p>
 * 代码生成阶段由 AI 调用，将生成的代码文件写入沙箱目录
 * {@code {targetProject.path}/ai-output/{projectId}/}，避免直接污染目标工程。
 * 人工通过前端预览确认后，再由应用操作将文件复制到目标项目真实路径。
 * 路径越界防护：禁止写入沙箱之外的目录。
 * </p>
 */
@Slf4j
public class SandboxWriteTools {

    /**
     * 沙箱根目录，对应目标项目路径下的 ai-output/{projectId}
     */
    private final Path sandboxRoot;

    public SandboxWriteTools(String targetProjectPath, Long projectId) {
        this.sandboxRoot = Paths.get(targetProjectPath, "ai-output", String.valueOf(projectId)).normalize();
    }

    /**
     * 获取沙箱根目录路径
     */
    public Path getSandboxRoot() {
        return sandboxRoot;
    }

    /**
     * 写入一个生成的代码文件到沙箱目录。
     * <p>
     * 如果文件所在目录不存在，自动创建父目录。
     * 如果文件已存在，覆盖写入。
     * </p>
     *
     * @param relativePath 相对项目根目录的路径，如 src/main/java/com/example/UserController.java
     * @param content      文件内容
     * @return 写入结果提示
     */
    @Tool("将生成的代码文件写入沙箱（写入目标项目路径下的 ai-output/{projectId} 目录），" +
            "relativePath 为相对项目根目录的路径，如 src/main/java/com/example/UserController.java。" +
            "如果文件所在目录不存在会自动创建，已存在的文件会被覆盖。")
    public String writeGeneratedCode(String relativePath, String content) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return "❌ 文件路径不能为空";
        }
        // 去掉开头的 / 或 \
        String cleanPath = relativePath.trim().replaceFirst("^[/\\\\]+", "");
        if (cleanPath.isEmpty()) {
            return "❌ 文件路径无效";
        }
        Path target = sandboxRoot.resolve(cleanPath).normalize();
        // 防目录穿越：必须在沙箱根目录内
        if (!target.startsWith(sandboxRoot)) {
            log.warn("[SandboxWriteTools][拒绝越界路径 {}]", relativePath);
            return "❌ 路径越界：" + relativePath;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
            log.info("[SandboxWriteTools][文件已写入 {}，大小 {} bytes]", cleanPath, content.length());
            return "✅ 已写入：" + cleanPath + "（" + content.length() + " 字节）";
        } catch (IOException e) {
            log.error("[SandboxWriteTools][写入失败 {}]", cleanPath, e);
            return "❌ 写入失败：" + e.getMessage();
        }
    }

    /**
     * 列出沙箱中已生成的全部文件（树形结构）
     *
     * @return 文件列表文本
     */
    @Tool("列出沙箱中已生成的全部代码文件（树形结构），用于查看已生成的文件清单")
    public String listGeneratedFiles() {
        if (!Files.isDirectory(sandboxRoot)) {
            return "（沙箱目录为空，尚未生成任何文件）";
        }
        StringBuilder sb = new StringBuilder("【沙箱文件清单】\n");
        try (Stream<Path> walk = Files.walk(sandboxRoot)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            if (files.isEmpty()) {
                return "（沙箱目录为空，尚未生成任何文件）";
            }
            for (Path file : files) {
                String relative = sandboxRoot.relativize(file).toString().replace('\\', '/');
                long size = Files.size(file);
                sb.append("  ").append(relative).append("  (").append(size).append(" bytes)\n");
            }
        } catch (IOException e) {
            log.error("[SandboxWriteTools][列出沙箱文件失败]", e);
            return "❌ 列出文件失败：" + e.getMessage();
        }
        return sb.toString();
    }

    /**
     * 删除沙箱中的指定文件
     *
     * @param relativePath 相对路径
     * @return 删除结果提示
     */
    @Tool("删除沙箱中已生成的指定文件，relativePath 为相对项目根目录的路径，如 src/main/java/com/example/UserController.java")
    public String deleteGeneratedFile(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return "❌ 文件路径不能为空";
        }
        String cleanPath = relativePath.trim().replaceFirst("^[/\\\\]+", "");
        Path target = sandboxRoot.resolve(cleanPath).normalize();
        if (!target.startsWith(sandboxRoot)) {
            return "❌ 路径越界：" + relativePath;
        }
        try {
            if (Files.deleteIfExists(target)) {
                log.info("[SandboxWriteTools][已删除 {}]", cleanPath);
                return "✅ 已删除：" + cleanPath;
            }
            return "⚠️ 文件不存在：" + cleanPath;
        } catch (IOException e) {
            log.error("[SandboxWriteTools][删除失败 {}]", cleanPath, e);
            return "❌ 删除失败：" + e.getMessage();
        }
    }

}