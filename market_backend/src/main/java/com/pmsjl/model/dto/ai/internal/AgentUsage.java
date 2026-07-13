package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

/** Token usage returned by the model provider. */
@Data
public class AgentUsage implements Serializable {
    /** 本次模型调用消耗的输入 token 数量。 */
    private Integer inputTokens;

    /** 本次模型调用生成的输出 token 数量。 */
    private Integer outputTokens;

    private static final long serialVersionUID = 1L;
}
