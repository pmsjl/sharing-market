package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.dto.ai.AiShoppingContext;
import com.pmsjl.model.vo.AiChatVO;
import jakarta.servlet.http.HttpServletRequest;

/** AI 聊天编排服务。 */
public interface AiChatService {

    /**
     * 创建会话、持久化首条用户消息，并通过 Python Agent 生成首条助手回复。
     * Python 或模型异常时，助手消息会以 FAILED 状态返回，供前端提示和后续重试。
     *
     * @param aiChatMessageRequest 首条自然语言消息及可选购买条件
     * @param request 当前 HTTP 请求，用于读取登录用户
     * @return 当前会话和本轮两条消息的公开响应
     */
    AiChatVO createConversation(AiChatMessageRequest aiChatMessageRequest, HttpServletRequest request);
    void validateShoppingContext(AiShoppingContext shoppingContext);

}
