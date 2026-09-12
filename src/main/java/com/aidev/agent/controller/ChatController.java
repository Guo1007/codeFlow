package com.aidev.agent.controller;

import com.aidev.agent.aiservice.ChatAiService;
import com.aidev.agent.aiservice.ProjectChatServiceFactory;
import com.aidev.agent.common.ApiResponse;
import com.aidev.agent.common.UserContext;
import com.aidev.agent.config.AgentProperties;
import com.aidev.agent.config.TargetProjectRegistry;
import com.aidev.agent.controller.vo.ChatMessageVO;
import com.aidev.agent.controller.vo.ChatReqVO;
import com.aidev.agent.controller.vo.ChatSessionVO;
import com.aidev.agent.controller.vo.SessionRenameReqVO;
import com.aidev.agent.controller.vo.TargetProjectVO;
import com.aidev.agent.service.ChatHistoryService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * 同时将每条用户/AI 消息与「会话」一并落库（agent_chat_session / agent_chat_message），
 * 供前端列出历史对话并一键载入回显。
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

    private final ProjectChatServiceFactory chatServiceFactory;

    private final TargetProjectRegistry targetProjectRegistry;

    private final ChatHistoryService chatHistoryService;

    /**
     * 聊天记忆存储（Redis 实现），删除会话时同步清除多轮记忆
     */
    private final ChatMemoryStore chatMemoryStore;

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
                    vo.setExample(Boolean.TRUE.equals(target.getExample()));
                    return vo;
                })
                .toList();
        return ApiResponse.success(projects);
    }

    /**
     * 删除动态接入的目标项目（yaml 静态/示例项目不可删除）
     */
    @DeleteMapping("/project")
    public ApiResponse<Boolean> removeTargetProject(@RequestParam("name") String name) {
        targetProjectRegistry.remove(name);
        return ApiResponse.success(true);
    }

    /**
     * 历史会话列表（可选按目标项目过滤）
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionVO>> listSessions(@RequestParam(value = "project", required = false) String project) {
        return ApiResponse.success(chatHistoryService.listSessions(project, UserContext.getUserIdStr()));
    }

    /**
     * 某会话的完整历史消息（载入历史对话回显用）
     */
    @GetMapping("/history")
    public ApiResponse<List<ChatMessageVO>> history(@RequestParam("conversationId") String conversationId) {
        return ApiResponse.success(chatHistoryService.listMessages(conversationId, UserContext.getUserIdStr()));
    }

    /**
     * 删除历史会话（同时清除会话索引、消息与 Redis 记忆）
     */
    @DeleteMapping("/session")
    public ApiResponse<Boolean> deleteSession(@RequestParam("conversationId") String conversationId,
                                              @RequestParam(value = "project", required = false) String project) {
        chatHistoryService.deleteSession(conversationId, UserContext.getUserIdStr());
        // 一并清除 Redis 多轮记忆，避免残留
        chatMemoryStore.deleteMessages(buildMemoryId(project, conversationId));
        return ApiResponse.success(true);
    }

    /**
     * 重命名会话标题
     */
    @PostMapping("/session/rename")
    public ApiResponse<Boolean> renameSession(@Validated @RequestBody SessionRenameReqVO reqVO) {
        chatHistoryService.renameSession(reqVO.getConversationId(), reqVO.getTitle(), UserContext.getUserIdStr());
        return ApiResponse.success(true);
    }

    /**
     * AI 同步对话，等待模型输出完整结果后一次性返回；落库用户消息与 AI 回复
     */
    @PostMapping("/generate")
    public ApiResponse<String> generateChat(@jakarta.validation.Valid @RequestBody ChatReqVO reqVO) {
        ChatAiService chatAiService = chatServiceFactory.getChatService(reqVO.getProject());
        String conversationId = resolveConversationId(reqVO.getConversationId());
        String memoryId = buildMemoryId(reqVO.getProject(), conversationId);
        chatHistoryService.ensureSession(conversationId, resolveProjectName(reqVO.getProject()), reqVO.getPrompt(), UserContext.getUserIdStr());
        chatHistoryService.saveUserMessage(conversationId, reqVO.getPrompt());
        String content = chatAiService.chat(memoryId, reqVO.getPrompt());
        chatHistoryService.saveAssistantMessage(conversationId, content);
        return ApiResponse.success(content);
    }

    /**
     * AI 流式对话接口，以 SSE 格式实时推送 AI 回复内容。
     * <p>
     * 事件格式：meta 事件（新会话时返回会话 ID）→ content 事件（AI 回复片段）→ [DONE] 结束标记。
     * 首条消息会自动创建「会话」并落库，AI 回复在流结束后整体落库，前端刷新/切页后可回显历史。
     * </p>
     */
    @PostMapping(value = "/generate-stream", produces = "text/event-stream;charset=utf-8")
    public Flux<String> generateChatStream(@jakarta.validation.Valid @RequestBody ChatReqVO reqVO) {
        ChatAiService chatAiService = chatServiceFactory.getChatService(reqVO.getProject());
        String conversationId = resolveConversationId(reqVO.getConversationId());
        String memoryId = buildMemoryId(reqVO.getProject(), conversationId);
        // 会话索引 + 用户消息落库（meta 事件返回会话 ID 前先落库，保证不丢失用户输入）
        chatHistoryService.ensureSession(conversationId, resolveProjectName(reqVO.getProject()), reqVO.getPrompt(), UserContext.getUserIdStr());
        chatHistoryService.saveUserMessage(conversationId, reqVO.getPrompt());

        boolean isNew = reqVO.getConversationId() == null || reqVO.getConversationId().isEmpty();
        Flux<String> metaEvent = isNew ? Flux.just(metaJson(conversationId)) : Flux.empty();
        // 累积 AI 回复，用于流结束落库
        StringBuilder reply = new StringBuilder();
        Flux<String> chatStream = chatAiService.streamChat(memoryId, reqVO.getPrompt())
                .doOnNext(reply::append)
                .map(this::contentJson)
                .concatWith(Flux.just("[DONE]"))
                .doOnComplete(() -> chatHistoryService.saveAssistantMessage(conversationId, reply.toString()))
                .doOnCancel(() -> chatHistoryService.saveAssistantMessage(conversationId, reply.toString()))
                .onErrorResume(e -> {
                    log.error("[generateChatStream][AI 流式对话发生异常 conversationId={}]", conversationId, e);
                    // 异常时也保存已生成的部分，避免历史消息缺 AI 回复
                    chatHistoryService.saveAssistantMessage(conversationId, reply.toString());
                    return Flux.just(errorJson("AI 服务暂时无法响应，请稍后再试"));
                });
        return Flux.concat(metaEvent, chatStream);
    }

    private String resolveConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return conversationId;
    }

    private String resolveProjectName(String projectName) {
        return targetProjectRegistry.resolve(projectName).getName();
    }

    /**
     * 构建聊天记忆标识：按项目隔离（不同项目的会话记忆互不干扰）
     *
     * @param projectName 目标项目名称，可为空（取默认项目）
     * @param conversationId 会话唯一标识
     * @return 聊天记忆标识，格式为：Key前缀 + 项目名 + ":" + 用户ID + ":" + 会话ID
     */
    private String buildMemoryId(String projectName, String conversationId) {
        AgentProperties.TargetProject target = targetProjectRegistry.resolve(projectName);
        return CHAT_MEMORY_KEY_PREFIX + target.getName() + ":" + UserContext.getUserIdStr() + ":" + conversationId;
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