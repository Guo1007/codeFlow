package com.aidev.agent.controller;

import com.aidev.agent.aiservice.ChatAiService;
import com.aidev.agent.aiservice.ProjectChatServiceFactory;
import com.aidev.agent.common.ApiResponse;
import com.aidev.agent.config.AgentProperties;
import com.aidev.agent.config.TargetProjectRegistry;
import com.aidev.agent.controller.vo.ChatReqVO;
import com.aidev.agent.controller.vo.TargetProjectVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * AI 对话控制器（同步 + SSE 流式，支持多目标项目）。
 * <p>
 * 请求携带目标项目名称（project，为空取默认项目）：读码工具的工作范围、
 * 会话记忆的 Redis Key 均按项目隔离，系统提示词按项目注入框架画像。
 * 流式接口基于 SSE 逐块推送 AI 回复，首次请求自动生成会话 ID 并通过 meta 事件返回。
 * </p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat")
@Validated
public class ChatController {

    /**
     * 聊天记忆在 Redis 中的 Key 前缀，完整格式：前缀 + 项目名 + ":" + 用户ID + ":" + 会话ID
     */
    private static final String CHAT_MEMORY_KEY_PREFIX = "agent:chat:memory:";

    /**
     * 当前操作用户（登录暂缓，内网演示固定值，后续接入用户体系时替换）
     */
    private static final String DEFAULT_USER = "admin";

    private final ProjectChatServiceFactory chatServiceFactory;

    private final TargetProjectRegistry targetProjectRegistry;

    /**
     * JSON 序列化工具，用于封装 SSE 事件内容
     */
    private final ObjectMapper objectMapper;

    /**
     * 目标项目列表（前端项目选择器数据源）
     */
    @GetMapping("/projects")
    public ApiResponse<List<TargetProjectVO>> getTargetProjects() {
        List<TargetProjectVO> projects = targetProjectRegistry.list().stream()
                .map(target -> {
                    TargetProjectVO vo = new TargetProjectVO();
                    vo.setName(target.getName());
                    vo.setPath(target.getPath());
                    vo.setHasProfile(target.getFrameworkProfile() != null && !target.getFrameworkProfile().isBlank());
                    return vo;
                })
                .toList();
        return ApiResponse.success(projects);
    }

    /**
     * AI 同步对话，等待模型输出完整结果后一次性返回
     */
    @PostMapping("/generate")
    public ApiResponse<String> generateChat(@jakarta.validation.Valid @RequestBody ChatReqVO reqVO) {
        ChatAiService chatAiService = chatServiceFactory.getChatService(reqVO.getProject());
        String memoryId = buildMemoryId(reqVO.getProject(), reqVO.getConversationId());
        return ApiResponse.success(chatAiService.chat(memoryId, reqVO.getPrompt()));
    }

    /**
     * AI 流式对话接口，以 SSE 格式实时推送 AI 回复内容。
     * <p>
     * 客户端发送用户消息后，服务端通过 SSE 流逐步返回 AI 生成的回复文本。
     * 若请求中未携带 conversationId，则自动创建新会话并通过 meta 事件返回会话 ID；
     * 若携带已有 conversationId，则基于该项目下的历史对话上下文继续交互。
     * </p>
     * <p>
     * 事件格式：meta 事件（新会话时返回会话 ID）→ content 事件（AI 回复片段）→ [DONE] 结束标记；
     * 出现异常时返回 error 事件。
     * </p>
     */
    @PostMapping(value = "/generate-stream", produces = "text/event-stream;charset=utf-8")
    public Flux<String> generateChatStream(@jakarta.validation.Valid @RequestBody ChatReqVO reqVO) {
        ChatAiService chatAiService = chatServiceFactory.getChatService(reqVO.getProject());
        // 会话 ID：未传则新建，并通过 meta 事件返回给客户端，供后续多轮对话使用
        String conversationId = reqVO.getConversationId();
        boolean isNew = conversationId == null || conversationId.isEmpty();
        if (isNew) {
            conversationId = UUID.randomUUID().toString().replace("-", "");
        }
        String memoryId = buildMemoryId(reqVO.getProject(), conversationId);
        Flux<String> metaEvent = isNew ? Flux.just(metaJson(conversationId)) : Flux.empty();
        // AI 回复逐块封装为 content 事件，结束后追加 [DONE] 标记，异常时降级为 error 事件
        Flux<String> chatStream = chatAiService.streamChat(memoryId, reqVO.getPrompt())
                .map(this::contentJson)
                .concatWith(Flux.just("[DONE]"))
                .onErrorResume(e -> {
                    log.error("[generateChatStream][AI 流式对话发生异常]", e);
                    return Flux.just(errorJson("AI 服务暂时无法响应，请稍后再试"));
                });
        return Flux.concat(metaEvent, chatStream);
    }

    /**
     * 构建聊天记忆标识：按项目隔离（不同项目的会话记忆互不干扰）
     *
     * @param projectName    目标项目名称，可为空（取默认项目）
     * @param conversationId 会话唯一标识，可为空
     * @return 聊天记忆标识，格式为：Key前缀 + 项目名 + ":" + 用户ID + ":" + 会话ID
     */
    private String buildMemoryId(String projectName, String conversationId) {
        AgentProperties.TargetProject target = targetProjectRegistry.resolve(projectName);
        if (conversationId == null || conversationId.isEmpty()) {
            conversationId = UUID.randomUUID().toString().replace("-", "");
        }
        return CHAT_MEMORY_KEY_PREFIX + target.getName() + ":" + DEFAULT_USER + ":" + conversationId;
    }

    /**
     * 构建元信息 JSON，用于在新建会话时通知客户端会话 ID
     */
    private String metaJson(String conversationId) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "meta");
            node.put("conversationId", conversationId);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"type\":\"meta\"}";
        }
    }

    /**
     * 构建 AI 回复内容的 JSON，用于封装单个流式文本片段
     */
    private String contentJson(String content) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("content", content);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"content\":\"\"}";
        }
    }

    /**
     * 构建错误信息 JSON，用于向前端返回异常提示
     */
    private String errorJson(String errorMsg) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("error", errorMsg);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"error\":\"序列化异常\"}";
        }
    }

}
