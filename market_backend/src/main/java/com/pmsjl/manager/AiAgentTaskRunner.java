package com.pmsjl.manager;

import com.pmsjl.mapper.AiMessageMapper;
import com.pmsjl.model.dto.ai.internal.AgentRunRequest;
import com.pmsjl.model.dto.ai.internal.AgentRunResponse;
import com.pmsjl.model.entity.AiMessage;
import com.pmsjl.model.enums.AiMessageStatusEnum;
import com.pmsjl.model.vo.AiChatVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

/** 仅负责提交和任务收尾；业务回写继续由原服务在独立事务中完成。 */
@Slf4j
@Component
public class AiAgentTaskRunner {

    @Autowired
    @Qualifier("aiAgentExecutor")
    private Executor executor;
    @Autowired
    private AiAgentClient aiAgentClient;
    @Autowired
    private AiMessageMapper aiMessageMapper;

    public AiChatVO submit(AiChatVO pendingResponse, AgentRunRequest request,
                           Function<AgentRunResponse, AiChatVO> onSuccess,
                           Function<AiAgentClientException, AiChatVO> onFailure) {
        String requestId = pendingResponse.getRequestId();
        Long messageId = pendingResponse.getAssistantMessage().getId();
        try {
            executor.execute(() -> run(requestId, messageId, request, onSuccess, onFailure));
            return pendingResponse;
        } catch (RejectedExecutionException exception) {
            log.warn("AI 后台任务被拒绝，requestId={}, messageId={}", requestId, messageId);
            return onFailure.apply(new AiAgentClientException(
                    "AI_AGENT_BUSY", "AI 服务繁忙，请稍后重试", true, exception));
        }
    }

    private void run(String requestId, Long messageId, AgentRunRequest request,
                     Function<AgentRunResponse, AiChatVO> onSuccess,
                     Function<AiAgentClientException, AiChatVO> onFailure) {
        try {
            onSuccess.apply(aiAgentClient.runAgent(requestId, request));
        } catch (Exception exception) {
            try {
                if (hasEnded(messageId)) {
                    log.info("丢弃已结束 AI 消息的迟到结果，requestId={}, messageId={}", requestId, messageId);
                    return;
                }
                AiAgentClientException failure = exception instanceof AiAgentClientException remote
                        ? remote : new AiAgentClientException("AI_AGENT_EXECUTION_FAILED",
                        "AI 回复处理失败，请稍后重试", true, exception);
                onFailure.apply(failure);
            } catch (Exception writeBackException) {
                // 查询或回写本身失败时不无限重试；超时清理继续负责遗留 PENDING。
                log.error("AI 后台任务收尾失败，requestId={}, messageId={}",
                        requestId, messageId, writeBackException);
            }
        }
    }

    private boolean hasEnded(Long messageId) {
        AiMessage message = aiMessageMapper.selectById(messageId);
        return message == null || Integer.valueOf(1).equals(message.getIsDelete())
                || !AiMessageStatusEnum.PENDING.getValue().equals(message.getStatus());
    }
}
