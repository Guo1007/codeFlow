package com.aidev.agent.service;

import com.aidev.agent.controller.vo.DesignApproveReqVO;
import reactor.core.publisher.Flux;

/**
 * AI 开发设计文档 Service
 * <p>
 * 状态机：任务处于"需求已录入/设计评审中"时可生成或修订设计文档；
 * 流式输出结束后服务端自动将全文保存为产物新版本；人工定稿后进入"设计已定稿"，锁死后续变更。
 * </p>
 */
public interface DevDesignService {

    /**
     * 根据需求文档流式生成设计文档（结束后自动保存版本）
     *
     * @param projectId 任务编号
     * @return 设计文档的内容流
     */
    Flux<String> streamGenerateDesign(Long projectId);

    /**
     * 根据评审意见流式修订设计文档（结束后自动保存版本）
     *
     * @param projectId 任务编号
     * @param opinion   评审意见
     * @return 修订后设计文档的内容流
     */
    Flux<String> streamReviseDesign(Long projectId, String opinion);

    /**
     * 保存人工编辑的设计文档（落为人工修改版本）
     *
     * @param projectId 任务编号
     * @param content   编辑后的全文
     */
    void saveHumanEditedDesign(Long projectId, String content);

    /**
     * 定稿设计文档（设计阶段的人工卡点，定稿后不可再修改）
     *
     * @param reqVO 定稿请求
     */
    void approveDesign(DesignApproveReqVO reqVO);

}
