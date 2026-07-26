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

import java.util.Date;
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

        Date now = new Date();
        List<AiAgentTrace> traceEntities = traces.stream()
                .map(trace -> toEntity(
                        requestId,
                        conversationId,
                        messageId,
                        trace,
                        now
                ))
                .toList();

        ThrowUtils.throwIf(
                !saveBatch(traceEntities),
                ErrorCode.OPERATION_ERROR,
                "保存 AI 工具调用轨迹失败"
        );
    }

    private AiAgentTrace toEntity(String requestId,
                                  Long conversationId,
                                  Long messageId,
                                  AgentToolTrace trace,
                                  Date createTime) {
        ThrowUtils.throwIf(
                trace == null || trace.getStatus() == null,
                ErrorCode.SYSTEM_ERROR,
                "AI 工具调用轨迹结构异常"
        );

        AiAgentTrace entity = new AiAgentTrace();
        entity.setRequestId(requestId);
        entity.setConversationId(conversationId);
        entity.setMessageId(messageId);
        entity.setToolName(trace.getToolName());
        entity.setToolArguments(toJson(trace.getToolArguments()));
        entity.setToolResultSummary(toJson(trace.getToolResultSummary()));
        entity.setStatus(trace.getStatus().getValue());
        entity.setLatencyMs(trace.getLatencyMs());
        entity.setErrorMessage(trace.getErrorMessage());
        entity.setCreateTime(createTime);
        return entity;
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
