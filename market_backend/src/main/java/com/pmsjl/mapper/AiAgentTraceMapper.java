package com.pmsjl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.entity.AiAgentTrace;
import org.apache.ibatis.annotations.Mapper;

/** AI Agent 工具调用轨迹数据访问。 */
@Mapper
public interface AiAgentTraceMapper extends BaseMapper<AiAgentTrace> {
}
