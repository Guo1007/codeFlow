package com.aidev.agent.service.impl;

import com.aidev.agent.aiservice.UsageManualAiService;
import com.aidev.agent.common.ServiceException;
import com.aidev.agent.config.AgentProperties;
import com.aidev.agent.config.TargetProjectRegistry;
import com.aidev.agent.dal.entity.DevArtifact;
import com.aidev.agent.dal.entity.DevProject;
import com.aidev.agent.dal.mapper.DevArtifactMapper;
import com.aidev.agent.dal.mapper.DevProjectMapper;
import com.aidev.agent.enums.DevArtifactStatusEnum;
import com.aidev.agent.enums.DevArtifactTypeEnum;
import com.aidev.agent.enums.DevStageEnum;
import com.aidev.agent.service.DevUsageService;
import com.aidev.agent.tools.ProjectCodeTools;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 使用说明生成 Service 实现类。
 * <p>
 * 代码定稿（阶段 4）后，基于定稿设计文档 + 目标项目真实代码，流式生成本需求/模块的
 * 使用说明，结束后自动落库为 MANUAL 产物并将阶段推进到「使用说明已生成」(5)（流水线终态）。
 * </p>
 */
@Service
@Slf4j
public class DevUsageServiceImpl implements DevUsageService {

    private final DevProjectMapper projectMapper;

    private final DevArtifactMapper artifactMapper;

    private final TargetProjectRegistry targetProjectRegistry;

    private final StreamingChatModel devDocStreamingModel;

    /**
     * 生成中的任务集合（projectId -> true），防止同一任务并发发起
     */
    private final ConcurrentHashMap<Long, Boolean> generatingProjects = new ConcurrentHashMap<>();

    public DevUsageServiceImpl(DevProjectMapper projectMapper,
                               DevArtifactMapper artifactMapper,
                               TargetProjectRegistry targetProjectRegistry,
                               @Qualifier("devDocStreamingModel") StreamingChatModel devDocStreamingModel) {
        this.projectMapper = projectMapper;
        this.artifactMapper = artifactMapper;
        this.targetProjectRegistry = targetProjectRegistry;
        this.devDocStreamingModel = devDocStreamingModel;
    }

    @Override
    public Flux<String> streamGenerateManual(Long projectId) {
        DevProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        if (project.getStage() == null || project.getStage() < DevStageEnum.CODE_FINALIZED.getStage()) {
            throw new ServiceException("请先完成设计文档，并生成、应用到项目且代码定稿后，再生成使用说明");
        }
        if (project.getTargetProject() == null || project.getTargetProject().isBlank()) {
            throw new ServiceException("该任务未绑定目标项目，无法基于真实代码生成使用说明，请重新生成代码");
        }
        DevArtifact design = artifactMapper.selectLatestDesign(projectId);
        if (design == null) {
            throw new ServiceException("设计文档缺失，无法生成使用说明");
        }
        if (generatingProjects.putIfAbsent(projectId, Boolean.TRUE) != null) {
            throw new ServiceException("该任务正在生成使用说明中，请勿重复发起");
        }

        AgentProperties.TargetProject target = targetProjectRegistry.resolve(project.getTargetProject());
        // 按任务动态构建：绑定目标项目读码工具，使说明贴合真实落盘代码
        UsageManualAiService ai = AiServices.builder(UsageManualAiService.class)
                .streamingChatModel(devDocStreamingModel)
                .tools(new ProjectCodeTools(target.getPath()))
                .build();
        String context = buildContext(project, design, target);
        // 逐片收集 AI 输出，流结束后以完整 Markdown 落库（同设计文档的模式）
        StringBuilder content = new StringBuilder();

        return Flux.defer(() -> ai.generateManual(context)
                .doOnNext(content::append)
                .doOnError(e -> {
                    generatingProjects.remove(projectId);
                    log.error("[streamGenerateManual][任务 {} 使用说明生成失败]", projectId, e);
                })
                .doOnCancel(() -> {
                    generatingProjects.remove(projectId);
                    log.warn("[streamGenerateManual][任务 {} 使用说明生成被取消，已生成内容不保存]", projectId);
                })
                .doOnComplete(() -> {
                    generatingProjects.remove(projectId);
                    saveManualArtifact(projectId, design.getVersion(), content.toString());
                    projectMapper.updateStage(projectId, DevStageEnum.MANUAL_GENERATED.getStage());
                    log.info("[streamGenerateManual][任务 {} 使用说明已生成并定稿，流水线完成]", projectId);
                }));
    }

    /**
     * 组装提示词上下文：目标任务 + 目标项目信息（含画像）+ 定稿设计文档
     */
    private String buildContext(DevProject project, DevArtifact design, AgentProperties.TargetProject target) {
        String profile = targetProjectRegistry.loadFrameworkProfile(target);
        StringBuilder sb = new StringBuilder();
        sb.append("【目标任务】").append(project.getName()).append("\n");
        sb.append("【目标项目】").append(target.getName())
                .append("（根目录：").append(target.getPath()).append("）\n");
        if (profile.isBlank()) {
            sb.append("该项目未提供框架画像：请先用读码工具自主探索项目实际结构与已生成代码。\n");
        } else {
            sb.append("【该项目框架画像（必须遵循）】\n").append(profile.trim()).append("\n");
        }
        sb.append("\n【定稿设计文档 v").append(design.getVersion()).append("】\n").append(design.getContent());
        return sb.toString();
    }

    /**
     * 落库为 MANUAL 产物新版本（生成即定稿，保留历史版本）
     *
     * @param content AI 生成的完整使用说明 Markdown 内容
     */
    private void saveManualArtifact(Long projectId, Integer designVersion, String content) {
        DevArtifact latest = artifactMapper.selectLatest(projectId, DevArtifactTypeEnum.MANUAL.getType());
        DevArtifact artifact = new DevArtifact();
        artifact.setProjectId(projectId);
        artifact.setType(DevArtifactTypeEnum.MANUAL.getType());
        artifact.setVersion(latest != null ? latest.getVersion() + 1 : 1);
        artifact.setStatus(DevArtifactStatusEnum.FINALIZED.getStatus());
        artifact.setContent(content);
        artifact.setRemark("基于设计文档 v" + designVersion + " 与目标项目代码生成");
        artifact.setCreator("admin");
        artifact.setCreateTime(LocalDateTime.now());
        artifactMapper.insert(artifact);
        log.info("[saveManualArtifact][任务 {} 使用说明产物 v{} 已保存]", projectId, artifact.getVersion());
    }

}