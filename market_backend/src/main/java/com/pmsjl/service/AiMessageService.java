package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.dto.ai.AiMessageQueryRequest;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.vo.AiChatVO;
import com.pmsjl.model.vo.AiMessageVO;
import com.pmsjl.model.vo.AiPageVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Date;

public interface AiMessageService extends IService<AiMessage> {
    AiChatVO sendMessage(Long conversationId, AiChatMessageRequest aiChatMessageRequest, HttpServletRequest request);

    AiPageVO<AiMessageVO> listConversationMessages(Long conversationId,
                                                    AiMessageQueryRequest queryRequest,
                                                    HttpServletRequest request);

    /**
     * 将指定时间以前仍未完成的助手消息批量收敛为 FAILED。
     *
     * @return 本批实际更新的消息数量
     */
    int expireStalePendingMessages(Date expireBefore, int batchSize);
}
