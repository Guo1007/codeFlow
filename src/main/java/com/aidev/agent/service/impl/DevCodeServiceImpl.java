package com.aidev.agent.service.impl;

import com.aidev.agent.aiservice.CodeGenAiService;
import com.aidev.agent.common.ServiceException;
import com.aidev.agent.config.AgentProperties;
import com.aidev.agent.config.TargetProjectRegistry;
import com.aidev.agent.controller.vo.CodeSandboxTreeVO;
import com.aidev.agent.dal.entity.DevArtifact;
import com.aidev.agent.dal.entity.DevProject;
import com.aidev.agent.dal.mapper.DevArtifactMapper;
import com.aidev.agent.dal.mapper.DevProjectMapper;
import com.aidev.agent.enums.DevArtifactStatusEnum;
import com.aidev.agent.enums.DevArtifactTypeEnum;
import com.aidev.agent.enums.DevStageEnum;
import com.aidev.agent.service.DevCodeService;
import com.aidev.agent.tools.ProjectCodeTools;
import com.aidev.agent.tools.SandboxWriteTools;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * AI 代码生成 Service 实现类。
 * <p>
 * 设计文档定稿（阶段≥2）后，AI 基于设计文档 + 目标项目框架画像，通过读码工具了解
 * 项目分层规范，然后用沙箱写入工具把生成的文件写到目标项目根目录下的
 * {@code ai-output/{projectId}/}，人工预览确认后再应用回项目真实路径。
 * </p>
 */
@Service
@Slf4j
public class DevCodeServiceImpl implements DevCodeService {

    /**
     * 沙箱在目标项目根目录下的相对目录名
     */
    private static final String OUTPUT_DIR = "ai-output";

    private final DevProjectMapper projectMapper;

    private final DevArtifactMapper artifactMapper;

    private final TargetProjectRegistry targetProjectRegistry;

    private final StreamingChatModel devDocStreamingModel;

    /**
     * 生成中的任务集合（projectId -> true），防止同一任务并发发起生成
     */
    private final ConcurrentHashMap<Long, Boolean> generatingProjects = new ConcurrentHashMap<>();

    public DevCodeServiceImpl(DevProjectMapper projectMapper,
                              DevArtifactMapper artifactMapper,
                              TargetProjectRegistry targetProjectRegistry,
                              @Qualifier("devDocStreamingModel") StreamingChatModel devDocStreamingModel) {
        this.projectMapper = projectMapper;
        this.artifactMapper = artifactMapper;
        this.targetProjectRegistry = targetProjectRegistry;
        this.devDocStreamingModel = devDocStreamingModel;
    }

    @Override
    public Flux<String> streamGenerateCode(Long projectId, String targetProject) {
        DevProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        // 设计文档定稿后才可生成代码
        if (project.getStage() == null || project.getStage() < DevStageEnum.DESIGN_FINALIZED.getStage()) {
            throw new ServiceException("设计文档尚未定稿，无法生成代码");
        }
        if (DevStageEnum.CODE_FINALIZED.getStage().equals(project.getStage())) {
            throw new ServiceException("代码已定稿，不可再生成");
        }
        DevArtifact design = artifactMapper.selectLatestDesign(projectId);
        if (design == null) {
            throw new ServiceException("设计文档缺失，无法生成代码");
        }
        AgentProperties.TargetProject target = targetProjectRegistry.resolve(targetProject);
        if (!target.getName().equals(project.getTargetProject())) {
            // 持久化目标项目绑定
            DevProject update = new DevProject();
            update.setId(projectId);
            update.setTargetProject(target.getName());
            projectMapper.updateById(update);
            log.info("[streamGenerateCode][任务 {} 绑定目标项目 {}]", projectId, target.getName());
        }
        if (generatingProjects.putIfAbsent(projectId, Boolean.TRUE) != null) {
            throw new ServiceException("该任务正在生成代码中，请勿重复发起");
        }

        Path sandboxRoot = sandboxPath(target.getPath(), projectId);
        // 动态构建：每个任务绑定独立的沙箱写入工具（读码工具按目标项目复用）
        CodeGenAiService codeGen = AiServices.builder(CodeGenAiService.class)
                .streamingChatModel(devDocStreamingModel)
                .tools(new ProjectCodeTools(target.getPath()), new SandboxWriteTools(target.getPath(), projectId))
                .build();
        String context = buildContext(project, design, target);

        return Flux.defer(() -> codeGen.generateCode(context)
                .doOnError(e -> {
                    generatingProjects.remove(projectId);
                    log.error("[streamGenerateCode][任务 {} 代码生成失败]", projectId, e);
                })
                .doOnCancel(() -> {
                    generatingProjects.remove(projectId);
                    log.warn("[streamGenerateCode][任务 {} 代码生成被取消]", projectId);
                })
                .doOnComplete(() -> {
                    generatingProjects.remove(projectId);
                    projectMapper.updateStage(projectId, DevStageEnum.CODE_GENERATING.getStage());
                    saveCodeManifest(projectId, sandboxRoot, target.getName());
                }));
    }

