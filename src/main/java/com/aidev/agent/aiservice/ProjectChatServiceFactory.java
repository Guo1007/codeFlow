package com.aidev.agent.aiservice;

import com.aidev.agent.config.AgentProperties;
import com.aidev.agent.config.TargetProjectRegistry;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import com.aidev.agent.tools.ProjectCodeTools;
import com.aidev.agent.tools.ProjectRegistrationTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标项目级对话服务工厂。
 * <p>
 * 不同目标项目需要绑定不同的读码工具实例（工作范围=项目根目录）与
 * 项目专属系统提示词（通用规则 + 框架画像注入），因此不能是单一 @AiService 单例：
 * 本工厂按项目构建 ChatAiService 并缓存（模型与聊天记忆为全局共享 Bean）。
 * </p>
 */
@Slf4j
@Component
public class ProjectChatServiceFactory {

    private static final String BASE_SYSTEM_PROMPT_RESOURCE = "system-prompt.txt";

    private final ChatModel chatModel;

    private final StreamingChatModel streamingChatModel;

    private final ChatMemoryProvider chatMemoryProvider;

    private final TargetProjectRegistry targetProjectRegistry;

    /**
     * 项目接入工具（全局单例，所有对话服务共享：用于对话中动态注册新目标项目）
     */
    private final ProjectRegistrationTools projectRegistrationTools;

    /**
     * 项目名 -> 该项目的对话服务（首次使用时构建）
     */
    private final Map<String, ChatAiService> serviceCache = new ConcurrentHashMap<>();

    /**
     * 基础系统提示词（通用部分，与项目无关）
     */
    private final String baseSystemPrompt;

    public ProjectChatServiceFactory(@Qualifier("openAiChatModel") ChatModel chatModel,
                                     @Qualifier("openAiStreamingChatModel") StreamingChatModel streamingChatModel,
                                     ChatMemoryProvider chatMemoryProvider,
                                     TargetProjectRegistry targetProjectRegistry,
                                     ProjectRegistrationTools projectRegistrationTools) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.targetProjectRegistry = targetProjectRegistry;
        this.projectRegistrationTools = projectRegistrationTools;
        this.baseSystemPrompt = loadBasePrompt();
    }

    /**
     * 获取指定目标项目的对话服务（name 为空时取默认项目），按项目缓存
     */
    public ChatAiService getChatService(String projectName) {
        AgentProperties.TargetProject target = targetProjectRegistry.resolve(projectName);
        return serviceCache.computeIfAbsent(target.getName(), name -> {
            log.info("[getChatService][构建项目 {} 的对话服务，根目录 {}]", name, target.getPath());
            return AiServices.builder(ChatAiService.class)
                    .chatModel(chatModel)
                    .streamingChatModel(streamingChatModel)
                    .chatMemoryProvider(chatMemoryProvider)
                    .tools(new ProjectCodeTools(target.getPath()), projectRegistrationTools)
                    .systemMessageProvider(memoryId -> buildSystemPrompt(target))
                    .build();
        });
    }

    /**
     * 组装项目专属系统提示词：通用规则 + 当前目标项目信息 + 框架画像（可空）
     */
    private String buildSystemPrompt(AgentProperties.TargetProject target) {
        String profile = targetProjectRegistry.loadFrameworkProfile(target);
        StringBuilder sb = new StringBuilder(baseSystemPrompt);
        sb.append("\n\n【当前目标项目】\n")
                .append("名称：").append(target.getName()).append('\n')
                .append("根目录：").append(target.getPath()).append('\n');
        if (!profile.isBlank()) {
            sb.append("\n【该项目的框架画像（必须遵循）】\n").append(profile.trim());
        } else {
            sb.append("\n该项目未提供框架画像：请先用读码工具（listProjectStructure / searchProjectCode / readProjectFile）"
                    + "自主探索项目结构与典型代码，推断其分层规范与代码风格后再回答。");
        }
        return sb.toString();
    }

    private String loadBasePrompt() {
        try {
            return new ClassPathResource(BASE_SYSTEM_PROMPT_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("基础系统提示词加载失败：" + BASE_SYSTEM_PROMPT_RESOURCE, e);
        }
    }

}
