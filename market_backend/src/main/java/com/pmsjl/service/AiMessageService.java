package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.dto.ai.AiShoppingContext;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.vo.AiChatVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

// TODO AI message service placeholder.
public interface AiMessageService extends IService<AiMessage> {
    AiChatVO sendMessage(Long conversationId, AiChatMessageRequest aiChatMessageRequest, HttpServletRequest request);

}