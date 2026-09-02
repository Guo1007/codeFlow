package com.aidev.agent.service;

import com.aidev.agent.controller.vo.ProjectCreateReqVO;
import com.aidev.agent.controller.vo.ProjectRespVO;
import com.aidev.agent.controller.vo.ProjectSimpleVO;

import java.util.List;

/**
 * AI 开发任务 Service
 */
public interface DevProjectService {

    /**
     * 创建开发任务（录入需求文档）
     *
     * @param createReqVO 创建信息
     * @return 任务编号
     */
    Long createProject(ProjectCreateReqVO createReqVO);

    /**
     * 获取任务详情（含最新设计文档）
     *
     * @param id 任务编号
     * @return 任务详情
     */
    ProjectRespVO getProjectDetail(Long id);

    /**
     * 历史任务列表（按创建时间倒序，最新在前，最多 50 条）
     *
     * @return 任务摘要列表（不含需求文档大字段）
     */
    List<ProjectSimpleVO> listProjects();

}
