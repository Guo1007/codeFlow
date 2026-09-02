package com.aidev.agent.controller.vo;

import lombok.Data;

/**
 * 目标项目 Response VO
 */
@Data
public class TargetProjectVO {

    /**
     * 项目标识（唯一）
     */
    private String name;

    /**
     * 项目根目录
     */
    private String path;

    /**
     * 是否配置了框架画像
     */
    private Boolean hasProfile;

}
