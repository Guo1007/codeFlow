package com.aidev.agent.tools;

import com.aidev.agent.config.TargetProjectRegistry;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 项目接入工具。
 * <p>
 * 全局单例（不绑定具体项目），挂到每个项目的对话服务上：
 * 用户在对话中提出接入新项目时，AI 调用本工具完成注册（校验 + 落库 + 内存生效），
 * 之后用户在前端顶部切换到新项目即可让 AI 阅读其代码。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectRegistrationTools {

    private final TargetProjectRegistry targetProjectRegistry;

    /**
     * 接入一个新的目标项目。
     *
     * @param name 项目标识（唯一；用户未指定时可用项目根目录的文件夹名）
     * @param path 项目根目录的绝对路径，如 d:/workplace/project/xxx
     * @return 注册结果说明（成功/失败原因），AI 据此回复用户
     */
    @Tool("接入一个新的目标项目，之后即可阅读该项目的代码。name 为项目唯一标识（用户未指定时用根目录文件夹名），path 为项目根目录的绝对路径。注册成功后需提示用户在界面顶部的项目选择器中切换到该项目")
    public String registerTargetProject(String name, String path) {
        try {
            targetProjectRegistry.register(name, path);
            return "接入成功：项目「" + name + "」（根目录 " + path + "）已注册。"
                    + "请提醒用户在界面顶部的目标项目选择器中切换到「" + name + "」，之后即可阅读该项目的代码。";
        } catch (Exception e) {
            log.warn("[registerTargetProject][接入失败 name={} path={}：{}]", name, path, e.getMessage());
            return "接入失败：" + e.getMessage();
        }
    }

}
