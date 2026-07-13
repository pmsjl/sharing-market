package com.pmsjl.model.dto.ai.internal;

import lombok.Data;

import java.io.Serializable;

/** Model provider metadata returned for auditing. */
@Data
public class AgentModelInfo implements Serializable {
    /** 模型服务提供方，例如 DeepSeek。 */
    private String provider;

    /** 实际完成本次调用的模型名称或版本。 */
    private String name;

    private static final long serialVersionUID = 1L;
}
