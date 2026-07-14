package com.pmsjl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/** AI 会话数据访问。 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
