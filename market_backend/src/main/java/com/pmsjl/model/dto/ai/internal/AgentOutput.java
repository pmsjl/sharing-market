package com.pmsjl.model.dto.ai.internal;

import com.pmsjl.model.enums.AiIntentEnum;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Validated structured output returned by the Python Agent. */
@Data
public class AgentOutput implements Serializable {
    /** Agent 判断出的本轮用户意图。 */
    private AiIntentEnum intent;
    /** 截至本轮仍对后续对话有用的滚动语义摘要。 */
    private String memorySummary;

    /** 对本轮结论的简短结构化摘要。 */
    private String summary;

    /** Agent 给出的候选商品推荐。 */
    private List<AgentRecommendation> recommendations = new ArrayList<>();

    /** 面向用户的购买检查或决策建议。 */
    private List<String> purchaseAdvice = new ArrayList<>();

    /** 需要用户重点注意的交易风险。 */
    private List<String> warnings = new ArrayList<>();

    /** 可用于继续检索平台商品的关键词。 */
    private List<String> searchKeywords = new ArrayList<>();

    /** RAG 检索命中的来源候选，返回用户前仍需 Java 校验。 */
    private List<AgentSource> sources = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}
