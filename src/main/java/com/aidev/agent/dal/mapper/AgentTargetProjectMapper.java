package com.aidev.agent.dal.mapper;

import com.aidev.agent.dal.entity.AgentTargetProject;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 目标项目注册表 Mapper
 */
@Mapper
public interface AgentTargetProjectMapper extends BaseMapper<AgentTargetProject> {

    /**
     * 物理删除（原生 SQL，绕过全局逻辑删除）
     */
    @Delete("DELETE FROM agent_target_project WHERE name = #{name}")
    int physicalDeleteByName(@Param("name") String name);
}
