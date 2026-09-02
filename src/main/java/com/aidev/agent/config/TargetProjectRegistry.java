package com.aidev.agent.config;

import com.aidev.agent.common.ServiceException;
import com.aidev.agent.dal.entity.AgentTargetProject;
import com.aidev.agent.dal.mapper.AgentTargetProjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标项目注册表。
 * <p>
 * 项目来源有两种，运行时合并：
 * 1. 静态配置：application.yaml 的 agent.targets（改配置重启生效）
 * 2. 动态注册：对话中 AI 调用接入工具写入 agent_target_project 表（立即生效，重启不丢，名称冲突时以 yaml 为准）
 * </p>
 * <p>
 * 提供按名称/默认取项目、框架画像文本加载（带缓存）、运行时注册。
 * </p>
 */
@Slf4j
@Component
public class TargetProjectRegistry {

    private static final String CLASSPATH_PREFIX = "classpath:";

    private final AgentTargetProjectMapper targetProjectMapper;

    /**
     * 全部目标项目（yaml 静态 + DB 动态，启动时初始化，注册时追加），第一个为默认项目
     */
    private final List<AgentProperties.TargetProject> targets = new ArrayList<>();

    private final Map<String, AgentProperties.TargetProject> byName = new ConcurrentHashMap<>();

    /**
     * 框架画像文本缓存（项目名 -> 画像内容，空串表示无画像）
     */
    private final Map<String, String> profileCache = new ConcurrentHashMap<>();

    public TargetProjectRegistry(AgentProperties properties, AgentTargetProjectMapper targetProjectMapper) {
        this.targetProjectMapper = targetProjectMapper;
        // 1. 静态配置（yaml）
        properties.getTargets().forEach(this::validateAndAdd);
        if (targets.isEmpty()) {
            log.warn("[TargetProjectRegistry][yaml 中未注册任何目标项目]");
        }
        // 2. 动态注册（DB），名称与 yaml 冲突时以 yaml 为准
        loadFromDatabase();
        if (targets.isEmpty()) {
            log.warn("[TargetProjectRegistry][未注册任何目标项目：yaml 与数据库均为空]");
        }
    }

    /**
     * 全部目标项目（注册顺序，第一个为默认）
     */
    public List<AgentProperties.TargetProject> list() {
        synchronized (targets) {
            return List.copyOf(targets);
        }
    }

    /**
     * 按名称取项目；name 为空时取默认（第一个）；名称不存在时抛业务异常
     */
    public AgentProperties.TargetProject resolve(String name) {
        if (targets.isEmpty()) {
            throw new ServiceException("未注册任何目标项目，请在 application.yaml 的 agent.targets 中配置，或对话中让 AI 接入");
        }
        if (name == null || name.isBlank()) {
            return list().get(0);
        }
        AgentProperties.TargetProject target = byName.get(name);
        if (target == null) {
            throw new ServiceException("目标项目不存在：" + name);
        }
        return target;
    }

    /**
     * 运行时注册新目标项目（对话中 AI 调用接入工具触发）：
     * 校验名称唯一、路径为存在的目录后落库并加入内存注册表，立即生效
     *
     * @param name 项目标识
     * @param path 项目根目录（绝对路径）
     * @return 注册成功后的项目
     */
    public AgentProperties.TargetProject register(String name, String path) {
        if (name == null || name.isBlank()) {
            throw new ServiceException("项目名称不能为空");
        }
        name = name.trim();
        if (path == null || path.isBlank()) {
            throw new ServiceException("项目根目录不能为空");
        }
        path = path.trim();
        if (byName.containsKey(name)) {
            throw new ServiceException("项目名称已存在：" + name);
        }
        if (!Files.isDirectory(Path.of(path))) {
            throw new ServiceException("项目根目录不存在或不是目录：" + path);
        }
        // 落库（动态项目无框架画像，靠 Agent 读码自主探索）
        AgentTargetProject entity = new AgentTargetProject();
        entity.setName(name);
        entity.setPath(path);
        entity.setFrameworkProfile("");
        entity.setCreator("admin");
        entity.setCreateTime(LocalDateTime.now());
        targetProjectMapper.insert(entity);
        // 加入内存注册表，立即生效
        AgentProperties.TargetProject target = new AgentProperties.TargetProject();
        target.setName(name);
        target.setPath(path);
        target.setFrameworkProfile("");
        synchronized (targets) {
            targets.add(target);
        }
        byName.put(name, target);
        log.info("[register][动态接入目标项目 {}，根目录 {}]", name, path);
        return target;
    }

    /**
     * 启动时从 DB 加载动态注册的项目
     */
    private void loadFromDatabase() {
        try {
            List<AgentTargetProject> rows = targetProjectMapper.selectList(null);
            for (AgentTargetProject row : rows) {
                if (byName.containsKey(row.getName())) {
                    log.warn("[loadFromDatabase][DB 项目 {} 与 yaml 名称冲突，以 yaml 为准]", row.getName());
                    continue;
                }
                AgentProperties.TargetProject target = new AgentProperties.TargetProject();
                target.setName(row.getName());
                target.setPath(row.getPath());
                target.setFrameworkProfile(row.getFrameworkProfile());
                synchronized (targets) {
                    targets.add(target);
                }
                byName.put(target.getName(), target);
            }
        } catch (Exception e) {
            // 表未建等场景不阻塞启动（yaml 项目仍可用）
            log.warn("[loadFromDatabase][动态项目加载失败：{}]", e.getMessage());
        }
    }

    /**
     * 校验并加入静态配置项目
     */
    private void validateAndAdd(AgentProperties.TargetProject target) {
        if (target.getName() == null || target.getName().isBlank()
                || target.getPath() == null || target.getPath().isBlank()) {
            throw new IllegalStateException("agent.targets 每个项目必须配置 name 与 path");
        }
        if (byName.putIfAbsent(target.getName(), target) != null) {
            throw new IllegalStateException("agent.targets 中存在重复的项目名称：" + target.getName());
        }
        if (!Files.isDirectory(Path.of(target.getPath()))) {
            log.warn("[TargetProjectRegistry][项目 {} 的路径不存在：{}]", target.getName(), target.getPath());
        }
        targets.add(target);
    }

    /**
     * 加载项目的框架画像文本（带缓存）；未配置或读取失败时返回空串
     */
    public String loadFrameworkProfile(AgentProperties.TargetProject target) {
        return profileCache.computeIfAbsent(target.getName(), name -> {
            String location = target.getFrameworkProfile();
            if (location == null || location.isBlank()) {
                return "";
            }
            try {
                if (location.startsWith(CLASSPATH_PREFIX)) {
                    return new ClassPathResource(location.substring(CLASSPATH_PREFIX.length()))
                            .getContentAsString(StandardCharsets.UTF_8);
                }
                return Files.readString(Path.of(location), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("[loadFrameworkProfile][项目 {} 画像加载失败：{}]", name, e.getMessage());
                return "";
            }
        });
    }

}
