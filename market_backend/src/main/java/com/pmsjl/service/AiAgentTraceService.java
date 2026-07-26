package com.pmsjl.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pmsjl.model.dto.ai.internal.AgentToolTrace;
import com.pmsjl.model.entity.AiAgentTrace;

import java.util.List;

/** AI Agent 工具调用轨迹持久化服务。 */
public interface AiAgentTraceService extends IService<AiAgentTrace> {

    /**
     * 将 Python 返回的本轮工具轨迹绑定到对应会话和助手消息并批量保存。
     */
    void saveAgentTraces(String requestId,
                         Long conversationId,
                         Long messageId,
                         List<AgentToolTrace> traces);
}
