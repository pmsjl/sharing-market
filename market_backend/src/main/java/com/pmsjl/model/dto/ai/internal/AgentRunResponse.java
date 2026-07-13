package com.pmsjl.model.dto.ai.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Successful response from POST /agent/v1/runs. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentRunResponse implements Serializable {
    /** 本次 Agent 调用的全链路请求标识。 */
    private String requestId;

    /** 可直接作为助手消息展示的 Markdown 文本。 */
    private String answer;

    /** 经过 Python 校验的结构化回答内容。 */
    private AgentOutput output;

    /** 本次调用实际使用的模型信息。 */
    private AgentModelInfo model;

    /** 本次模型调用的 token 用量。 */
    private AgentUsage usage;

    /** Python Agent 完成本次请求的总耗时，单位为毫秒。 */
    private Integer latencyMs;

    /** 本轮产生的工具调用轨迹，供 Java 持久化审计。 */
    private List<AgentToolTrace> traces = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
