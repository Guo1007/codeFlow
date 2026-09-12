package com.aidev.agent.service.impl;

import com.aidev.agent.aiservice.DevDocAiService;
import com.aidev.agent.common.ServiceException;
import com.aidev.agent.controller.vo.DesignApproveReqVO;
import com.aidev.agent.dal.entity.DevArtifact;
import com.aidev.agent.dal.entity.DevProject;
import com.aidev.agent.dal.mapper.DevArtifactMapper;
import com.aidev.agent.dal.mapper.DevProjectMapper;
import com.aidev.agent.enums.DevArtifactStatusEnum;
import com.aidev.agent.enums.DevArtifactTypeEnum;
import com.aidev.agent.enums.DevStageEnum;
import com.aidev.agent.service.DevDesignService;
import com.aidev.agent.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 开发设计文档 Service 实现类
 * <p>
 * 状态机：任务处于"需求已录入/设计评审中"时可生成或修订设计文档；
 * 流式输出结束后服务端自动将全文保存为产物新版本；人工定稿后进入"设计已定稿"，锁死后续变更。
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DevDesignServiceImpl implements DevDesignService {

    private final DevDocAiService devDocAiService;

    private final DevProjectMapper projectMapper;

    private final DevArtifactMapper artifactMapper;

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 生成中的任务集合（projectId -> true），防止同一任务并发发起生成
     */
    private final ConcurrentHashMap<Long, Boolean> generatingProjects = new ConcurrentHashMap<>();

    @Override
    public Flux<String> streamGenerateDesign(Long projectId) {
        DevProject project = validateProjectForGenerate(projectId);
        // 输出上下文：需求文档全文（阶段提示词已约束输出格式）
        String context = "【需求文档】\n" + project.getRequirementContent();
        return doStreamAndSave(devDocAiService.generateDesignDoc(context), projectId, null);
    }

    @Override
    public Flux<String> streamReviseDesign(Long projectId, String opinion) {
        if (opinion == null || opinion.trim().isEmpty()) {
            throw new ServiceException("评审意见不能为空");
        }
        DevProject project = validateProjectForGenerate(projectId);
        DevArtifact latest = artifactMapper.selectLatestDesign(projectId);
        if (latest == null) {
            throw new ServiceException("设计文档尚未生成，无法修订");
        }
        // 输出上下文：当前设计文档全文 + 评审意见（阶段提示词已约束输出修订版全文）
        String context = "【当前设计文档（版本 v" + latest.getVersion() + "）】\n" + latest.getContent()
                + "\n\n【评审意见】\n" + opinion.trim();
        return doStreamAndSave(devDocAiService.reviseDesignDoc(context), projectId, opinion.trim());
    }

    @Override
    public void saveHumanEditedDesign(Long projectId, String content) {
        validateProjectForGenerate(projectId);
        DevArtifact latest = artifactMapper.selectLatestDesign(projectId);
        if (latest == null) {
            throw new ServiceException("设计文档尚未生成，无法编辑");
        }
        insertArtifact(projectId, content, DevArtifactStatusEnum.HUMAN_MODIFIED, "人工编辑");
        // 人工修改说明评审仍在进行中
        projectMapper.updateStage(projectId, DevStageEnum.DESIGN_REVIEWING.getStage());
    }

    @Override
    public void approveDesign(DesignApproveReqVO reqVO) {
        DevProject project = projectMapper.selectById(reqVO.getProjectId());
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        DevArtifact latest = artifactMapper.selectLatestDesign(reqVO.getProjectId());
        if (latest == null) {
            throw new ServiceException("设计文档尚未生成，无法定稿");
        }
        // 定稿可携带最后的全文修改：有内容则以人工修改版本落库后定稿，否则直接定稿当前版本
        Long projectId = reqVO.getProjectId();
        if (reqVO.getContent() != null && !reqVO.getContent().trim().isEmpty()
                && !reqVO.getContent().equals(latest.getContent())) {
            latest = insertArtifact(projectId, reqVO.getContent(), DevArtifactStatusEnum.HUMAN_MODIFIED, "定稿时的人工修改");
        }
        DevArtifact update = new DevArtifact();
        update.setId(latest.getId());
        update.setStatus(DevArtifactStatusEnum.FINALIZED.getStatus());
        update.setUpdater("admin");
        update.setUpdateTime(LocalDateTime.now());
        artifactMapper.updateById(update);
        projectMapper.updateStage(projectId, DevStageEnum.DESIGN_FINALIZED.getStage());
        // 文档定稿时自动向量化入库，供知识库检索
        try {
            knowledgeBaseService.indexDesign(project.getTargetProject(), latest.getContent(),
                    latest.getVersion(), project.getCreator());
        } catch (Exception e) {
            log.warn("[approveDesign][任务 {} 设计文档定稿后的知识库索引失败] {}", projectId, e.getMessage());
        }
        log.info("[approveDesign][任务 {} 设计文档已定稿，版本 v{}]", projectId, latest.getVersion());
    }

    /**
     * 执行流式生成并在流结束后保存产物、推进阶段
     *
     * @param contentFlux 模型输出的内容流
     * @param projectId   任务编号
     * @param remark      版本备注（评审意见），首次生成时为 null
     */
    private Flux<String> doStreamAndSave(Flux<String> contentFlux, Long projectId, String remark) {
        StringBuilder content = new StringBuilder();
        // 占位标记，防止同一任务的生成并发发起（订阅时才开始真正执行）
        if (generatingProjects.putIfAbsent(projectId, Boolean.TRUE) != null) {
            throw new ServiceException("该任务正在生成中，请勿重复发起");
        }
        return Flux.defer(() -> contentFlux
                .doOnNext(content::append)
                .doOnError(e -> {
                    generatingProjects.remove(projectId);
                    log.error("[doStreamAndSave][任务 {} 设计文档生成失败]", projectId, e);
                })
                // 客户端断开等取消场景同样要释放标记，否则任务会被永久锁死
                .doOnCancel(() -> {
                    generatingProjects.remove(projectId);
                    log.warn("[doStreamAndSave][任务 {} 设计文档生成被取消，已生成内容不保存]", projectId);
                })
                .doOnComplete(() -> {
                    generatingProjects.remove(projectId);
                    // 全文落库为新版本，任务进入"设计评审中"，等待人工评审
                    insertArtifact(projectId, content.toString(), DevArtifactStatusEnum.AI_GENERATED, remark);
                    projectMapper.updateStage(projectId, DevStageEnum.DESIGN_REVIEWING.getStage());
                }));
    }

    /**
     * 校验任务可发起生成/修订：存在、且设计未定稿
     */
    private DevProject validateProjectForGenerate(Long projectId) {
        DevProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        if (DevStageEnum.DESIGN_FINALIZED.getStage().equals(project.getStage())
                || project.getStage() > DevStageEnum.DESIGN_FINALIZED.getStage()) {
            throw new ServiceException("设计文档已定稿，不可再修改");
        }
        return project;
    }

    /**
     * 新增一个产物版本，版本号 = 当前最新版本 + 1
     */
    private DevArtifact insertArtifact(Long projectId, String content,
                                       DevArtifactStatusEnum status, String remark) {
        DevArtifact latest = artifactMapper.selectLatestDesign(projectId);
        DevArtifact artifact = new DevArtifact();
        artifact.setProjectId(projectId);
        artifact.setType(DevArtifactTypeEnum.DESIGN_DOC.getType());
        artifact.setVersion(latest != null ? latest.getVersion() + 1 : 1);
        artifact.setStatus(status.getStatus());
        artifact.setContent(content);
        artifact.setRemark(remark != null ? remark : "");
        artifact.setCreator("admin");
        artifact.setCreateTime(LocalDateTime.now());
        artifactMapper.insert(artifact);
        log.info("[insertArtifact][任务 {} 设计文档保存新版本 v{}，状态 {}]", projectId, artifact.getVersion(), status);
        return artifact;
    }

}
