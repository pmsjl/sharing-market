package com.pmsjl.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.config.AiAgentProperties;
import com.pmsjl.model.dto.ai.internal.AgentErrorResponse;
import com.pmsjl.model.dto.ai.internal.AgentOutput;
import com.pmsjl.model.dto.ai.internal.AgentRunRequest;
import com.pmsjl.model.dto.ai.internal.AgentRunResponse;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Java 到 Python Agent 的唯一 HTTP 出口。 */
@Component
public class AiAgentClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    @Autowired
    private AiAgentProperties aiAgentProperties;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 将本轮上下文发送至 Python，并校验 Python 回传的 requestId。
     * 该方法不处理数据库，也不向浏览器暴露 Python 的原始错误。
     */
    public AgentRunResponse runAgent(String requestId, AgentRunRequest agentRunRequest) {
        if (StringUtils.isBlank(aiAgentProperties.getInternalToken())) {
            throw new AiAgentClientException("AI_AGENT_CONFIG_INVALID", "AI 服务内部 Token 未配置", false);
        }
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(agentRunRequest);
        } catch (JsonProcessingException e) {
            throw new AiAgentClientException("AI_AGENT_REQUEST_INVALID", "AI 请求序列化失败", false, e);
        }

        String baseUrl = StringUtils.removeEnd(aiAgentProperties.getBaseUrl(), "/");
        Request request = new Request.Builder()
                .url(baseUrl + "/agent/v1/runs")
                .header("X-Internal-Token", aiAgentProperties.getInternalToken())
                .header("X-Request-Id", requestId)
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .build();

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(aiAgentProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .readTimeout(aiAgentProperties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw toRemoteException(responseBody, response.code());
            }
            AgentRunResponse agentRunResponse = objectMapper.readValue(responseBody, AgentRunResponse.class);
            if (agentRunResponse == null || !StringUtils.equals(requestId, agentRunResponse.getRequestId())) {
                throw new AiAgentClientException("AI_AGENT_RESPONSE_INVALID", "AI 服务返回的请求标识不一致", true);
            }
            if (StringUtils.isBlank(agentRunResponse.getAnswer())) {
                throw new AiAgentClientException("AI_AGENT_RESPONSE_INVALID", "AI 服务没有返回回答内容", true);
            }
            AgentOutput output = agentRunResponse.getOutput();
            if (output == null
                    || output.getIntent() == null
                    || StringUtils.isBlank(output.getSummary())
                    || StringUtils.isBlank(output.getMemorySummary())) {
                throw new AiAgentClientException(
                        "AI_AGENT_RESPONSE_INVALID",
                        "AI 服务返回的结构化结果不完整",
                        true
                );
            }
            return agentRunResponse;
        } catch (AiAgentClientException e) {
            throw e;
        } catch (IOException e) {
            throw new AiAgentClientException("AI_AGENT_UNAVAILABLE", "AI 服务暂不可用", true, e);
        }
    }

    private AiAgentClientException toRemoteException(String responseBody, int statusCode) {
        try {
            AgentErrorResponse errorResponse = objectMapper.readValue(responseBody, AgentErrorResponse.class);
            if (StringUtils.isNotBlank(errorResponse.getAgentErrorKey())) {
                return new AiAgentClientException(errorResponse.getAgentErrorKey(), errorResponse.getMessage(),
                        Boolean.TRUE.equals(errorResponse.getRetryable()));
            }
        } catch (JsonProcessingException ignored) {
            // Python 异常页不是内部契约 JSON 时，降级为统一错误码。
        }
        boolean retryable = statusCode >= 500 || statusCode == 429;
        return new AiAgentClientException("AI_AGENT_REMOTE_ERROR", "AI 服务返回异常状态：" + statusCode, retryable);
    }
}
