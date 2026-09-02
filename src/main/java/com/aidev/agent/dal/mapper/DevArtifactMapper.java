package com.aidev.agent.dal.mapper;

import com.aidev.agent.dal.entity.DevArtifact;
import com.aidev.agent.enums.DevArtifactTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 开发阶段产物 Mapper
 */
@Mapper
public interface DevArtifactMapper extends BaseMapper<DevArtifact> {

    /**
     * 查询指定任务指定类型的最新版本产物
     *
     * @param projectId 任务编号
     * @param type      产物类型
     * @return 最新版本的产物，不存在时返回 null
     */
    default DevArtifact selectLatest(Long projectId, Integer type) {
        return selectOne(new LambdaQueryWrapper<DevArtifact>()
                .eq(DevArtifact::getProjectId, projectId)
                .eq(DevArtifact::getType, type)
                .orderByDesc(DevArtifact::getVersion)
                .last("LIMIT 1"));
    }

    /**
     * 查询指定任务的最新设计文档（常用类型的便捷方法）
     */
    default DevArtifact selectLatestDesign(Long projectId) {
        return selectLatest(projectId, DevArtifactTypeEnum.DESIGN_DOC.getType());
    }

}
