package com.aidev.agent.controller;

import com.aidev.agent.common.ApiResponse;
import com.aidev.agent.controller.vo.ProjectCreateReqVO;
import com.aidev.agent.controller.vo.ProjectRespVO;
import com.aidev.agent.controller.vo.ProjectSimpleVO;
import com.aidev.agent.service.DevProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 开发任务控制器
 */
@RestController
@RequestMapping("/dev/project")
@Validated
@RequiredArgsConstructor
public class DevProjectController {

    private final DevProjectService projectService;

    /**
     * 创建开发任务（录入需求文档）
     */
    @PostMapping("/create")
    public ApiResponse<Long> createProject(@Valid @RequestBody ProjectCreateReqVO createReqVO) {
        return ApiResponse.success(projectService.createProject(createReqVO));
    }

    /**
     * 获取开发任务详情（含最新设计文档）
     */
    @GetMapping("/get")
    public ApiResponse<ProjectRespVO> getProject(@RequestParam("id") Long id) {
        return ApiResponse.success(projectService.getProjectDetail(id));
    }

    /**
     * 历史任务列表（按创建时间倒序，最多 50 条，一键载入用）
     */
    @GetMapping("/list")
    public ApiResponse<List<ProjectSimpleVO>> listProjects() {
        return ApiResponse.success(projectService.listProjects());
    }

}
