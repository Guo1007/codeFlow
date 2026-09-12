package com.aidev.agent.controller.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 会话重命名 Request VO
 */
@Data
public class SessionRenameReqVO {

    /**
     * 会话唯一标识
     */
    @NotBlank(message = "会话标识不能为空")
    private String conversationId;

    /**
     * 新标题
     */
    @NotBlank(message = "标题不能为空")
    private String title;

}