package com.pmsjl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/** AI 消息数据访问。 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
