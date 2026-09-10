package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.AiAgentTraceMapper;
import com.pmsjl.model.dto.ai.internal.AgentToolTrace;
import com.pmsjl.model.entity.AiAgentTrace;
import com.pmsjl.service.AiAgentTraceService;
import com.pmsjl.utils.ThrowUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/** AI Agent 工具调用轨迹持久化实现。 */
@Service
public class AiAgentTraceServiceImpl
        extends ServiceImpl<AiAgentTraceMapper, AiAgentTrace>
        implements AiAgentTraceService {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void saveAgentTraces(String requestId,
                                Long conversationId,
                                Long messageId,
                                List<AgentToolTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return;
        }

        List<AiAgentTrace> traceEntities = traces.stream()
                .map(trace -> toAgentTrace(
                        requestId,
                        conversationId,
                        messageId,
                        trace
                ))
                .toList();

        ThrowUtils.throwIf(
                !this.saveBatch(traceEntities),
                ErrorCode.OPERATION_ERROR,
                "保存 AI 工具调用轨迹失败"
        );
    }

    private AiAgentTrace toAgentTrace(String requestId,
                                      Long conversationId,
                                      Long messageId,
                                      AgentToolTrace trace) {
        ThrowUtils.throwIf(
                trace == null || trace.getStatus() == null,
                ErrorCode.SYSTEM_ERROR,
                "AI 工具调用轨迹结构异常"
        );

        AiAgentTrace aiAgentTrace = new AiAgentTrace();
        aiAgentTrace.setRequestId(requestId);
        aiAgentTrace.setConversationId(conversationId);
        aiAgentTrace.setMessageId(messageId);
        aiAgentTrace.setToolName(trace.getToolName());
        aiAgentTrace.setToolArguments(toJson(trace.getToolArguments()));
        aiAgentTrace.setToolResultSummary(toJson(trace.getToolResultSummary()));
        aiAgentTrace.setStatus(trace.getStatus().getValue());
        aiAgentTrace.setLatencyMs(trace.getLatencyMs());
        aiAgentTrace.setErrorMessage(trace.getErrorMessage());
        return aiAgentTrace;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "AI 工具调用轨迹序列化失败"
            );
        }
    }
}
