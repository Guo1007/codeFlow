package com.aidev.agent.dal.mapper;

import com.aidev.agent.dal.entity.DevProject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 开发任务 Mapper
 */
@Mapper
public interface DevProjectMapper extends BaseMapper<DevProject> {

    /**
     * 更新任务阶段
     */
    default int updateStage(Long id, Integer stage) {
        DevProject entity = new DevProject();
        entity.setId(id);
        entity.setStage(stage);
        return updateById(entity);
    }

}
