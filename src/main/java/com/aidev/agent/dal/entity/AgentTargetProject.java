package com.aidev.agent.dal.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 目标项目注册表实体（对话中动态接入的项目，与 yaml 静态配置合并）
 */
@Data
@TableName("agent_target_project")
public class AgentTargetProject {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 项目标识（唯一）
     */
    private String name;

    /**
     * 项目根目录
     */
    private String path;

    /**
     * 框架画像文件路径（可空 = Agent 自主探索）
     */
    private String frameworkProfile;

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
