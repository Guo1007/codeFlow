package com.aidev.agent.service.impl;

import com.aidev.agent.common.ServiceException;
import com.aidev.agent.controller.vo.ProjectCreateReqVO;
import com.aidev.agent.controller.vo.ProjectRespVO;
import com.aidev.agent.controller.vo.ProjectSimpleVO;
import com.aidev.agent.dal.entity.DevArtifact;
import com.aidev.agent.dal.entity.DevProject;
import com.aidev.agent.dal.mapper.DevArtifactMapper;
import com.aidev.agent.dal.mapper.DevProjectMapper;
import com.aidev.agent.enums.DevArtifactTypeEnum;
import com.aidev.agent.enums.DevStageEnum;
import com.aidev.agent.service.DevProjectService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 开发任务 Service 实现类
 */
@Service
@Validated
@RequiredArgsConstructor
public class DevProjectServiceImpl implements DevProjectService {

    private final DevProjectMapper projectMapper;

    private final DevArtifactMapper artifactMapper;

    @Override
    public Long createProject(ProjectCreateReqVO createReqVO) {
        if (createReqVO.getRequirementContent() == null || createReqVO.getRequirementContent().trim().isEmpty()) {
            throw new ServiceException("需求文档内容不能为空");
        }
        DevProject project = new DevProject();
        project.setName(createReqVO.getName());
        project.setRequirementContent(createReqVO.getRequirementContent());
        project.setStage(DevStageEnum.REQUIREMENT.getStage());
        project.setCreator("admin"); // 登录暂缓，固定值，后续接入用户体系时替换
        project.setCreateTime(LocalDateTime.now());
        projectMapper.insert(project);
        return project.getId();
    }

    @Override
    public ProjectRespVO getProjectDetail(Long id) {
        DevProject project = projectMapper.selectById(id);
        if (project == null) {
            throw new ServiceException("开发任务不存在");
        }
        ProjectRespVO respVO = new ProjectRespVO();
        respVO.setId(project.getId());
        respVO.setName(project.getName());
        respVO.setRequirementContent(project.getRequirementContent());
        respVO.setStage(project.getStage());
        respVO.setTargetProject(project.getTargetProject());
        respVO.setCreateTime(project.getCreateTime());
        // 附带最新版本的设计文档，供工作台直接展示
        DevArtifact design = artifactMapper.selectLatestDesign(id);
        if (design != null) {
            respVO.setDesignContent(design.getContent());
            respVO.setDesignVersion(design.getVersion());
            respVO.setDesignStatus(design.getStatus());
        }
        // 附带最新代码产物版本号，供工作台展示代码生成状态
        DevArtifact code = artifactMapper.selectLatest(id, DevArtifactTypeEnum.CODE.getType());
        if (code != null) {
            respVO.setCodeVersion(code.getVersion());
        }
        return respVO;
    }

    @Override
    public List<ProjectSimpleVO> listProjects() {
        // 摘要列表：只查名称/阶段/时间，不取需求文档大字段
        List<DevProject> projects = projectMapper.selectList(new LambdaQueryWrapper<DevProject>()
                .select(DevProject::getId, DevProject::getName, DevProject::getStage, DevProject::getCreateTime)
                .orderByDesc(DevProject::getId)
                .last("LIMIT 50"));
        return projects.stream().map(project -> {
            ProjectSimpleVO vo = new ProjectSimpleVO();
            vo.setId(project.getId());
            vo.setName(project.getName());
            vo.setStage(project.getStage());
            vo.setCreateTime(project.getCreateTime());
            return vo;
        }).toList();
    }

}
