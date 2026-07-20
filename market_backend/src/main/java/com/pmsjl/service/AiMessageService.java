package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.dto.ai.AiMessageQueryRequest;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.vo.AiChatVO;
import com.pmsjl.model.vo.AiMessageVO;
import com.pmsjl.model.vo.AiPageVO;
import jakarta.servlet.http.HttpServletRequest;

public interface AiMessageService extends IService<AiMessage> {
    AiChatVO sendMessage(Long conversationId, AiChatMessageRequest aiChatMessageRequest, HttpServletRequest request);

    AiPageVO<AiMessageVO> listConversationMessages(Long conversationId,
                                                    AiMessageQueryRequest queryRequest,
                                                    HttpServletRequest request);
}
