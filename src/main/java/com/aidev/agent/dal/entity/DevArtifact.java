package com.aidev.agent.dal.entity;

import com.aidev.agent.enums.DevArtifactStatusEnum;
import com.aidev.agent.enums.DevArtifactTypeEnum;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 开发阶段产物实体（无 BaseDO，自带基础字段）
 * <p>
 * 流水线各阶段的产物（设计文档/代码/使用说明），同一任务同一类型按版本递增，
 * 每次生成（或人工修改后保存）都会新增一条记录，形成完整的演进历史
 */
@Data
@TableName("dev_artifact")
public class DevArtifact {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * 任务编号
     * <p>
     * 关联 {@link DevProject#getId()}
     */
    private Long projectId;

    /**
     * 产物类型
     * <p>
     * 枚举 {@link DevArtifactTypeEnum}
     */
    private Integer type;

    /**
     * 版本号，同一任务同一类型从 1 递增
     */
    private Integer version;

    /**
     * 状态
     * <p>
     * 枚举 {@link DevArtifactStatusEnum}
     */
    private Integer status;

    /**
     * 产物内容（Markdown / 代码）
     */
    private String content;

    /**
     * 生成该版本的依据（如评审意见）
     */
    private String remark;

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
