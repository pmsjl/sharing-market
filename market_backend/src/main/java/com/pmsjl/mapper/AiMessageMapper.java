package com.pmsjl.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
                                   @Param("agentErrorKey") String agentErrorKey);

    /**
     * Completes an assistant message only while it is still pending.
     * The affected-row count is the concurrency check for late agent results.
     */
    int updatePendingAssistantMessage(AiMessage assistantMessage);

    List<AiMessage> selectRecentSuccessfulHistory(
            @Param("conversationId") Long conversationId,
            @Param("turnLimit") int turnLimit
    );

}
