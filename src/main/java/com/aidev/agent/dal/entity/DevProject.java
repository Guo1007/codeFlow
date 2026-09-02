package com.aidev.agent.dal.entity;

import com.aidev.agent.enums.DevStageEnum;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 开发任务实体（无 BaseDO，自带基础字段）
 * <p>
 * 一次 AI 辅助开发流水线任务：录入需求文档 → 人机协同产出设计文档 → 生成代码 → 生成使用说明，
 * 各阶段产物见 {@link DevArtifact}
 */
@Data
@TableName("dev_project")
public class DevProject {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 需求文档内容
     * <p>
     * 枚举 {@link DevStageEnum}
     */
    private String requirementContent;

    /**
     * 当前阶段
     */
    private Integer stage;

    /**
     * 代码生成阶段所绑定的目标项目名称（对应用户在 targetProjectRegistry 中选择的项目，
     * 生成代码时写入其 ai-output/{projectId} 沙箱，应用时复制回该项目根目录）
     */
    private String targetProject;

    /**
     * 创建者
     */
    private String creator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    private String updater;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    private Boolean deleted;

}
