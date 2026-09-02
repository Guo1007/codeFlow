package com.aidev.agent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置：扫描 Agent 引擎的 Mapper（启动类在 gcy.codeflow 包，需显式指定扫描路径）
 */
@Configuration
@MapperScan("com.aidev.agent.dal.mapper")
public class MybatisConfig {
}
