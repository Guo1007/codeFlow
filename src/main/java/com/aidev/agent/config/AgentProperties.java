package com.aidev.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 配置项（agent.*）
 * <p>
 * 目标项目画像层：在 targets 中注册任意多个目标项目（换项目/加项目只改配置，不改代码），
 * 每个目标项目可携带一份框架画像（技术栈、分层规范、命名习惯），
 * 对话时动态注入系统提示词；画像为空时 Agent 用读码工具自主探索推断规范。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /**
     * 目标项目注册表（至少注册一个，第一个为默认项目）
     */
    private List<TargetProject> targets = new ArrayList<>();

    /**
     * 文档级生成配置
     */
    private Doc doc = new Doc();

    @Data
    public static class TargetProject {

        /**
         * 项目标识（唯一，用于前端选择器展示与会话记忆隔离）
         */
        private String name;

        /**
         * 项目根目录（读码工具的工作范围）
         */
        private String path;

        /**
         * 框架画像文件路径（classpath: 前缀或绝对路径）
         * 为空时提示 Agent 用读码工具自主探索目标项目再推断规范
         */
        private String frameworkProfile;

    }

    @Data
    public static class Doc {

        /**
         * 文档级生成的超时倍数（长文档生成慢）
         */
        private Integer timeoutMultiplier = 5;

    }

}
