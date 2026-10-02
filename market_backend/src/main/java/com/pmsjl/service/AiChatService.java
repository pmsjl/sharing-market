package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.ai.AiChatMessageRequest;
import com.pmsjl.model.dto.ai.AiShoppingContext;
import com.pmsjl.model.vo.AiChatVO;
import jakarta.servlet.http.HttpServletRequest;

/** AI 聊天编排服务。 */
public interface AiChatService {

    /**
     * 创建会话、持久化首条用户消息，返回 PENDING 并提交后台 Agent 任务。
     * 任务提交被拒绝时直接返回 FAILED；后台生成结果通过消息查询获取。
     *
     * @param aiChatMessageRequest 首条自然语言消息及可选购买条件
     * @param request 当前 HTTP 请求，用于读取登录用户
     * @return 当前会话和本轮两条消息的公开响应
     */
    AiChatVO createConversation(AiChatMessageRequest aiChatMessageRequest, HttpServletRequest request);
    void validateShoppingContext(AiShoppingContext shoppingContext);

}