    @Override
    public List<CodeSandboxTreeVO> listSandboxFiles(Long projectId) {
        Path sandboxRoot = sandboxForProject(projectId);
        if (!Files.isDirectory(sandboxRoot)) {
            return List.of();
        }
        CodeSandboxTreeVO root = buildTree(sandboxRoot, sandboxRoot.getFileName().toString(), "");
        return root.getChildren() == null ? List.of() : root.getChildren();
    }

    @Override
    public String readSandboxFile(Long projectId, String relativePath) {
        Path target = resolveSandbox(sandboxForProject(projectId), relativePath);
        if (!Files.isRegularFile(target)) {
            throw new ServiceException("文件不存在：" + relativePath);
        }
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServiceException("读取失败：" + e.getMessage());
        }
    }

    @Override
    public void applyFile(Long projectId, String relativePath) {
        DevProject project = requireCodeStage(projectId);
        Path sandbox = targetRootOf(project).resolve(OUTPUT_DIR).resolve(String.valueOf(projectId)).normalize();
        Path source = resolveSandbox(sandbox, relativePath);
        if (!Files.isRegularFile(source)) {
            throw new ServiceException("沙箱中不存在该文件：" + relativePath);
        }
        Path dest = targetRootOf(project).resolve(relativePath).normalize();
        copyFile(source, dest);
        log.info("[applyFile][任务 {} 已应用文件 {}]", projectId, relativePath);
    }

    @Override
    public void applyAllFiles(Long projectId) {
        DevProject project = requireCodeStage(projectId);
        Path sandbox = targetRootOf(project).resolve(OUTPUT_DIR).resolve(String.valueOf(projectId)).normalize();
        if (!Files.isDirectory(sandbox)) {
            throw new ServiceException("沙箱目录为空，无可应用的文件");
        }
        Path projectRoot = targetRootOf(project);
        int count = 0;
        try (Stream<Path> walk = Files.walk(sandbox)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String relative = sandbox.relativize(file).toString().replace('\\', '/');
                // 沙箱自身与 ai-dev-agent 的编译产物不应被应用
                if (isInternalFile(relative)) {
                    continue;
                }
                copyFile(file, projectRoot.resolve(relative).normalize());
                count++;
            }
        } catch (IOException e) {
            throw new ServiceException("应用文件失败：" + e.getMessage());
        }
        log.info("[applyAllFiles][任务 {} 已应用 {} 个文件到项目 {}]", projectId, count,
                project.getTargetProject());
    }

    @Override
    public void clearSandbox(Long projectId) {
        DevProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        if (DevStageEnum.CODE_FINALIZED.getStage().equals(project.getStage())) {
            throw new ServiceException("代码已定稿，不可清空");
        }
        Path sandbox = getSandboxPath(project);
        if (sandbox != null && Files.isDirectory(sandbox)) {
            deleteRecursively(sandbox);
            log.info("[clearSandbox][任务 {} 沙箱已清空]", projectId);
        }
    }

    @Override
    public void finalizeCode(Long projectId) {
        DevProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        if (project.getStage() == null || project.getStage() < DevStageEnum.CODE_GENERATING.getStage()) {
            throw new ServiceException("尚未生成代码，无法定稿");
        }
        if (DevStageEnum.CODE_FINALIZED.getStage().equals(project.getStage())) {
            return;
        }
        DevArtifact latest = artifactMapper.selectLatest(projectId, DevArtifactTypeEnum.CODE.getType());
        if (latest != null) {
            DevArtifact update = new DevArtifact();
            update.setId(latest.getId());
            update.setStatus(DevArtifactStatusEnum.FINALIZED.getStatus());
            update.setUpdater("admin");
            update.setUpdateTime(LocalDateTime.now());
            artifactMapper.updateById(update);
        }
        projectMapper.updateStage(projectId, DevStageEnum.CODE_FINALIZED.getStage());
        log.info("[finalizeCode][任务 {} 代码已定稿]", projectId);
    }

    @Override
    public int exportSandboxZip(Long projectId, java.io.OutputStream out) {
        Path sandbox = sandboxForProject(projectId);
        if (!Files.isDirectory(sandbox)) {
            throw new ServiceException("沙箱目录为空，无可导出的文件");
        }
        int count = 0;
        try (var zip = new java.util.zip.ZipOutputStream(out)) {
            // 逐个文件写入 zip，保留相对项目根目录的路径结构
            try (Stream<Path> walk = Files.walk(sandbox)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String relative = sandbox.relativize(file).toString().replace('\\', '/');
                    zip.putNextEntry(new java.util.zip.ZipEntry(relative));
                    byte[] bytes = Files.readAllBytes(file);
                    zip.write(bytes);
                    zip.closeEntry();
                    count++;
                }
            }
            log.info("[exportSandboxZip][任务 {} 导出 {} 个文件]", projectId, count);
        } catch (IOException e) {
            throw new ServiceException("导出失败：" + e.getMessage());
        }
        return count;
    }

    /**
     * 校验任务处于可应用/可查看代码的阶段（至少已开始代码生成），并返回项目实体
     */
    private DevProject requireCodeStage(Long projectId) {
        DevProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        if (project.getStage() == null || project.getStage() < DevStageEnum.CODE_GENERATING.getStage()) {
            throw new ServiceException("尚未生成代码");
        }
        if (project.getTargetProject() == null || project.getTargetProject().isBlank()) {
            throw new ServiceException("任务未绑定目标项目，请先重新生成代码");
        }
        return project;
    }

    private Path targetRootOf(DevProject project) {
        AgentProperties.TargetProject target = targetProjectRegistry.resolve(project.getTargetProject());
        return Paths.get(target.getPath());
    }

    /**
     * 目标项目路径不存在或未绑定时返回 null（用于清空时的安全判断）
     */
    private Path getSandboxPath(DevProject project) {
        if (project.getTargetProject() == null || project.getTargetProject().isBlank()) {
            return null;
        }
        return sandboxPath(targetProjectRegistry.resolve(project.getTargetProject()).getPath(), project.getId());
    }

    /**
     * 按任务解析沙箱目录：需任务已绑定目标项目
     */
    private Path sandboxForProject(Long projectId) {
        DevProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        if (project.getTargetProject() == null || project.getTargetProject().isBlank()) {
            return Paths.get("ai-output", String.valueOf(projectId)).normalize();
        }
        return sandboxPath(targetProjectRegistry.resolve(project.getTargetProject()).getPath(), projectId);
    }

    /**
     * 计算沙箱根目录：目标项目根目录 / ai-output / {projectId}
     */
    private static Path sandboxPath(String targetProjectPath, Long projectId) {
        return Paths.get(targetProjectPath, OUTPUT_DIR, String.valueOf(projectId)).normalize();
    }

    /**
     * 解析沙箱内相对路径，防目录穿越
     */
    private static Path resolveSandbox(Path sandboxRoot, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ServiceException("文件路径不能为空");
        }
        String clean = relativePath.trim().replaceFirst("^[/\\\\]+", "");
        Path resolved = sandboxRoot.resolve(clean).normalize();
        if (!resolved.startsWith(sandboxRoot.normalize())) {
            throw new ServiceException("路径越界：" + relativePath);
        }
        return resolved;
    }

    /**
     * 复制单个文件到目标位置（自动创建父目录）
     */
    private static void copyFile(Path source, Path dest) {
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceException("复制文件失败 " + dest + "：" + e.getMessage());
        }
    }

    /**
     * 是否属于不应应用到目标项目的内部文件（沙箱进程产生的辅助文件）
     */
    private static boolean isInternalFile(String relativePath) {
        return relativePath.equals("README.md")
                || relativePath.endsWith("/README.md")
                || relativePath.endsWith("/.gitkeep");
    }

    /**
     * 递归删除目录
     */
    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            // 先删子文件再删目录
            for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            log.warn("[deleteRecursively][删除目录失败 {}：{}]", dir, e.getMessage());
        }
    }

    /**
     * 递归构建文件树
     */
    private CodeSandboxTreeVO buildTree(Path dir, String name, String rel) {
        List<CodeSandboxTreeVO> children = new ArrayList<>();
        try (Stream<Path> list = Files.list(dir)) {
            List<Path> entries = list.sorted().toList();
            for (Path entry : entries) {
                String childName = entry.getFileName().toString();
                String childRel = rel.isEmpty() ? childName : rel + "/" + childName;
                if (Files.isDirectory(entry)) {
                    children.add(buildTree(entry, childName, childRel));
                } else {
                    long size = safeSize(entry);
                    children.add(new CodeSandboxTreeVO(childName, childRel, false, size, null));
                }
            }
        } catch (IOException e) {
            log.warn("[buildTree][读取目录失败 {}：{}]", dir, e.getMessage());
        }
        return new CodeSandboxTreeVO(name, rel, true, 0L, children);
    }

    private static long safeSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * 生成结束后保存代码产物清单（用于版本化记录）
     */
    private void saveCodeManifest(Long projectId, Path sandboxRoot, String targetName) {
        List<CodeSandboxTreeVO> tree = listSandboxFiles(projectId);
        StringBuilder sb = new StringBuilder("# 代码生成清单\n\n");
        sb.append("- 目标项目：").append(targetName).append("\n");
        sb.append("- 生成时间：").append(LocalDateTime.now()).append("\n");
        sb.append("- 文件总数：").append(countFiles(tree)).append("\n\n");
        if (!tree.isEmpty()) {
            sb.append("```\n");
            appendTreeText(tree, sb, "");
            sb.append("```\n");
        }
        insertCodeArtifact(projectId, sb.toString());
    }

    private int countFiles(List<CodeSandboxTreeVO> nodes) {
        int count = 0;
        for (CodeSandboxTreeVO n : nodes) {
            if (Boolean.TRUE.equals(n.getIsDir())) {
                count += countFiles(n.getChildren() == null ? List.of() : n.getChildren());
            } else {
                count++;
            }
        }
        return count;
    }

    private void appendTreeText(List<CodeSandboxTreeVO> nodes, StringBuilder sb, String prefix) {
        for (CodeSandboxTreeVO n : nodes) {
            sb.append(prefix).append(n.getName());
            if (Boolean.TRUE.equals(n.getIsDir())) {
                sb.append("/\n");
                appendTreeText(n.getChildren() == null ? List.of() : n.getChildren(), sb, prefix + "  ");
            } else {
                sb.append("\n");
            }
        }
    }

    private void insertCodeArtifact(Long projectId, String content) {
        DevArtifact latest = artifactMapper.selectLatest(projectId, DevArtifactTypeEnum.CODE.getType());
        DevArtifact artifact = new DevArtifact();
        artifact.setProjectId(projectId);
        artifact.setType(DevArtifactTypeEnum.CODE.getType());
        artifact.setVersion(latest != null ? latest.getVersion() + 1 : 1);
        artifact.setStatus(DevArtifactStatusEnum.AI_GENERATED.getStatus());
        artifact.setContent(content);
        artifact.setRemark("");
        artifact.setCreator("admin");
        artifact.setCreateTime(LocalDateTime.now());
        artifactMapper.insert(artifact);
        log.info("[saveCodeManifest][任务 {} 代码产物 v{} 已保存]", projectId, artifact.getVersion());
    }

    /**
     * 组装提示词上下文：设计文档全文 + 目标项目信息（含框架画像）
     */
    private String buildContext(DevProject project, DevArtifact design, AgentProperties.TargetProject target) {
        String profile = targetProjectRegistry.loadFrameworkProfile(target);
        StringBuilder sb = new StringBuilder();
        sb.append("【目标任务】").append(project.getName()).append("\n");
        sb.append("【目标项目】").append(target.getName())
                .append("（根目录：").append(target.getPath()).append("）\n");
        if (profile.isBlank()) {
            sb.append("该项目未提供框架画像：请先用读码工具自主探索项目结构、分层规范与代码风格后再生成。\n");
        } else {
            sb.append("【该项目框架画像（必须遵循）】\n").append(profile.trim()).append("\n");
        }
        sb.append("\n【设计文档 v").append(design.getVersion()).append("】\n").append(design.getContent());
        return sb.toString();
    }

}