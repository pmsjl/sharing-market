package com.pmsjl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

/** AI 消息数据访问。 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {


    List<AiMessage> selectStalePendingMessages(@Param("expireBefore") Date expireBefore,
                                               @Param("batchSize") int batchSize);

    int markPendingMessageTimedOut(@Param("messageId") Long messageId,
                                   @Param("expireBefore") Date expireBefore,
                                   @Param("content") String content,
                                   @Param("agentErrorKey") String agentErrorKey,
                                   @Param("updateTime") Date updateTime);

    List<AiMessage> selectRecentSuccessfulHistory(
            @Param("conversationId") Long conversationId,
            @Param("turnLimit") int turnLimit
    );

}
