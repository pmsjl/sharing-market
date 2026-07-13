package com.pmsjl.model.dto.ai.internal;

import com.pmsjl.model.enums.AiSuggestedActionTypeEnum;
import lombok.Data;

import java.io.Serializable;

/** Safe navigation action suggested by the Agent. */
@Data
public class AgentSuggestedAction implements Serializable {
    /** 建议操作类型，决定前端后续行为。 */
    private AiSuggestedActionTypeEnum type;

    /** 展示给用户的操作文案。 */
    private String label;

    /** 查看商品操作对应的商品 ID。 */
    private Long commodityId;

    /** 搜索商品操作使用的检索关键词。 */
    private String keyword;

    private static final long serialVersionUID = 1L;
}
