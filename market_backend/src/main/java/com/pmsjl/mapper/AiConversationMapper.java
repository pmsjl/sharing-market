package com.pmsjl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** AI 会话数据访问。 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
    @Select("SELECT * FROM ai_conversation WHERE id=#{conversationId} and isDelete=0 FOR UPDATE")
    AiConversation selectByIdForUpdate(@Param("conversationId") Long conversationId);

    @Select("SELECT * FROM ai_conversation " +
            "WHERE id=#{conversationId} AND userId=#{userId} AND isDelete=0 FOR UPDATE")
    AiConversation selectOwnedByIdForUpdate(@Param("conversationId") Long conversationId,
                                            @Param("userId") Long userId);
}
